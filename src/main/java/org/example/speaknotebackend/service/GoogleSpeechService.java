package org.example.speaknotebackend.service;

import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.BidiStreamObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.util.SttTextBuffer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.FileInputStream;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class GoogleSpeechService {

    // Google STT 클라이언트 객체 (gRPC 커넥션/호출의 엔트리 포인트)
    private SpeechClient speechClient;

    /**
     * 세션별 상태 컨텍스트
     * - WebSocket 세션 ID를 키로, STT 스트림/버퍼/스케줄 등의 상태를 분리 관리
     */
    private static class SessionContext {
        // 세션별 STT 델타 누적 버퍼 (유한 버퍼, drop_oldest)
        final SttTextBuffer textBuffer = new SttTextBuffer();
        // gRPC 스트리밍이 시작/유지되고 있는지 여부 (멀티스레드 안전)
        final AtomicBoolean streamingStarted = new AtomicBoolean(false); // AtomicBoolean : 동시성 안전한 불리언
        // 초기 설정 패킷(StreamingRecognitionConfig) 전송 완료 여부
        final AtomicBoolean initialConfigSent = new AtomicBoolean(false);
        // Google STT로 오디오 청크를 전송하는 gRPC 요청 스트림 핸들
        volatile ClientStream<StreamingRecognizeRequest> requestStream;
        // 2초 지연 후 1초 주기로 버퍼를 비우고 후속 처리를 수행하는 작업 핸들
        volatile ScheduledFuture<?> scheduledTask;
        // 세션별 Inbound(오디오 바이트) 큐 - drop_oldest
        final Deque<byte[]> inboundQueue = new ConcurrentLinkedDeque<>();
        // 세션별 Outbound(클라이언트로 보낼 메시지) 큐 - drop_oldest
        final Deque<String> outboundQueue = new ConcurrentLinkedDeque<>();
    }

    // 세션 ID별로 SessionContext를 보관하는 맵 (동시성 안전)
    private final java.util.Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();

    // 주기 작업 실행용 공용 스케줄러 (캡처/처리 2스레드 운용)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    // (옵션) 텍스트 후처리/정제를 위한 서비스 – 현재 STT 테스트 단계에서는 비활성화 상태
    private final TextRefineService textRefineService;
    // 큐 용량 (백프레셔)
    @Value("${stt.queue.inbound.capacity:6}")
    private int INBOUND_QUEUE_CAPACITY;
    @Value("${stt.queue.outbound.capacity:6}")
    private int OUTBOUND_QUEUE_CAPACITY;

    private void enqueueDropOldest(Deque<byte[]> q, byte[] item, int capacity) {
        while (q.size() >= capacity) {
            q.pollFirst();
            log.debug("[QUEUE DROP] Inbound drop_oldest triggered (capacity={}), newItemSizeBytes={}", capacity, item == null ? -1 : item.length);
        }
        q.offerLast(item);
    }

    private void enqueueDropOldest(Deque<String> q, String item, int capacity) {
        while (q.size() >= capacity) {
            q.pollFirst();
            log.debug("[QUEUE DROP] Outbound drop_oldest triggered (capacity={}), newItemLength={}", capacity, item == null ? -1 : item.length());
        }
        q.offerLast(item);
    }


    /**
     * 애플리케이션 시작 시 Google STT 클라이언트를 초기화한다.
     */
    public GoogleSpeechService(TextRefineService textRefineService) throws Exception {
        this.textRefineService = textRefineService;
        log.info("[GoogleSpeechService] 생성자 진입");
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream("src/main/resources/stt-credentials.json")
            );

            // 인증 정보를 포함한 STT 클라이언트 설정
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            speechClient = SpeechClient.create(settings);
            log.info("Google SpeechClient 초기화 완료");

        } catch (Exception e) {
            log.error("Google STT 초기화 실패", e);
        }
    }

    /**
     * Google STT 스트리밍을 시작한다.
     */
    public void startStreaming(WebSocketSession session,Long fileId) {
        try {
            final String sessionId = session.getId();
            final SessionContext context = sessionContexts.computeIfAbsent(sessionId, k -> new SessionContext());
            if (context.scheduledTask != null && !context.scheduledTask.isDone()) {
                log.warn("이미 스케줄러가 실행 중입니다. session={}", sessionId);
            }
            context.streamingStarted.set(true);
            context.initialConfigSent.set(false);

            // 2초 윈도우(버퍼는 SttTextBuffer가 유지), 1초 스텝으로 주기적 전송
            context.scheduledTask = scheduler.scheduleAtFixedRate(() -> {
                String aggregatedText = context.textBuffer.getAccumulatedContextAndClear();
                log.warn("[AI 전송 비활성화] 누적 context (session={}): {}", sessionId, aggregatedText);
                if (aggregatedText != null && !aggregatedText.isBlank()) {
                    // Google STT만 테스트하기 위해 AI 서버 전송 로직을 임시 비활성화합니다.
                    // 필요 시 아래 원본 로직을 복구하기
                    /*
                    try {
                        Map<String,Object> result = textRefineService.refine(aggregatedText,fileId,session.getId());
                        log.info("AI 서버 정제 결과: {}", result);

                        Map<String, Object> payload = new HashMap<>();
                        payload.put("refinedText", result.get("refinedText"));
                        payload.put("voice",result.get("voice"));
                        payload.put("answerState", result.get("answerState"));
                        payload.put("pageNumber", result.get("pageNumber"));

                        String refinedText = String.valueOf(result.get("refinedText")).trim();
                        System.out.println(result.get("refinedText"));
                        // 조건 1: 시작이 "에러"로 시작
                        boolean startsWithError = refinedText.startsWith("에러");

                        // 조건 2: 전체 내용에 "에러" 단어가 3번 이상 포함
                        long errorCount = refinedText.chars()
                                .mapToObj(c -> (char) c)
                                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                                .toString()
                                .split("에러", -1).length - 1; // "에러" 등장 횟수

                        boolean tooManyErrors = errorCount >= 3;

                        // 조건 3: 전체 길이가 너무 짧은 경우
                        boolean tooShort = refinedText.length() < 15;

                        if (startsWithError || tooManyErrors || tooShort) {
                            log.info("전송 생략 - 이유: 시작 '에러'={}, 에러빈도={}, 길이={}", startsWithError, errorCount, refinedText.length());
                            return;
                        }

                        ObjectMapper mapper = new ObjectMapper();
                        String json = mapper.writeValueAsString(payload);
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(json));
                        } else {
                            log.warn("WebSocket 세션이 이미 닫혔습니다.");
                        }

                        log.info("정제된 결과 WebSocket 전송 완료");
                    } catch (Exception e) {
                        log.error("AI 정제 및 전송 중 오류", e);
                    }
                    */
                    enqueueDropOldest(context.outboundQueue, aggregatedText, OUTBOUND_QUEUE_CAPACITY);
                    flushOutbound(session, context);
                    return;
                }
            }, 2000, 1000, TimeUnit.MILLISECONDS); // 최적의 파라미터 (2초 후 최초 실행, 이후 1초마다 반복)

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
                                    if (isFinal) context.textBuffer.append(transcript);
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
                            if (sendInitialRequest(context.requestStream)) {
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
    private boolean sendInitialRequest(ClientStream<StreamingRecognizeRequest> requestStream) {
        try {
            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("ko-KR") // 기본 언어 : 한국어
                    .setEnableAutomaticPunctuation(true) // 자동 문장부호 활성화
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
            enqueueDropOldest(context.inboundQueue, audioBytes, INBOUND_QUEUE_CAPACITY);
            // 가능한 만큼 즉시 전송 (간단 동기 flush)
            byte[] chunk;
            while ((chunk = context.inboundQueue.pollFirst()) != null) {
                StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(chunk))
                        .build();
                context.requestStream.send(audioRequest);
            }
        } catch (Exception e) {
            log.warn("오디오 chunk 전송 실패", e);
        }
    }

    private void flushOutbound(WebSocketSession session, SessionContext context) {
        try {
            if (!session.isOpen()) {
                context.outboundQueue.clear();
                return;
            }
            String msg;
            ObjectMapper mapper = new ObjectMapper();
            while ((msg = context.outboundQueue.pollFirst()) != null) {
                String json = mapper.writeValueAsString(java.util.Map.of(
                        "refinedText", msg
                ));
                log.info("[WS OUTBOUND] session={} payload={}", session.getId(), json);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("Outbound 전송 실패", e);
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
                log.info("STT 스트리밍 종료 (session={})", sessionId);
            }
        } catch (Exception e) {
            log.warn("STT 종료 중 오류", e);
        }
    }
}
