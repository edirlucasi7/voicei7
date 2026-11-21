package tech.voice.icety.application.service;

import tech.voice.icety.application.port.in.AttendCallUseCase;
import tech.voice.icety.application.port.out.ConversationAssistant;
import tech.voice.icety.domain.model.BookingException;
import tech.voice.icety.domain.model.CallSession;

public class AttendCallService implements AttendCallUseCase {
    private final ConversationAssistant assistant;

    public AttendCallService(ConversationAssistant assistant) {
        this.assistant = assistant;
    }

    @Override
    public void playbackInterrupted(String callId) {
        CallSession.validateId(callId);
        assistant.playbackInterrupted(callId);
    }

    @Override
    public String reply(String callId, String text) {
        CallSession.validateId(callId);
        if (text == null || text.isBlank()) {
            throw BookingException.invalid("Message text must not be blank");
        }

        return assistant.reply(callId, text);
    }
}
