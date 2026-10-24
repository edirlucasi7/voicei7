package tech.voice.icety.adapter.in.web.dto;

import java.time.Instant;

public record CallResponse(String callId, String status, Instant lastActivityAt, Instant expiresAt,
                               long heartbeatIntervalSeconds) { }
