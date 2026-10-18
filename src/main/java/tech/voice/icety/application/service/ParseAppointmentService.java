package tech.voice.icety.application.service;

import tech.voice.icety.application.port.in.ParseAppointmentUseCase;
import tech.voice.icety.application.port.out.AppointmentParser;
import tech.voice.icety.domain.model.AppointmentRequest;
import tech.voice.icety.domain.model.UserIntent;

public class ParseAppointmentService implements ParseAppointmentUseCase {

    private final AppointmentParser parser;

    public ParseAppointmentService(AppointmentParser parser) {
        this.parser = parser;
    }

    @Override
    public AppointmentRequest parse(String userText) {
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("User text must not be blank");
        }
        AppointmentRequest result = parser.parse(userText);
        if (result.specificTime() != null
                || (result.intent() != UserIntent.BOOK
                && result.intent() != UserIntent.CHECK_AVAILABILITY)) {
            return result;
        }
        String explicitTime = ExplicitTimeExtractor.extract(userText);
        if (explicitTime == null) {
            return result;
        }
        return new AppointmentRequest(result.customerName(), result.requestedDate(), explicitTime,
                result.period(), result.intent());
    }
}
