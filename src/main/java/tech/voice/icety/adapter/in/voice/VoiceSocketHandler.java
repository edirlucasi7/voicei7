package tech.voice.icety.adapter.in.voice;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.in.result.VoiceEvent;
import tech.voice.icety.application.service.VoiceTurnService;
import tech.voice.icety.domain.model.BookingException;
import tools.jackson.databind.json.JsonMapper;

public class VoiceSocketHandler extends AbstractWebSocketHandler {
    private final ConcurrentHashMap<String, VoiceConnection> connections = new ConcurrentHashMap<>();
    private final Set<String> claimedCalls = ConcurrentHashMap.newKeySet();
    private final VoiceTurnService turns;
    private final ManageAppointmentsUseCase appointments;
    private final JsonMapper json = JsonMapper.builder().build();

    public VoiceSocketHandler(VoiceTurnService turns, ManageAppointmentsUseCase appointments) {
        this.turns = turns;
        this.appointments = appointments;
    }

    boolean claim(String callId) {
        return claimedCalls.add(callId);
    }

    void release(String callId) {
        claimedCalls.remove(callId);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) {
        var callId = (String) socket.getAttributes().get("callId");
        socket.setTextMessageSizeLimit(4096);
        socket.setBinaryMessageSizeLimit(9600);
        var connection = new VoiceConnection(callId, socket, turns);
        connections.put(socket.getId(), connection);
        try {
            turns.requireActive(callId);
            connection.send(VoiceEvent.text("ready", null, null));
        } catch (BookingException exception) {
            connection.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        var connection = connections.get(socket.getId());
        if (connection == null) {
            return;
        }

        VoiceInput input = null;
        try {
            input = json.readValue(message.getPayload(), VoiceInput.class);
            connection.input(input);
        } catch (BookingException exception) {
            connection.send(VoiceEvent.error(input == null ? null : input.turnId(), exception.kind().name(), exception.getMessage()));
            connection.close(CloseStatus.POLICY_VIOLATION);
        } catch (RuntimeException exception) {
            connection.send(VoiceEvent.error(input == null ? null : input.turnId(), "INVALID", "Evento, formato ou sequência de voz inválidos."));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession socket, BinaryMessage message) {
        var connection = connections.get(socket.getId());
        if (connection == null) {
            return;
        }

        var bytes = new byte[message.getPayloadLength()];
        message.getPayload().get(bytes);
        try {
            connection.audio(bytes);
        } catch (IllegalArgumentException exception) {
            connection.send(VoiceEvent.error(null, "INVALID", exception.getMessage()));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession socket, Throwable error) {
        var connection = connections.get(socket.getId());
        if (connection != null) {
            connection.close(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        var connection = connections.remove(socket.getId());
        if (connection == null) {
            return;
        }

        connection.close(status);
        var callId = (String) socket.getAttributes().get("callId");
        try {
            appointments.endCall(callId);
        } finally {
            release(callId);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void expireConnections() {
        connections.values().forEach(connection -> {
            try {
                connection.checkActive();
            } catch (RuntimeException exception) {
                connection.close(CloseStatus.POLICY_VIOLATION);
            }
        });
    }

    @PreDestroy
    public void stop() {
        connections.values().forEach(connection -> connection.close(CloseStatus.GOING_AWAY));
    }
}
