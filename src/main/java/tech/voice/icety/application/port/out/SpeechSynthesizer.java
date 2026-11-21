package tech.voice.icety.application.port.out;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface SpeechSynthesizer {
    void speak(String text, Consumer<byte[]> audio, BooleanSupplier cancelled);
}
