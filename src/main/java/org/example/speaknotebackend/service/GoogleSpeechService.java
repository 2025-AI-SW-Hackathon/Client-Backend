package org.example.speaknotebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
 import com.google.api.gax.grpc.GrpcCallContext;
 import com.google.api.gax.rpc.BidiStreamObserver;
 import com.google.api.gax.rpc.ClientStream;
 import com.google.api.gax.rpc.StreamController;
 import com.google.auth.oauth2.GoogleCredentials;
 import com.google.cloud.speech.v1.*;
 import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.util.SttTextBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
public class GoogleSpeechService {

    // === 새 옵션: 환경변수로 STT on/off ===
    private final boolean sttEnabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("STT_ENABLED", "false")
    );

    // ===== STT 비활성화 모드면 전부 null/미사용 =====
    // private SpeechClient speechClient;
    // private ClientStream<StreamingRecognizeRequest> requestStream;

    private Consumer<String> transcriptConsumer; // 사용 중이면 유지
    private final AtomicBoolean streamingStarted = new AtomicBoolean(false);

    private final SttTextBuffer textBuffer = new SttTextBuffer();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;

    private final TextRefineService textRefineService;

    // 기본 dummy 컨텍스트(네가 원하는 문구로 수정 가능)
    private static final String DUMMY_CONTEXT = "연습용입니다";

    public GoogleSpeechService(TextRefineService textRefineService) {
        this.textRefineService = textRefineService;
        log.info("[GoogleSpeechService] 생성자 진입. STT_ENABLED={}", sttEnabled);

        if (!sttEnabled) {
            log.warn("STT 비활성화 모드로 동작합니다. Google STT 초기화/연결을 생략합니다.");
            return;
        }

        // ===== STT 활성화 모드일 때만 초기화 =====
        try {
            // GoogleCredentials credentials = GoogleCredentials.fromStream(
            //         new FileInputStream("src/main/resources/stt-credentials.json")
            // );
            // SpeechSettings settings = SpeechSettings.newBuilder()
            //         .setCredentialsProvider(() -> credentials)
            //         .build();
            // speechClient = SpeechClient.create(settings);
            // log.info("Google SpeechClient 초기화 완료");
        } catch (Exception e) {
            log.error("Google STT 초기화 실패", e);
        }
    }

    /**
     * 스트리밍 시작:
     * - STT_DISABLED(기본)일 때는 스케줄러만 켜고, dummy context를 정제하여 WS로 전송.
     * - STT_ENABLED일 때만 gRPC 스트리밍을 붙임.
     */
    public void startStreaming(WebSocketSession session) {
        try {
            if (scheduledTask != null && !scheduledTask.isDone()) {
                log.warn("이미 스케줄러가 실행 중입니다.");
                return;
            }
            streamingStarted.set(true);

            // ⏱ 15초 후 시작, 45초마다 전송(원하면 간격 바꿔도 됨)
            scheduledTask = scheduler.scheduleAtFixedRate(() -> {
                // String context = textBuffer.getAccumulatedContextAndClear();
                String context = DUMMY_CONTEXT; // 지금은 주석(요약)만 테스트하므로 더미 텍스트 고정

                if (context != null && !context.isBlank()) {
                    try {
                        Map<String, Object> result = textRefineService.refine(context);
                        // 실패 시 null 반환을 대비
                        if (result == null) result = new HashMap<>();

                        // null-safe 추출
                        String refinedText = Objects.toString(result.get("refinedText"), "").trim();
                        Object voice = result.getOrDefault("voice", "");
                        Object answerState = result.getOrDefault("answerState", "OK");
                        Object pageNumber = result.getOrDefault("pageNumber", 1);

                        // 간단한 필터(너가 준 조건 유지)
                        boolean startsWithError = refinedText.startsWith("에러");
                        int errorCount = refinedText.isEmpty() ? 0 : refinedText.split("에러", -1).length - 1;
                        boolean tooManyErrors = errorCount >= 3;
                        boolean tooShort = refinedText.length() < 15;
                        if (startsWithError || tooManyErrors || tooShort) {
                            log.info("전송 생략 - 이유: 시작'에러'={}, 에러빈도={}, 길이={}",
                                    startsWithError, errorCount, refinedText.length());
                            return;
                        }

                        Map<String, Object> payload = new HashMap<>();
                        payload.put("refinedText", refinedText);
                        payload.put("voice", voice);
                        payload.put("answerState", answerState);
                        payload.put("pageNumber", pageNumber);
                        // 필요 시 payload.put("refinedMarkdown", result.get("refinedMarkdown"));

                        String json = new ObjectMapper().writeValueAsString(payload);
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(json));
                            log.info("정제된 결과 WebSocket 전송 완료: {}", refinedText);
                        } else {
                            log.warn("WebSocket 세션이 이미 닫혔습니다.");
                        }
                    } catch (Exception e) {
                        // 업스테이지 401 등 외부 실패도 여기서 캐치
                        log.error("AI 정제 및 전송 중 오류", e);
                    }
                }
            }, 15, 45, TimeUnit.SECONDS);

            if (!sttEnabled) {
                // STT 비활성화 모드: 여기서 바로 리턴. gRPC 안 붙임.
                log.warn("STT 비활성화 모드: 음성 인식 연결을 생략합니다. (주석 전송만 동작)");
                return;
            }

            // ===== STT 활성화 모드일 때만 아래 실행 =====
            // speechClient.streamingRecognizeCallable().call(
            //         new BidiStreamObserver<StreamingRecognizeResponse>() {
            //             @Override
            //             public void onStart(StreamController controller) {
            //                 log.info("STT 스트리밍 시작됨");
            //             }
            //             @Override
            //             public void onResponse(StreamingRecognizeResponse response) {
            //                 response.getResultsList().forEach(result -> {
            //                     if (result.getAlternativesCount() > 0) {
            //                         String transcript = result.getAlternatives(0).getTranscript();
            //                         textBuffer.append(transcript);
            //                     }
            //                 });
            //             }
            //             @Override public void onError(Throwable t) { log.error("STT 오류", t); }
            //             @Override public void onComplete() { log.info("STT 스트림 종료됨"); }
            //             @Override
            //             public void onReady(ClientStream<StreamingRecognizeRequest> stream) {
            //                 log.info("STT 스트림 전송 준비 완료");
            //                 requestStream = stream;
            //                 sendInitialRequest(); // 초기 설정
            //                 streamingStarted.set(true);
            //             }
            //         },
            //         GrpcCallContext.createDefault()
            // );

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
                    .setInterimResults(true)    // 중간 인식 결과 포함
                    .setSingleUtterance(false)  // 단일 발화로 자동 종료 X
                    .build();

            StreamingRecognizeRequest initialRequest = StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build();

            requestStream.send(initialRequest);
            log.info("STT 초기 설정 전송 완료");

        } catch (Exception e) {
            log.error("STT 초기 요청 전송 실패", e);
        }
    }

    /**
     * 프론트엔드에서 수신한 오디오 chunk를 실시간으로 Google STT 서버에 전송한다.
     * @param audioBytes 오디오 chunk (LINEAR16 PCM)
     */
    public void sendAudioChunk(byte[] audioBytes) {
        if (!sttEnabled) return;
        // if (!streamingStarted.get() || requestStream == null) return;
        // try {
        //     StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
        //             .setAudioContent(ByteString.copyFrom(audioBytes))
        //             .build();
        //     requestStream.send(audioRequest);
        // } catch (Exception e) {
        //     log.warn("오디오 chunk 전송 실패", e);
        // }
    }

    public void stopStreaming() {
        try {
            // if (requestStream != null) {
            //     requestStream.closeSend();
            //     requestStream = null;
            // }
            streamingStarted.set(false);
            textBuffer.clearAll();

            if (scheduledTask != null && !scheduledTask.isCancelled()) {
                scheduledTask.cancel(true);
                log.info("STT 스케줄러 작업 종료");
            }
            log.info("STT 스트리밍 종료(모드: {})", sttEnabled ? "ENABLED" : "DISABLED");
        } catch (Exception e) {
            log.warn("STT 종료 중 오류", e);
        }
    }

    // STT 활성화 모드에서만 쓰는 초기 요청 (현재는 주석 처리)
    // private void sendInitialRequest() {
    //     try {
    //         RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
    //                 .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
    //                 .setSampleRateHertz(16000)
    //                 .setLanguageCode("ko-KR")
    //                 .build();
    //
    //         StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
    //                 .setConfig(recognitionConfig)
    //                 .setInterimResults(true)
    //                 .setSingleUtterance(false)
    //                 .build();
    //
    //         StreamingRecognizeRequest initialRequest = StreamingRecognizeRequest.newBuilder()
    //                 .setStreamingConfig(streamingConfig)
    //                 .build();
    //
    //         requestStream.send(initialRequest);
    //         log.info("STT 초기 설정 전송 완료");
    //     } catch (Exception e) {
    //         log.error("STT 초기 요청 전송 실패", e);
    //     }
    // }
}
