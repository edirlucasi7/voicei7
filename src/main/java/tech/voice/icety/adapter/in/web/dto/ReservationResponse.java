package tech.voice.icety.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(UUID id, String callId, LocalDateTime dateTime, String customerName,
                                      String status, Instant expiresAt, Instant createdAt, Instant updatedAt) { }
