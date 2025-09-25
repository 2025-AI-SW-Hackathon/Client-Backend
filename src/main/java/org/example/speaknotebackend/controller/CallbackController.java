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
        log.info("[CALLBACK] totalNum={} results={}", body.getTotalNum(), body.getResults() == null ? 0 : body.getResults().size());
        if (body.getResults() != null) {
            for (AnnotationCallbackRequest.AnnotationResult result : body.getResults()) {
                googleSpeechService.enqueueOutboundFromCallback(result);
            }
        }
        return new BaseResponse<>(null);
    }
}