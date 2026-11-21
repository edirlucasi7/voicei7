package tech.voice.icety.application.port.in.result;

public record VoiceEvent(String type, String turnId, String text, String code, String audio) {
    public static VoiceEvent text(String type, String turnId, String text) {
        return new VoiceEvent(type, turnId, text, null, null);
    }

    public static VoiceEvent error(String turnId, String code, String text) {
        return new VoiceEvent("error", turnId, text, code, null);
    }
}
