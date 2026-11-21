package tech.voice.icety.adapter.out.audio;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PcmAudioSubscriber implements HttpResponse.BodySubscriber<Void> {
    private final CompletableFuture<Void> done = new CompletableFuture<>();
    private final Consumer<byte[]> audio;
    private final BooleanSupplier cancelled;
    private volatile Flow.Subscription subscription;
    private int trailing = -1;
    private int total;

    PcmAudioSubscriber(Consumer<byte[]> audio, BooleanSupplier cancelled) {
        this.audio = audio;
        this.cancelled = cancelled;
    }

    @Override
    public CompletionStage<Void> getBody() {
        return done;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (done.isDone() || cancelled.getAsBoolean()) {
            cancel();
        } else {
            subscription.request(1);
        }
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        try {
            if (cancelled.getAsBoolean() || done.isDone()) {
                cancel();
                return;
            }

            for (var buffer : buffers) {
                while (buffer.hasRemaining()) {
                    int count = Math.min(buffer.remaining() + (trailing < 0 ? 0 : 1), 9600);
                    var bytes = new byte[count];
                    int offset = 0;
                    if (trailing >= 0) {
                        bytes[offset++] = (byte) trailing;
                        trailing = -1;
                    }

                    int copied = Math.min(buffer.remaining(), count - offset);
                    buffer.get(bytes, offset, copied);
                    if (bytes.length % 2 != 0) {
                        trailing = Byte.toUnsignedInt(bytes[bytes.length - 1]);
                        bytes = java.util.Arrays.copyOf(bytes, bytes.length - 1);
                    }

                    total += bytes.length;
                    if (total > 2_880_000) {
                        throw new IllegalStateException("Synthesized audio exceeds 60 seconds");
                    }

                    if (bytes.length > 0 && !cancelled.getAsBoolean()) {
                        audio.accept(bytes);
                    }
                }
            }

            subscription.request(1);
        } catch (RuntimeException exception) {
            fail(exception);
            cancel();
        }
    }

    @Override
    public void onError(Throwable error) {
        fail(error);
    }

    @Override
    public void onComplete() {
        if (trailing >= 0 || total == 0) {
            fail(new IllegalStateException("Invalid or empty PCM audio"));
        } else {
            done.complete(null);
        }
    }

    void fail(Throwable error) {
        done.completeExceptionally(error);
    }

    void cancel() {
        var active = subscription;
        if (active != null) {
            active.cancel();
        }
    }
}
