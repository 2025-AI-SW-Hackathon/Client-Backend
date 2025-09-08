package org.example.speaknotebackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextRefineService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.server.url}")
    private String aiServerUrl;

    // ✅ 테스트 편의 스위치: 기본 true면 항상 목 데이터 반환 (외부 AI 불필요)
    @Value("${ai.mock:true}")
    private boolean mockAi;

    // ✅ 외부 AI 실패 시에도 목으로 대체할지 여부(실서비스 전환 시 false로)
    @Value("${ai.fallback-on-error:true}")
    private boolean fallbackOnError;

    /**
     * AI 정제 서버에 원본 텍스트를 전송하고, 성공 여부 판단.
     * 테스트 모드(mockAi=true)에서는 외부 호출 없이 목 결과 반환.
     */
    public Map<String, Object> refine(String originalText) {

        // ===== 0) MOCK 모드: 외부 호출 생략하고 바로 목 데이터 반환 =====
        if (mockAi) {
            return mockResult(originalText, null);
        }

        // ===== 1) (원래 코드) 외부 AI 호출 로직 =====
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Map.of는 null 허용 안 하므로 NPE 방지용으로 toString 처리
        Map<String, String> body = new HashMap<>();
        body.put("text", Objects.toString(originalText, ""));

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(aiServerUrl, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                // refinedText, refinedMarkdown 등을 그대로 반환
                Map<String, Object> result = response.getBody();
                if (result == null) {
                    // 빈 응답 대비
                    return mockOrFail(originalText, "AI_EMPTY_RESPONSE");
                }
                return result;
            } else {
                log.warn("AI 서버 응답 실패: {}", response.getStatusCode());
                return mockOrFail(originalText, "AI_BAD_STATUS_" + response.getStatusCode().value());
            }
        } catch (HttpStatusCodeException e) {
            // 401(크레딧/결제), 5xx 등
            log.error("AI 서버 요청 중 오류 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return mockOrFail(originalText, "AI_HTTP_" + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("AI 서버 요청 중 오류", e);
            return mockOrFail(originalText, "AI_EXCEPTION");
        }

        /*
        // ===== [원본 코드 – 남겨둠] =====
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(aiServerUrl, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK) { // 200
                return response.getBody(); // refinedText, refinedMarkdown 포함된 Map
            } else {
                log.warn("AI 서버 응답 실패: {}", response.getStatusCode());
            }
        } catch (Exception e) { // AI 서버가 꺼져있거나 예외가 발생한 경우
            log.error("AI 서버 요청 중 오류", e);
        }
        return Map.of( // 실패 시 기본값 (※ Map.of는 null 불가)
                "refinedText", "[AI 정제 실패]",
                "refinedMarkdown", null
        );
        */
    }

    /** 실패 시 목으로 대체하거나, 강제 실패 형태(프론트에서 표시 가능)로 반환 */
    private Map<String, Object> mockOrFail(String originalText, String errorTag) {
        if (fallbackOnError) return mockResult(originalText, errorTag);
        // 실패를 그대로 노출하고 싶다면 아래처럼 일관된 포맷으로 반환
        Map<String, Object> out = new HashMap<>();
        out.put("refinedText", "에러: " + errorTag);
        out.put("answerState", errorTag);
        out.put("voice", "neutral");
        out.put("pageNumber", 1);
        out.put("refinedMarkdown", null);
        return out;
    }

    /** 간단 목 결과 생성: 프론트 필터를 통과하도록 15자 이상 */
    private Map<String, Object> mockResult(String context, String errorTag) {
        Map<String, Object> out = new HashMap<>();
        String base = Objects.toString(context, "연습용입니다");
        String text = "연습용입니다: " + base + " (테스트용 목 결과 문장입니다. 충분한 길이를 유지합니다.)";

        out.put("refinedText", text);
        out.put("answerState", 0);
        out.put("voice", "neutral");
        out.put("pageNumber", 1);
        out.put("refinedMarkdown", "- " + text);
        return out;
    }
}
