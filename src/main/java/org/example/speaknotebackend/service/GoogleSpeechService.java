package org.example.speaknotebackend.service;

import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.BidiStreamObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.common.exceptions.BaseException;
import org.example.speaknotebackend.util.SttTextBuffer;
import org.example.speaknotebackend.domain.repository.LectureRepository;
import org.example.speaknotebackend.entity.Lecture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import java.util.Deque;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.springframework.scheduling.annotation.Async;
import java.util.concurrent.CompletableFuture;

import static org.example.speaknotebackend.common.response.BaseResponseStatus.UNEXPECTED_ERROR;
import static org.example.speaknotebackend.common.response.BaseResponseStatus.WS_CONTEXT_NOT_FOUND;
import static org.example.speaknotebackend.common.response.BaseResponseStatus.WS_SESSION_NOT_FOUND;

@Slf4j
@Service
public class GoogleSpeechService {

    // Google STT 클라이언트 객체 (gRPC 커넥션/호출의 엔트리 포인트)
    private SpeechClient speechClient;

    private final LectureRepository lectureRepository;
    
    @Value("${google.stt.credentials.path}")
    private String credentialsPath;

    /**
     * 세션별 상태 컨텍스트
     * - WebSocket 세션 ID를 키로, STT 스트림/버퍼/스케줄 등의 상태를 분리 관리
     */
    private static class SessionContext {
        // 세션별 STT 문장 누적 버퍼
        final SttTextBuffer textBuffer = new SttTextBuffer();
        // gRPC 스트리밍이 시작/유지되고 있는지 여부 (멀티스레드 안전)
        final AtomicBoolean streamingStarted = new AtomicBoolean(false); // AtomicBoolean : 동시성 안전한 불리언
        // 초기 설정 패킷(StreamingRecognitionConfig) 전송 완료 여부
        final AtomicBoolean initialConfigSent = new AtomicBoolean(false);
        // 세션 내 전송 순서 보장을 위한 단조 증가 시퀀스
        final AtomicLong seq = new AtomicLong(0L);
        // 콜백 결과의 순서 보장을 위한 마지막 전송 완료 seq
        final AtomicLong lastDeliveredSeq = new AtomicLong(0L);
        // Google STT로 오디오 청크를 전송하는 gRPC 요청 스트림 핸들
        volatile ClientStream<StreamingRecognizeRequest> requestStream;
        // 2초 지연 후 1초 주기로 버퍼를 비우고 후속 처리를 수행하는 작업 핸들
        volatile ScheduledFuture<?> scheduledTask;
        // WebSocket 세션 (콜백에서 사용)
        volatile WebSocketSession webSocketSession;
        // 세션별 Inbound(오디오 바이트) 큐 - drop_oldest
        final Deque<byte[]> inboundQueue = new ConcurrentLinkedDeque<>();
        // 세션별 Outbound(클라이언트로 보낼 메시지) 큐 - drop_oldest (구조화 페이로드)
        final Deque<Map<String,Object>> outboundQueue = new ConcurrentLinkedDeque<>();
        // 최근 처리한 requestId 집합(중복 제거용) - 간단한 LRU 유사 정책으로 제한 관리
        final Deque<String> recentRequestOrder = new ConcurrentLinkedDeque<>();
        final Set<String> recentRequestIds = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
        // 연관 파일 ID (STT 키워드 주입용)
        Long fileId;
    }

    // 세션 ID별로 SessionContext를 보관하는 맵 (동시성 안전)
    private final java.util.Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();

    // 주기 작업 실행용 공용 스케줄러 (캡처/처리 2스레드 운용)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Autowired(required = false)
    private org.example.speaknotebackend.client.PythonAnnotationClient pythonClient;

    // 큐 용량 (백프레셔)
    @Value("${stt.queue.inbound.capacity:6}")
    private int INBOUND_QUEUE_CAPACITY;
    @Value("${stt.queue.outbound.capacity:6}")
    private int OUTBOUND_QUEUE_CAPACITY;

    // 웹소켓 -> Inbound 큐
    private void enqueueDropOldest(Deque<byte[]> q, byte[] item, int capacity) {
        while (q.size() >= capacity) {
            q.pollFirst();
            log.debug("[QUEUE DROP] Inbound drop_oldest triggered (capacity={}), newItemSizeBytes={}", capacity, item == null ? -1 : item.length);
        }
        q.offerLast(item);
    }

    // Python -> Outbound 큐
    private void enqueueDropOldest(Deque<Map<String,Object>> q, Map<String,Object> item, int capacity) {
        while (q.size() >= capacity) {
            q.pollFirst();
            log.debug("[QUEUE DROP] Outbound drop_oldest triggered (capacity={}), newItemKeys={}", capacity, item == null ? -1 : item.size());
        }
        q.offerLast(item);
    }


    /**
     * 생성자
     */
    public GoogleSpeechService(LectureRepository lectureRepository) {
        this.lectureRepository = lectureRepository;
    }

    /**
     * 애플리케이션 시작 시 Google STT 클라이언트를 초기화한다.
     */
    @PostConstruct
    public void initSpeechClient() {
        log.info("[GoogleSpeechService] STT 클라이언트 초기화 시작 - credentialsPath={}", credentialsPath);
        try {
            Resource resource = new ClassPathResource(credentialsPath);
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    resource.getInputStream()
            );

            // 인증 정보를 포함한 STT 클라이언트 설정
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            speechClient = SpeechClient.create(settings);
            log.info("✅ Google SpeechClient 초기화 완료");

        } catch (Exception e) {
            log.error("❌ Google STT 초기화 실패", e);
        }
    }

    /**
     * Google STT 스트리밍을 시작한다. (userId 포함)
     */
    public void startStreaming(WebSocketSession session, Long fileId, Long userId) {
        try {
            final String sessionId = session.getId();
            final SessionContext context = sessionContexts.computeIfAbsent(sessionId, k -> new SessionContext());
            if (context.scheduledTask != null && !context.scheduledTask.isDone()) {
                log.warn("이미 스케줄러가 실행 중입니다. session={}", sessionId);
            }
            context.webSocketSession = session;
            context.fileId = fileId;
            context.streamingStarted.set(true);
            context.initialConfigSent.set(false);
            
            // userId를 세션에 저장
            if (userId != null) {
                session.getAttributes().put("userId", userId);
                log.info("🔍 [STT] userId 세션에 저장: {}", userId);
            }

            // 1초 스텝으로 트리거를 평가(최종 문장 버퍼 기반)
            context.scheduledTask = scheduler.scheduleAtFixedRate(() -> {
                // 누적 문장 수가 최소 5문장 이상
                int count = context.textBuffer.getSentenceCount();
                log.info("🔍 [STT Buffer] 세션={}, 누적 문장 수={}/5", sessionId, count);
                
                if (count >= 5) {
                    String snapshot = context.textBuffer.getSnapshotAndClearIfEnough();
                    if (snapshot != null && !snapshot.isBlank()) {
                        long seq = context.seq.incrementAndGet();
                        String requestId = UUID.randomUUID().toString();

                        log.info("📤 [Python 전송] 세션={}, seq={}, requestId={}, 텍스트 길이={}", 
                                sessionId, seq, requestId, snapshot.length());
                        log.info("📤 [Python 전송] 텍스트 내용: {}", snapshot);

                        // 다음 단계에서 Python /text 호출에 사용될 메타 포함
                        Map<String,Object> payload = new HashMap<>();
                        payload.put("sessionId", sessionId);
                        payload.put("seq", seq);
                        payload.put("requestId", requestId);
                        payload.put("timestamp", System.currentTimeMillis());
                        payload.put("text", snapshot);

                        // Python /text 호출
                        if (pythonClient != null) {
                            log.info("🚀 [Python 호출] postTextFireAndForget 시작 - userId={}, sessionId={}, seq={}", 
                                    userId, sessionId, seq);
                            try {
                                pythonClient.postTextFireAndForget(
                                        userId,
                                        sessionId,
                                        seq,
                                        snapshot,
                                        "ko-KR",
                                        requestId
                                );
                                payload.put("status", "queued");
                                log.info("✅ [Python 호출] postTextFireAndForget 완료 - requestId={}", requestId);
                            } catch (Exception e) {
                                log.error("❌ [Python 호출] postTextFireAndForget 실패: ", e);
                                payload.put("status", "error");
                            }
                        } else {
                            payload.put("status", "skipped");
                            log.warn("⚠️ [Python 호출] pythonClient가 null - 호출 건너뜀");
                        }

                        // 관측용 WS 송출
                        enqueueDropOldest(context.outboundQueue, payload, OUTBOUND_QUEUE_CAPACITY);
                        flushOutbound(session, context);
                    } else {
                        log.warn("⚠️ [STT Buffer] snapshot이 null이거나 비어있음");
                    }
                } else {
                    log.debug("⏳ [STT Buffer] 문장 수 부족 - {}/5", count);
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);

            // 양방향 스트리밍을 위한 BidiStreamObserver 구현
            speechClient.streamingRecognizeCallable().call(
                    new BidiStreamObserver<>() {

                        @Override
                        public void onStart(StreamController controller) {
                            log.info("STT 스트리밍 시작됨");
                        }

                        @Override
                        public void onResponse(StreamingRecognizeResponse response) {
                            // Google이 반환한 음성 인식 결과를 처리
                            for (StreamingRecognitionResult result : response.getResultsList()) {
                                if (result.getAlternativesCount() > 0) {
                                    String transcript = result.getAlternatives(0).getTranscript();
                                    boolean isFinal = result.getIsFinal();
                                    log.info("[STT] {}: {}", isFinal ? "final" : "interim", transcript);
                                    if (isFinal) {
                                        int beforeCount = context.textBuffer.getSentenceCount();
                                        context.textBuffer.appendTranscript(transcript);
                                        int afterCount = context.textBuffer.getSentenceCount();
                                        log.info("📝 [STT Buffer] 문장 추가 - 세션={}, 이전={}, 이후={}, 추가된 텍스트='{}'", 
                                                sessionId, beforeCount, afterCount, transcript);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.error("STT 오류", t);
                        }

                        @Override
                        public void onComplete() {
                            log.info("STT 스트림 종료됨");
                        }

                        @Override
                        public void onReady(ClientStream<StreamingRecognizeRequest> stream) {
                            log.info("STT 스트림 전송 준비 완료 (session={})", sessionId);
                            context.requestStream = stream;

                            // 초기 환경설정 요청 전송
                            if (sendInitialRequest(context.requestStream, context.fileId)) {
                                context.initialConfigSent.set(true);
                            }
                            context.streamingStarted.set(true);
                        }
                    },
                    GrpcCallContext.createDefault()  // gRPC 호출 컨텍스트
            );

        } catch (Exception e) {
            log.error("STT 스트리밍 시작 실패", e);
        }
    }

    /**
     * 초기 STT 환경설정 요청을 Google에 전송한다.
     * - 샘플레이트, 인코딩, 언어 등
     */
    private boolean sendInitialRequest(ClientStream<StreamingRecognizeRequest> requestStream, Long fileId) {
        try {
            SpeechContext.Builder scBuilder = SpeechContext.newBuilder().setBoost(20.0f);
            // Lecture.tags 기반 키워드 주입
            try {
                Lecture lecture = (fileId == null) ? null : lectureRepository.findByLectureFile_Id(fileId);
                String tagsString = null;
                if (lecture != null) tagsString = lecture.getTags();
                
                addTagsToSpeechContext(scBuilder, tagsString);
            } catch (Exception ignored) {}
            // 키워드가 하나도 없으면 기본값 몇 개만 보조적으로 유지
            SpeechContext sttContext = scBuilder
                    .addPhrases("STT")
                    .build();

            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("ko-KR") // 기본 언어 : 한국어
                    .setEnableAutomaticPunctuation(true) // 자동 문장부호 활성화
                    .addSpeechContexts(sttContext)
                    .build();

            StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setInterimResults(false)    // 중간 인식 결과 포함 X
                    .setSingleUtterance(false)  // 단일 발화로 자동 종료 X
                    .build();

            StreamingRecognizeRequest initialRequest = StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build();

            requestStream.send(initialRequest);
            log.info("STT 초기 설정 전송 완료");

            return true;
        } catch (Exception e) {
            log.error("STT 초기 요청 전송 실패", e);
            return false;
        }
    }

    private void addTagsToSpeechContext(SpeechContext.Builder builder, String tags) {
        if (tags == null) return;
        String[] arr = tags.split(",");
        for (String raw : arr) {
            if (raw == null) continue;
            String kw = raw.trim();
            if (!kw.isEmpty()) builder.addPhrases(kw);
        }
    }

    /**
     * 프론트엔드에서 수신한 오디오 chunk를 실시간으로 Google STT 서버에 전송한다.
     */
    public void sendAudioChunk(WebSocketSession session, byte[] audioBytes, Long fileId) {
        String sessionId = session.getId();
        SessionContext context = sessionContexts.get(sessionId);
        if (context == null || !context.streamingStarted.get() || context.requestStream == null || !context.initialConfigSent.get()) {
            if (context != null && !context.initialConfigSent.get()) {
                log.debug("초기 설정 전송 전 오디오 수신 - 무시");
            }
            return;
        }

        try {
            // 웹소켓 수신 스레드는 enqueue만 수행
            enqueueDropOldest(context.inboundQueue, audioBytes, INBOUND_QUEUE_CAPACITY);
            // 가능한 만큼 즉시 전송 (간단 동기 flush)
            drainInboundAsync(context);
        } catch (Exception e) {
            log.warn("오디오 chunk 전송 실패", e);
        }
    }

    /**
     * 세션의 Inbound 큐에 쌓인 오디오 청크를 하나씩 꺼내서 Google STT gRPC 스트림에 전송한다.
     * 스레드 풀에서 비동기로 처리한다.
     */
    @Async("sttTaskExecutor")
    protected CompletableFuture<Void> drainInboundAsync(SessionContext context) {
        try {
            byte[] chunk;
            while ((chunk = context.inboundQueue.pollFirst()) != null) {
                StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(chunk))
                        .build();
                context.requestStream.send(audioRequest);
            }
        } catch (Exception e) {
            log.warn("Inbound drain 실패", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 세션의 Outbound 큐에 쌓인 메시지를 하나씩 꺼내서 클라이언트로 전송한다.
     */
    private void flushOutbound(WebSocketSession session, SessionContext context) {
        try {
            if (!session.isOpen()) {
                log.warn("⚠️ [CALLBACK] WebSocket 세션이 닫혀있음 - sessionId={}", session.getId());
                context.outboundQueue.clear();
                return;
            }
            
            log.info("📤 [CALLBACK] flushOutbound 시작 - sessionId={}, 큐 크기={}", session.getId(), context.outboundQueue.size());
            
            java.util.Map<String,Object> msg;
            ObjectMapper mapper = new ObjectMapper();
            int sentCount = 0;
            while ((msg = context.outboundQueue.pollFirst()) != null) {
                String json = mapper.writeValueAsString(msg);
                log.info("📤 [CALLBACK] WebSocket 메시지 전송 - sessionId={}, payload={}", session.getId(), json);
                session.sendMessage(new TextMessage(json));
                sentCount++;
            }
            
            log.info("✅ [CALLBACK] flushOutbound 완료 - sessionId={}, 전송된 메시지 수={}", session.getId(), sentCount);
        } catch (Exception e) {
            log.error("❌ [CALLBACK] Outbound 전송 실패 - sessionId={}, error: ", session.getId(), e);
        }
    }

    /**
     * Python 콜백 결과를 세션별 Outbound 큐에 적재하고 즉시 전송한다.
     */
    public void enqueueOutboundFromCallback(org.example.speaknotebackend.dto.request.AnnotationCallbackRequest.AnnotationResult result) {
        log.info("🔄 [CALLBACK] enqueueOutboundFromCallback 시작 - sessionId={}, seq={}, requestId={}", 
                result.getSessionId(), result.getSeq(), result.getRequestId());
        
        try {
            // 입력값 검증
            if (result.getSessionId() == null || result.getSessionId().isEmpty()) {
                log.error("❌ [CALLBACK] sessionId가 null 또는 빈 문자열");
                throw new IllegalArgumentException("sessionId가 null 또는 빈 문자열입니다");
            }
            
            SessionContext context = sessionContexts.get(result.getSessionId());
            if (context == null) {
                log.error("❌ [CALLBACK] 세션 컨텍스트 없음 - sessionId={}", result.getSessionId());
                log.error("❌ [CALLBACK] 현재 활성 세션들: {}", sessionContexts.keySet());
                throw new BaseException(WS_CONTEXT_NOT_FOUND, "세션 컨텍스트 없음: " + result.getSessionId());
            }
            log.info("✅ [CALLBACK] 세션 컨텍스트 찾음 - sessionId={}", result.getSessionId());

            // 멱등 처리: requestId 중복이면 무시
            if (isDuplicateRequest(context, result.getRequestId())) {
                log.info("⚠️ [CALLBACK] 중복 requestId는 무시: {}", result.getRequestId());
                return;
            }

            // 순서 보장: 과거 seq 응답은 무시
            long lastSeq = context.lastDeliveredSeq.get();
            Long currSeq = result.getSeq();
            if (currSeq != null && currSeq < lastSeq) {
                log.info("⚠️ [CALLBACK] 과거 seq는 무시: curr={} < last={}", currSeq, lastSeq);
                return;
            }

            Map<String,Object> payload = new HashMap<>();
            if (result.getUserId()==null) {
                payload.put("userId",1L);
            }
            else {
                payload.put("userId", result.getUserId());
            }
            payload.put("seq", result.getSeq());
            payload.put("audioText", result.getAudioText());
            payload.put("annotation", result.getAnnotation());
            payload.put("page", result.getPage());
            payload.put("answerState", result.getAnswerState());
            payload.put("timestamp", System.currentTimeMillis());

            log.info("📦 [CALLBACK] 페이로드 생성 완료 - payload={}", payload);
            enqueueDropOldest(context.outboundQueue, payload, OUTBOUND_QUEUE_CAPACITY);
            log.info("📦 [CALLBACK] Outbound 큐에 추가 완료");

            WebSocketSession session = context.webSocketSession;
            if (session != null) {
                log.info("📤 [CALLBACK] WebSocket으로 전송 시작 - sessionId={}", result.getSessionId());
                flushOutbound(session, context);
                // 전송 성공으로 간주하고 마지막 seq 갱신 및 requestId 기록
                if (currSeq != null && currSeq >= lastSeq) {
                    context.lastDeliveredSeq.set(currSeq);
                }
                rememberRequestId(context, result.getRequestId());
            } else {
                throw new BaseException(WS_SESSION_NOT_FOUND, "WebSocket 세션 없음: " + result.getSessionId());
            }
        } catch (Exception e) {
            if (e instanceof BaseException be) {
                throw be;
            }
            throw new BaseException(UNEXPECTED_ERROR, e.getMessage());
        }
    }

    private boolean isDuplicateRequest(SessionContext context, String requestId) {
        if (requestId == null) return false;
        return context.recentRequestIds.contains(requestId);
    }

    private void rememberRequestId(SessionContext context, String requestId) {
        if (requestId == null) return;
        // 용량 제한 200
        final int LIMIT = 200;
        if (context.recentRequestIds.add(requestId)) {
            context.recentRequestOrder.offerLast(requestId);
            while (context.recentRequestOrder.size() > LIMIT) {
                String old = context.recentRequestOrder.pollFirst();
                if (old != null) context.recentRequestIds.remove(old);
            }
        }
    }

    /**
     * 스트리밍 세션을 종료하고 리소스를 해제한다.
     */
    public void stopStreaming(WebSocketSession session) {
        try {
            String sessionId = session.getId();
            SessionContext context = sessionContexts.remove(sessionId);
            if (context != null) {
                if (context.requestStream != null) {
                    context.requestStream.closeSend();
                }
                context.streamingStarted.set(false);
                    context.textBuffer.clearAll();
                if (context.scheduledTask != null && !context.scheduledTask.isCancelled()) {
                    context.scheduledTask.cancel(true);
                }
                context.webSocketSession = null;
                log.info("STT 스트리밍 종료 (session={})", sessionId);
            }
        } catch (Exception e) {
            log.warn("STT 종료 중 오류", e);
        }
    }
}
