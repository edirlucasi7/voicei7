package tech.voice.icety.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlannedAppointmentAction(AppointmentAction action,
        @Schema(nullable = true) @JsonPropertyDescription("Data e hora local ISO 8601 extraídas do pedido; null se ausentes")
        String dateTime,
        @Schema(nullable = true) @JsonPropertyDescription("Nome realmente informado pelo cliente; null se ausente")
        String customerName,
        @JsonPropertyDescription("True apenas para confirmação explícita da reserva atual; um pedido inicial de agendamento é false")
        boolean explicitlyConfirmed) { }
