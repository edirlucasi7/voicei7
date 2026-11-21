package tech.voice.icety.application.port.in;

public interface AttendCallUseCase {
    String reply(String callId, String text);

    default void playbackInterrupted(String callId) { }
}
