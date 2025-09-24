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
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.FileInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class GoogleSpeechService {

    // Google STT 클라이언트 객체
    private SpeechClient speechClient;

    // Google에 오디오 chunk를 전송한 stream 객체
    private ClientStream<StreamingRecognizeRequest> requestStream;

    // 인식된 텍스트를 전달할 콜백 함수 (미사용)
    // private Consumer<String> transcriptConsumer;

    // 스트리밍 세션이 활성화 상태인지를 알려주는 flag
    private final AtomicBoolean streamingStarted = new AtomicBoolean(false);
    // 초기 설정 패킷(streaming_config) 전송 여부
    private final AtomicBoolean initialConfigSent = new AtomicBoolean(false);
    private final SttTextBuffer textBuffer = new SttTextBuffer();

    // 최적의 파라미터 (캡처/처리 2스레드 운용)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final TextRefineService textRefineService;
    private ScheduledFuture<?> scheduledTask;
    String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

    /**
     * 애플리케이션 시작 시 Google STT 클라이언트를 초기화한다.
     */
    public GoogleSpeechService(TextRefineService textRefineService) throws Exception {
        this.textRefineService = textRefineService;
        log.info("[GoogleSpeechService] 생성자 진입");
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
//                    new FileInputStream(credentialsPath)
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
            if (scheduledTask != null && !scheduledTask.isDone()) {
                log.warn("이미 스케줄러가 실행 중입니다.");
                return;
            }

            streamingStarted.set(true);
            initialConfigSent.set(false);

            // 2초 윈도우(버퍼는 SttTextBuffer가 유지), 1초 스텝으로 주기적 전송
            scheduledTask = scheduler.scheduleAtFixedRate(() -> {
                String context = textBuffer.getAccumulatedContextAndClear();
                log.warn("[AI 전송 비활성화] 누적 context: {}", context);
                if (context != null && !context.isBlank()) {
                    // Google STT만 테스트하기 위해 AI 서버 전송 로직을 임시 비활성화합니다.
                    // 필요 시 아래 원본 로직을 복구하기
                    /*
                    try {
                        Map<String,Object> result = textRefineService.refine(context,fileId,session.getId());
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
                                    textBuffer.append(transcript);
                                    boolean isFinal = result.getIsFinal();
                                    log.info("[STT] {}: {}", isFinal ? "final" : "interim", transcript);
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
                            log.info("STT 스트림 전송 준비 완료");
                            requestStream = stream;

                            // 초기 환경설정 요청 전송
                            sendInitialRequest();
                            streamingStarted.set(true);
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
    private void sendInitialRequest() {
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
            initialConfigSent.set(true);
            log.info("STT 초기 설정 전송 완료");

        } catch (Exception e) {
            log.error("STT 초기 요청 전송 실패", e);
        }
    }

    /**
     * 프론트엔드에서 수신한 오디오 chunk를 실시간으로 Google STT 서버에 전송한다.
     * @param audioBytes 오디오 chunk (LINEAR16 PCM)
     */
    public void sendAudioChunk(WebSocketSession session,byte[] audioBytes,Long fileId) {
        if (!streamingStarted.get() || requestStream == null) return;
        if (!initialConfigSent.get()) {
            log.debug("초기 설정 전송 전 오디오 수신 - 무시");
            return;
        }

        try {
            StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(audioBytes))
                    .build();
            requestStream.send(audioRequest);
        } catch (Exception e) {
            log.warn("오디오 chunk 전송 실패", e);
        }
    }

    /**
     * 스트리밍 세션을 종료하고 리소스를 해제한다.
     */
    public void stopStreaming(WebSocketSession session) {
        try {
            if (requestStream != null) {
                requestStream.closeSend();
                requestStream = null;
                streamingStarted.set(false);
                textBuffer.clearAll();  // 반드시 버퍼 초기화!
                log.info("STT 스트리밍 종료");
            }

            if (scheduledTask != null && !scheduledTask.isCancelled()) {
                scheduledTask.cancel(true);  // 실행 중인 작업도 중단
                log.info("STT 스케줄러 작업 종료");
            }
        } catch (Exception e) {
            log.warn("STT 종료 중 오류", e);
        }
    }
}
