package tech.voice.icety.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import tech.voice.icety.domain.model.AppointmentRequest;
import tech.voice.icety.domain.model.DayPeriod;
import tech.voice.icety.domain.model.UserIntent;

record ExtractedAppointment(
        @JsonPropertyDescription("Nome do cliente, caso informado na conversa; null se ausente")
        @Schema(nullable = true)
        String customerName,
        @JsonPropertyDescription("Data da consulta calculada em YYYY-MM-DD; null se ausente")
        @Schema(nullable = true)
        LocalDate requestedDate,
        @JsonPropertyDescription("Horário específico no formato HH:mm; null se apenas um período foi informado")
        @Schema(nullable = true)
        String specificTime,
        @JsonPropertyDescription("Período do dia: MORNING, AFTERNOON, EVENING ou UNSPECIFIED")
        DayPeriod period,
        @JsonPropertyDescription("Intenção do usuário: BOOK, CHECK_AVAILABILITY, CANCEL ou OTHER")
        UserIntent intent) {

    AppointmentRequest toDomain() {
        return new AppointmentRequest(customerName, requestedDate, specificTime, period, intent);
    }
}
