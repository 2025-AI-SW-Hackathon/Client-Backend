package org.example.speaknotebackend.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class PythonAnnotationClient {

    private final WebClient webClient;
    private final Duration timeout;

    public PythonAnnotationClient(
            @Value("${python.api.base-url:http://localhost:8000}") String baseUrl,
            @Value("${python.api.timeout.ms:3000}") long timeoutMs
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    public void postTextFireAndForget(Long userId,
                                      String sessionId,
                                      long seq,
                                      String text,
                                      String lang,
                                      String requestId) {
        Map<String, Object> body = Map.of(
                "userId", userId,
                "sessionId", sessionId,
                "seq", seq,
                "text", text,
                "lang", lang,
                "requestId", requestId
        );

        webClient.post()
                .uri("/text")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toEntity(QueueResponse.class) // 202(Accepted) 응답 처리
                .timeout(timeout)
                .doOnNext((ResponseEntity<QueueResponse> response) -> {
                    if (response != null && response.getStatusCode().value() == 202 && response.getBody() != null) {
                        QueueResponse b = response.getBody();
                        log.info("[PYTHON /text] 파이썬 작업 큐에 들어감: jobId={} sessionId={} seq={}", b.jobId, b.sessionId, b.seq);
                    } else if (response != null) {
                        log.warn("[PYTHON /text] 응답 못 받음: {}", response.getStatusCode());
                    }
                })
                .doOnError(err -> log.warn("[PYTHON /text] API 호출 실패: {}", err.toString()))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }

    public static class QueueResponse {
        public Boolean success;
        public String status;
        public String jobId;
        public String sessionId;
        public Long seq;
    }
}


