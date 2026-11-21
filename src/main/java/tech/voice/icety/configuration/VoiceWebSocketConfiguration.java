package tech.voice.icety.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;
import tech.voice.icety.adapter.in.voice.VoiceHandshakeInterceptor;
import tech.voice.icety.adapter.in.voice.VoiceSocketHandler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "voice.enabled", havingValue = "true")
@EnableWebSocket
public class VoiceWebSocketConfiguration implements WebSocketConfigurer {
    private final VoiceSocketHandler handler;
    private final VoiceHandshakeInterceptor handshake;

    public VoiceWebSocketConfiguration(VoiceSocketHandler handler, VoiceHandshakeInterceptor handshake) {
        this.handler = handler;
        this.handshake = handshake;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/calls/*/voice")
                .addInterceptors(new OriginHandshakeInterceptor(), handshake);
    }
}
