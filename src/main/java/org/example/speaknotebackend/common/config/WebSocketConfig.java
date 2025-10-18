package org.example.speaknotebackend.common.config;

import org.example.speaknotebackend.domain.websocket.AudioWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AudioWebSocketHandler audioWebSocketHandler;

    @Value("${custom.cors.allowed-origin}")
    private String allowedOrigin;

    public WebSocketConfig(
            AudioWebSocketHandler audioWebSocketHandler) {
        this.audioWebSocketHandler = audioWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry.addHandler(audioWebSocketHandler, "/ws/audio")
                .setAllowedOrigins(allowedOrigin);
    }
}
