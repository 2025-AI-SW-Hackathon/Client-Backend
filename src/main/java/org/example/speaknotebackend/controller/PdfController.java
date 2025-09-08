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
        // 인증된 사용자가 있으면 userId 사용, 없으면 null (게스트)
        Long userId = null;
        if (userDetails != null) {
            userId = userDetails.getUserId();
        }
        
        Long fileId = pdfService.saveTempPDF(file, userId);
        String fastApiResponse = pdfService.sendPdfFileToFastAPI(file);  //응답 받아오기
        System.out.println("FastAPI 응답: " + fastApiResponse);  //로그 출력

        String status = "error";
        String errorMessage = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(fastApiResponse);
            
            // FastAPI에서 에러 응답인지 확인
            if (jsonNode.has("error")) {
                errorMessage = jsonNode.get("error").asText();
                System.out.println("FastAPI 에러: " + errorMessage);
                status = "error";
            } else if (jsonNode.has("status")) {
                status = jsonNode.get("status").asText();
                System.out.println("FastAPI 응답 상태: " + status);
            } else {
                System.out.println("FastAPI 응답에 status 필드가 없습니다: " + fastApiResponse);
                status = "error";
            }
        } catch (Exception e) {
            System.out.println("FastAPI 응답 파싱 에러: " + e.getMessage());
            e.printStackTrace();
            status = "error";
        }

        Map<String, Serializable> response = new HashMap<>();
        response.put("fileId", fileId);
        response.put("status", status);
        if (errorMessage != null) {
            response.put("error", errorMessage);
        }
        
        return ResponseEntity.ok(response);
    }
}
