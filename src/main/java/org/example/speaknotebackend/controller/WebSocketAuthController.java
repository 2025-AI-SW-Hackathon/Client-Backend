package org.example.speaknotebackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.global.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/websocket")
@RequiredArgsConstructor
public class WebSocketAuthController {

    private final JwtService jwtService;

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticateWebSocket(@RequestHeader("Authorization") String authHeader) {
        try {
            // Authorization 헤더에서 토큰 추출
            if (!authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid authorization header"));
            }
            
            String token = authHeader.substring(7);
            Long userId = jwtService.getUserIdByToken(token);
            
            // WebSocket 연결용 임시 토큰 생성
            String connectionToken = UUID.randomUUID().toString();
            
            log.info("🔑 [WebSocket Auth] 사용자 인증 성공: userId={}, connectionToken={}", userId, connectionToken);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", userId,
                "connectionToken", connectionToken
            ));
            
        } catch (Exception e) {
            log.error("❌ [WebSocket Auth] 인증 실패: ", e);
            return ResponseEntity.status(401).body(Map.of("error", "Authentication failed"));
        }
    }
}
