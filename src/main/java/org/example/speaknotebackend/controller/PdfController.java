package org.example.speaknotebackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.service.PdfService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.example.speaknotebackend.config.UserDetailsImpl;

import java.util.HashMap;
import java.io.Serializable;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pdf")
public class PdfController {

    private final PdfService pdfService;

    @Operation(
            summary = "PDF 파일 업로드",
            description = "사용자가 업로드한 PDF 파일을 서버의 temp 디렉토리에 저장합니다."
    )
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Serializable>> uploadForModeling(@RequestParam("file") MultipartFile file,
                                                                 @AuthenticationPrincipal UserDetailsImpl userDetails) {
        // 인증 사용자면 userId, 아니면 null(게스트)
        final Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        System.out.println("🔍 [PdfController] userDetails: " + (userDetails != null ? "존재" : "null"));
        System.out.println("🔍 [PdfController] userId: " + userId);
        System.out.println("🔍 [PdfController] 파일명: " + file.getOriginalFilename());
        
        // 파일 저장(로그인 사용자는 LectureFile 생성됨)
        final Long fileId = pdfService.saveTempPDF(file, userId);
        System.out.println("🔍 [PdfController] fileId: " + fileId);

        // sessionId - 세션 연결 전이므로 fileId를 문자열로 변환
        final String sessionId = fileId != null ? fileId.toString() : null;
        System.out.println("🔍 [PdfController] sessionId: " + sessionId);

        // FastAPI로 파일 + userId + fileId + sessionId 함께 전송
        final String fastApiResponse = pdfService.sendPdfFileToFastAPI(file, userId, fileId, sessionId);
        System.out.println("FastAPI 응답: " + fastApiResponse);

        String errorMessage = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(fastApiResponse);
            if (json.has("error")) {
                errorMessage = json.get("error").asText();
            } else if (!json.has("ok") || !json.get("ok").asBoolean()) {
                errorMessage = "Unknown response format";
            }
        } catch (Exception e) {
            System.out.println("FastAPI 응답 파싱 에러: " + e.getMessage());
            errorMessage = "FastAPI response parse error";
        }

        Map<String, Serializable> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("userId", userId);
        resp.put("fileId", fileId);
        resp.put("sessionID", sessionId);
        if (errorMessage != null) {
            resp.put("error", errorMessage);
        }

        return ResponseEntity.ok(resp);
    }
}
