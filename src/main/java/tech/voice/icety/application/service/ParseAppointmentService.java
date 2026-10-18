package tech.voice.icety.application.service;

import tech.voice.icety.application.port.in.ParseAppointmentUseCase;
import tech.voice.icety.application.port.out.AppointmentParser;
import tech.voice.icety.domain.model.AppointmentRequest;

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

        return parser.parse(userText);
    }
}
