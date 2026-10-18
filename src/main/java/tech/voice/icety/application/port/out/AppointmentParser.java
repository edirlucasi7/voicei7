package tech.voice.icety.application.port.out;

import tech.voice.icety.domain.model.AppointmentRequest;

public interface AppointmentParser {
    AppointmentRequest parse(String userText);
}
