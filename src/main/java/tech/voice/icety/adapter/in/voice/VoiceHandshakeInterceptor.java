package tech.voice.icety.adapter.in.voice;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import tech.voice.icety.application.service.VoiceTurnService;
import tech.voice.icety.domain.model.BookingException;
import tech.voice.icety.domain.model.CallSession;

public class VoiceHandshakeInterceptor implements HandshakeInterceptor {
    private final VoiceSocketHandler sockets;
    private final VoiceTurnService turns;

    public VoiceHandshakeInterceptor(VoiceSocketHandler sockets, VoiceTurnService turns) {
        this.sockets = sockets;
        this.turns = turns;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        var segments = UriComponentsBuilder.fromUri(request.getURI()).build().getPathSegments();
        var callId = org.springframework.web.util.UriUtils.decode(segments.get(2), java.nio.charset.StandardCharsets.UTF_8);
        try {
            CallSession.validateId(callId);
            turns.requireActive(callId);
            if (!sockets.claim(callId)) {
                response.setStatusCode(HttpStatus.CONFLICT);
                return false;
            }

            attributes.put("callId", callId);
            return true;
        } catch (BookingException exception) {
            response.setStatusCode(exception.kind() == BookingException.Kind.NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {
        if (exception != null || response.getHeaders().getUpgrade() == null) {
            var segments = UriComponentsBuilder.fromUri(request.getURI()).build().getPathSegments();
            sockets.release(org.springframework.web.util.UriUtils.decode(segments.get(2), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
