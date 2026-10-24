package tech.voice.icety.application.port.in.result;

import java.time.Instant;
import tech.voice.icety.domain.model.Appointment;

public record Reservation(Appointment appointment, Instant expiresAt) { }
