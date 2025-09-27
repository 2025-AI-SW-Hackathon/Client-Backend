package org.example.speaknotebackend.controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.dto.request.AnnotationCallbackRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.example.speaknotebackend.common.response.BaseResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.example.speaknotebackend.service.GoogleSpeechService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/callbacks")
public class CallbackController {

    private final GoogleSpeechService googleSpeechService;

    @Operation(
            summary = "주석 결과 콜백 수신",
            description = "Python 서버가 생성한 주석 결과를 수신하여 해당 WebSocket 세션으로 전달합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "콜백 처리 완료 (본문 없음)")
    })
    @PostMapping("/annotations")
    public BaseResponse<Void> onAnnotations(@RequestBody AnnotationCallbackRequest body) {
        try {
            log.info("📥 [CALLBACK] 파이썬에서 콜백 요청 수신 - totalNum={}, results={}", 
                    body.getTotalNum(), body.getResults() == null ? 0 : body.getResults().size());
            
            if (body.getResults() != null) {
                for (AnnotationCallbackRequest.AnnotationResult result : body.getResults()) {
                    log.info("📥 [CALLBACK] 결과 처리 시작 - userId={}, sessionId={}, seq={}, requestId={}", 
                            result.getUserId(), result.getSessionId(), result.getSeq(), result.getRequestId());
                    log.info("📥 [CALLBACK] 콘텐츠 - audioText='{}', annotation='{}', page={}, answerState={}", 
                            result.getAudioText(), result.getAnnotation(), result.getPage(), result.getAnswerState());
                    
                    try {
                        googleSpeechService.enqueueOutboundFromCallback(result);
                        log.info("✅ [CALLBACK] 결과 처리 완료 - sessionId={}, seq={}", result.getSessionId(), result.getSeq());
                    } catch (Exception e) {
                        log.error("❌ [CALLBACK] 결과 처리 실패 - sessionId={}, seq={}, error: ", 
                                result.getSessionId(), result.getSeq(), e);
                        throw e; // 예외를 다시 던져서 상위에서 처리
                    }
                }
            } else {
                log.warn("⚠️ [CALLBACK] results가 null입니다");
            }
            
            return new BaseResponse<>(null);
        } catch (Exception e) {
            log.error("❌ [CALLBACK] 콜백 처리 중 예외 발생: ", e);
            throw e; // 예외를 다시 던져서 GlobalExceptionHandler에서 처리
        }
    }
}