package tech.voice.icety.application.port.out;

public interface ConversationAssistant {
    String reply(String callId, String text);

    default void playbackInterrupted(String callId) { }
}
