package tech.voice.icety.application.port.out;

public interface SpeechTranscriber {
    String transcribe(byte[] wav);
}
