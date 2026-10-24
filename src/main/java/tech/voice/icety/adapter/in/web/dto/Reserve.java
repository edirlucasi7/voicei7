package tech.voice.icety.adapter.in.web.dto;

import java.time.LocalDateTime;

public record Reserve(String callId, LocalDateTime dateTime) { }
