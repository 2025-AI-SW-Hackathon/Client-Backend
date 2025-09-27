package org.example.speaknotebackend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.service.GoogleSpeechService;
import org.example.speaknotebackend.service.TextRefineService;
import org.example.speaknotebackend.global.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.example.speaknotebackend.config.UserDetailsImpl;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioWebSocketHandler extends BinaryWebSocketHandler {

    private final GoogleSpeechService googleSpeechService;
    private final JwtService jwtService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long fileId = resolveFileId(session);
        if (fileId == null) {
            log.warn("fileId 누락 또는 유효하지 않음. STT만 진행: {}", session.getId());
        } else {
            session.getAttributes().put("fileId", fileId);
        }

        // 사용자 인증 정보 가져오기 (WebSocket에서는 SecurityContext가 비어있을 수 있음)
        Long userId = null;
        try {
            // 1. SecurityContext에서 먼저 시도
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
                userId = userDetails.getUserId();
                log.info("🔍 [WebSocket] SecurityContext에서 사용자 찾음: userId={}", userId);
            } else {
                // 2. WebSocket 쿼리 파라미터에서 connectionToken 검증
                String connectionToken = resolveConnectionToken(session);
                if (connectionToken != null) {
                    try {
                        // TODO: connectionToken을 Redis나 메모리에 저장하고 검증하는 로직 추가
                        // 지금은 간단히 UUID 형식인지만 확인
                        UUID.fromString(connectionToken);
                        // 임시로 userId=1로 설정 (실제로는 connectionToken으로 userId 조회)
                        userId = 1L;
                        log.info("🔍 [WebSocket] connectionToken으로 사용자 찾음: userId={}", userId);
                    } catch (Exception e) {
                        log.warn("⚠️ [WebSocket] connectionToken 검증 실패: ", e);
                    }
                } else {
                    log.warn("⚠️ [WebSocket] connectionToken 없음");
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [WebSocket] 사용자 인증 정보 가져오기 실패: ", e);
        }

        log.info("클라이언트 WebSocket 연결됨: {}, fileId={}, userId={}", session.getId(), fileId, userId);

        // 세션+fileId+userId 기반으로 STT 스트리밍 시작
        googleSpeechService.startStreaming(session, fileId, userId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ByteBuffer payload = message.getPayload();
        byte[] audioBytes = new byte[payload.remaining()];
        payload.get(audioBytes);

        Long fileId = (Long) session.getAttributes().get("fileId");

        // 세션별 컨텍스트를 사용하도록 서비스로 전달
        googleSpeechService.sendAudioChunk(session, audioBytes, fileId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket 연결 종료 {}", session.getId());
        googleSpeechService.stopStreaming(session);
    }

    // 사용자가 녹음 중지 누르면 종료
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String msg = message.getPayload();
        if ("stop-recording".equals(msg)) {
            log.info("클라이언트로부터 stop-recording 수신");
            googleSpeechService.stopStreaming(session);
        }
        // (선택) {"type":"init","fileId":123} 같은 초기화 메시지도 허용하고 싶다면 여기서 처리해도 됨
    }

    private Long resolveFileId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;
            String q = uri.getQuery(); // e.g. "fileId=11&foo=bar"
            for (String pair : q.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) {
                    String k = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
                    String v = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                    if ("fileId".equals(k)) {
                        try {
                            return Long.parseLong(v);
                        } catch (NumberFormatException ignore) {
                            return null;
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private String resolveConnectionToken(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;
            String q = uri.getQuery(); // e.g. "fileId=11&token=abc123"
            for (String pair : q.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) {
                    String k = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
                    String v = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                    if ("token".equals(k)) {
                        return v;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [WebSocket] connectionToken 파싱 실패: ", e);
        }
        return null;
    }
}
