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

        // 파일 저장(로그인 사용자는 LectureFile 생성됨)
        final Long fileId = pdfService.saveTempPDF(file, userId);

        // ⬇️ FastAPI로 파일 + userId + fileId 함께 전송
        final String fastApiResponse = pdfService.sendPdfFileToFastAPI(file, userId, fileId);
        System.out.println("FastAPI 응답: " + fastApiResponse);

        String status = "error";
        String errorMessage = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(fastApiResponse);
            if (json.has("error")) {
                errorMessage = json.get("error").asText();
                status = "error";
            } else if (json.has("status")) {
                status = json.get("status").asText(); // FastAPI가 status 주면 그대로 사용
            } else {
                // 상태 필드가 없으면 기본값
                status = "ready";
            }
        } catch (Exception e) {
            System.out.println("FastAPI 응답 파싱 에러: " + e.getMessage());
            status = "error";
            errorMessage = "FastAPI response parse error";
        }

        Map<String, Serializable> resp = new HashMap<>();
        resp.put("fileId", fileId);
        resp.put("status", status);
        if (errorMessage != null) resp.put("error", errorMessage);

        return ResponseEntity.ok(resp);
    }
}
