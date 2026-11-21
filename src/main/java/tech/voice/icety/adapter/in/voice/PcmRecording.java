package tech.voice.icety.adapter.in.voice;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class PcmRecording {
    static final int SAMPLE_RATE = 24_000;
    static final int MAX_BYTES = SAMPLE_RATE * 2 * 30;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final long started = System.nanoTime();

    void append(byte[] bytes) {
        if (bytes.length % 2 != 0 || pcm.size() + bytes.length > MAX_BYTES || expired()) {
            throw new IllegalArgumentException("Áudio inválido ou gravação acima de 30 segundos.");
        }

        pcm.writeBytes(bytes);
    }

    boolean expired() {
        return System.nanoTime() - started > java.time.Duration.ofSeconds(31).toNanos();
    }

    byte[] wav() {
        if (pcm.size() < 2400 || expired()) {
            throw new IllegalArgumentException("Grave entre 50 milissegundos e 30 segundos antes de enviar.");
        }

        var wav = ByteBuffer.allocate(44 + pcm.size()).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.size());
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        wav.putShort((short) 1).putShort((short) 1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2);
        wav.putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.size()).put(pcm.toByteArray());
        return wav.array();
    }
}
