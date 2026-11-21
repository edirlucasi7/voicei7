package tech.voice.icety.adapter.in.voice;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tech.voice.icety.application.port.in.result.VoiceEvent;
import tech.voice.icety.application.service.VoiceTurnService;
import tools.jackson.databind.json.JsonMapper;

final class VoiceConnection {
    private final String callId;
    private final WebSocketSession socket;
    private final VoiceTurnService turns;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final Set<String> usedIds = new HashSet<>();
    private volatile boolean closed;
    private volatile String latestId;
    private volatile long processingStarted;
    private PcmRecording recording;
    private RecordedTurn pending;
    private boolean busy;

    VoiceConnection(String callId, WebSocketSession socket, VoiceTurnService turns) {
        this.callId = callId;
        this.socket = new ConcurrentWebSocketSessionDecorator(socket, 5000, 262_144);
        this.turns = turns;
    }

    synchronized void input(VoiceInput input) {
        turns.requireActive(callId);
        if (closed) {
            throw new IllegalArgumentException("A sessão de voz foi encerrada.");
        }

        switch (input.type() == null ? "" : input.type()) {
            case "input.start" -> start(input);
            case "input.commit" -> commit(input.turnId());
            default -> throw new IllegalArgumentException("Evento de voz desconhecido.");
        }
    }

    private void start(VoiceInput input) {
        if (recording != null || pending != null) {
            throw new IllegalArgumentException("Aguarde a fala pendente antes de gravar novamente.");
        }

        if (!"pcm16".equals(input.format()) || !Integer.valueOf(24000).equals(input.sampleRate())
                || !Integer.valueOf(1).equals(input.channels())) {
            throw new IllegalArgumentException("Use PCM16 mono em 24000 Hz.");
        }

        var id = UUID.fromString(input.turnId()).toString();
        if (!id.equals(input.turnId()) || usedIds.size() >= 1000 || !usedIds.add(id)) {
            throw new IllegalArgumentException("Identificador de turno inválido ou repetido.");
        }

        if (latestId != null && (busy || Boolean.TRUE.equals(input.playbackInterrupted()))) {
            turns.playbackInterrupted(callId);
        }

        latestId = id;
        recording = new PcmRecording();
        send(VoiceEvent.text("input.started", id, null));
    }

    synchronized void audio(byte[] pcm) {
        if (closed || recording == null) {
            throw new IllegalArgumentException("Inicie a gravação antes de enviar áudio.");
        }

        try {
            recording.append(pcm);
        } catch (IllegalArgumentException exception) {
            recording = null;
            throw exception;
        }
    }

    private void commit(String id) {
        if (recording == null || !latestId.equals(id)) {
            throw new IllegalArgumentException("Gravação inexistente ou turno incorreto.");
        }

        var captured = recording;
        recording = null;
        pending = new RecordedTurn(id, captured.wav());
        send(VoiceEvent.text("state", id, "queued"));
        if (!busy) {
            busy = true;
            worker.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            RecordedTurn turn;
            synchronized (this) {
                turn = pending;
                pending = null;
                if (closed || turn == null) {
                    busy = false;
                    processingStarted = 0;
                    return;
                }

                processingStarted = System.nanoTime();
            }

            try {
                turns.reply(callId, turn.id(), turn.wav(), this::send,
                        () -> closed || !turn.id().equals(latestId));
            } catch (RuntimeException exception) {
                close(CloseStatus.SERVER_ERROR);
            }
        }
    }

    synchronized void checkActive() {
        turns.requireActive(callId);
        if (recording != null && recording.expired()) {
            recording = null;
            send(VoiceEvent.error(latestId, "INVALID", "A gravação excedeu 30 segundos."));
        }

        if (processingStarted != 0 && System.nanoTime() - processingStarted > Duration.ofMinutes(2).toNanos()) {
            send(VoiceEvent.error(latestId, "TIMEOUT", "O processamento excedeu dois minutos; confira a agenda antes de repetir."));
            close(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    void send(VoiceEvent event) {
        if (closed || !socket.isOpen()) {
            return;
        }

        try {
            socket.sendMessage(new TextMessage(json.writeValueAsString(event)));
        } catch (IOException | RuntimeException exception) {
            close(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    synchronized void close(CloseStatus status) {
        if (closed) {
            return;
        }

        closed = true;
        recording = null;
        pending = null;
        worker.shutdown(); // Do not interrupt a tool transaction already running in the attendance service.
        try {
            socket.close(status);
        } catch (IOException exception) {
            // Transport is already gone; afterConnectionClosed releases the call.
        }
    }
}
