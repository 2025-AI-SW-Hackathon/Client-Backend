package org.example.speaknotebackend.domain.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.domain.speech.GoogleSpeechService;
import org.example.speaknotebackend.domain.websocket.WebSocketAuthController;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioWebSocketHandler extends BinaryWebSocketHandler {

    private final GoogleSpeechService googleSpeechService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long fileId = resolveFileId(session);
        if (fileId == null) {
            log.warn("fileId 누락 또는 유효하지 않음. STT만 진행: {}", session.getId());
        } else {
            session.getAttributes().put("fileId", fileId);
        }

        // connectionToken으로 userId 조회
        Long userId = null;
        String connectionToken = resolveConnectionToken(session);
        if (connectionToken != null) {
            userId = WebSocketAuthController.getUserIdByConnectionToken(connectionToken);
            if (userId != null) {
                log.info("🔍 [WebSocket] connectionToken으로 사용자 찾음: userId={}", userId);
            } else {
                log.warn("⚠️ [WebSocket] connectionToken으로 사용자를 찾을 수 없음: {}", connectionToken);
            }
        } else {
            log.warn("⚠️ [WebSocket] connectionToken 없음");
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
        
        // connectionToken 정리
        String connectionToken = resolveConnectionToken(session);
        if (connectionToken != null) {
            WebSocketAuthController.removeConnectionToken(connectionToken);
            log.info("🔍 [WebSocket] connectionToken 정리 완료: {}", connectionToken);
        }
        
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

    /**
     * WebSocket 연결 URL에서 fileId를 추출합니다.
     * 
     * @param session WebSocket 세션
     * @return fileId, 없으면 null
     * 
     * 예시 URL: ws://localhost:8080/ws/audio?fileId=123&token=abc-123-def
     * 반환값: 123L
     */
    private Long resolveFileId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) return null;
            
            String fileIdStr = UriComponentsBuilder.fromUri(uri)
                    .build()
                    .getQueryParams()
                    .getFirst("fileId");
            
            if (fileIdStr != null) {
                Long fileId = Long.parseLong(fileIdStr);
                log.info("🔍 [WebSocket] fileId 직접 전달: {}", fileId);
                return fileId;
            }
            
        } catch (Exception e) {
            log.warn("⚠️ [WebSocket] fileId 파싱 실패: ", e);
        }
        return null;
    }

    /**
     * WebSocket 연결 URL에서 connectionToken을 추출합니다.
     * 
     * @param session WebSocket 세션
     * @return connectionToken 문자열, 없으면 null
     * 
     * 예시 URL: ws://localhost:8080/ws/audio?fileId=123&token=abc-123-def
     * 반환값: "abc-123-def"
     */
    private String resolveConnectionToken(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) return null;
            
            return UriComponentsBuilder.fromUri(uri)
                    .build()
                    .getQueryParams()
                    .getFirst("token");
                    
        } catch (Exception e) {
            log.warn("⚠️ [WebSocket] connectionToken 파싱 실패: ", e);
        }
        return null;
    }
}
