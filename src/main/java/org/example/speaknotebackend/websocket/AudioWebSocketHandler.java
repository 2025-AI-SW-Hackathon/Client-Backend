package org.example.speaknotebackend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.service.GoogleSpeechService;
import org.example.speaknotebackend.service.TextRefineService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioWebSocketHandler extends BinaryWebSocketHandler {

    private final TextRefineService textRefineService;
    private final GoogleSpeechService googleSpeechService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long fileId = resolveFileId(session);
        session.getAttributes().put("fileId", fileId);

        log.info("클라이언트 WebSocket 연결됨: {}, fileId={}", session.getId(), fileId);

        // 세션+fileId 기반으로 STT 스트리밍 시작
        googleSpeechService.startStreaming(session, fileId);
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
}
