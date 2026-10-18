package tech.voice.icety.application.port.in;

import tech.voice.icety.domain.model.AppointmentRequest;

public interface ParseAppointmentUseCase {
    AppointmentRequest parse(String userText);
}
