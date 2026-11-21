package tech.voice.icety.adapter.in.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tech.voice.icety.application.service.VoiceTurnService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceConnectionTest {
    @Test
    void wavHasCorrectFormatAndEnforcesRecordingBounds() {
        var recording = new PcmRecording();
        assertThatThrownBy(recording::wav).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recording.append(new byte[3])).isInstanceOf(IllegalArgumentException.class);
        recording.append(new byte[PcmRecording.MAX_BYTES]);
        var wav = ByteBuffer.wrap(recording.wav()).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(wav.getInt(24)).isEqualTo(24000);
        assertThat(wav.getShort(22)).isEqualTo((short) 1);
        assertThat(wav.getShort(34)).isEqualTo((short) 16);
        assertThat(wav.getInt(40)).isEqualTo(PcmRecording.MAX_BYTES);
        assertThatThrownBy(() -> recording.append(new byte[2])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializesTurnsBoundsQueueAndDoesNotInterruptAnAttendanceInFlight() throws Exception {
        var socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        var turns = mock(VoiceTurnService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(2);
        var firstObsolete = new AtomicBoolean();
        var interrupted = new AtomicBoolean();
        var first = UUID.randomUUID().toString();
        doAnswer(invocation -> {
            if (first.equals(invocation.getArgument(1))) {
                entered.countDown();
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    interrupted.set(true);
                    throw exception;
                }

                BooleanSupplier obsolete = invocation.getArgument(4);
                firstObsolete.set(obsolete.getAsBoolean());
            }

            completed.countDown();
            return null;
        }).when(turns).reply(anyString(), anyString(), any(), any(), any());
        var connection = new VoiceConnection("a", socket, turns);
        try {
            assertThatThrownBy(() -> connection.audio(new byte[2400])).isInstanceOf(IllegalArgumentException.class);
            connection.input(start(first));
            connection.audio(new byte[2400]);
            connection.input(commit(first));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = UUID.randomUUID().toString();
            connection.input(start(second));
            connection.audio(new byte[2400]);
            connection.input(commit(second));
            assertThatThrownBy(() -> connection.input(start(UUID.randomUUID().toString())))
                    .isInstanceOf(IllegalArgumentException.class);
            release.countDown();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(firstObsolete).isTrue();
            assertThat(interrupted).isFalse();
            verify(turns, times(2)).reply(anyString(), anyString(), any(), any(), any());
            verify(turns).playbackInterrupted("a");
            assertThatThrownBy(() -> connection.input(start(first))).isInstanceOf(IllegalArgumentException.class);
        } finally {
            release.countDown();
            connection.close(CloseStatus.NORMAL);
        }
    }

    private VoiceInput start(String id) {
        return new VoiceInput("input.start", id, "pcm16", 24000, 1, false);
    }

    private VoiceInput commit(String id) {
        return new VoiceInput("input.commit", id, null, null, null, null);
    }
}
