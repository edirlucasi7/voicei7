package tech.voice.icety.adapter.out.ai;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.domain.model.BookingException;

@Component
public class AppointmentTools {
    private final ManageAppointmentsUseCase appointments;
    private final CallSessionRepository calls;
    private final Clock clock;

    public AppointmentTools(ManageAppointmentsUseCase appointments, CallSessionRepository calls, Clock clock) {
        this.appointments = appointments;
        this.calls = calls;
        this.clock = clock;
    }

    @Tool(description = "Consulta disponibilidade real para uma data/hora específica, sem reservar. Use data/hora local ISO 8601 sem fuso, como 2026-10-20T10:00:00.")
    public ToolResult checkAvailability(
            @ToolParam(description = "Data/hora em America/Fortaleza, exemplo: 2026-10-20T14:30:00")
            String dateTime, ToolContext context) {
        return execute(context, "checkAvailability", () -> appointments.available(parseTime(dateTime)));
    }

    @Tool(returnDirect = true, description = "Reserva temporariamente o horário solicitado pelo cliente. Não confirma o agendamento. Use data/hora local ISO 8601 sem fuso, como 2026-10-20T10:00:00.")
    public ToolResult reserveAppointment(
            @ToolParam(description = "Data/hora em America/Fortaleza, exemplo: 2026-10-20T14:30:00")
            String dateTime, ToolContext context) {
        return execute(context, "reserveAppointment", () -> appointments.reserve(callId(context), parseTime(dateTime)).value());
    }

    @Tool(returnDirect = true, description = "Libera uma reserva temporária desta ligação antes de escolher outro horário. Não cancela agendamento confirmado.")
    public ToolResult releaseReservation(@ToolParam(required = false) String reservationId, ToolContext context) {
        return execute(context, "releaseReservation", () -> appointments.release(reservationId(reservationId, context), callId(context)));
    }

    @Tool(returnDirect = true, description = "Confirma a reserva temporária atual após o cliente confirmar explicitamente o horário e informar o nome. reservationId pode ser omitido: o servidor usa a reserva desta ligação. Nunca use para mera consulta ou criação de reserva.")
    public ToolResult confirmAppointment(@ToolParam(required = false) String reservationId, String customerName,
            @ToolParam(description = "True somente se o cliente confirmou explicitamente o agendamento")
            boolean explicitlyConfirmed, ToolContext context) {
        return execute(context, "confirmAppointment", () -> {
            if (!explicitlyConfirmed) {
                throw BookingException.invalid("Explicit customer confirmation is required");
            }

            var id = reservationId(reservationId, context);
            if (context.getContext().containsKey("pendingReservationId")
                    && !id.toString().equals(context.getContext().get("pendingReservationId"))) {
                throw BookingException.invalid("A reservation must be presented before confirmation in a later message");
            }

            return appointments.confirm(id, callId(context), customerName);
        });
    }

    private ToolResult execute(ToolContext context, String operation, Supplier<Object> action) {
        var log = context.getContext().get("appointmentToolLog") instanceof AppointmentToolLog existing
                ? existing : new AppointmentToolLog();
        try {
            var call = calls.findById(callId(context)).orElseThrow(() ->
                    new BookingException(BookingException.Kind.NOT_FOUND, "Call not found"));
            call.requireActive(clock.instant());
            return log.record(operation, new ToolResult(true, "OK", action.get()));
        } catch (BookingException exception) {
            return log.record(operation, new ToolResult(false, exception.kind().name(), exception.getMessage()));
        } catch (IllegalArgumentException | DateTimeException exception) {
            return log.record(operation, new ToolResult(false, "INVALID", "Invalid tool arguments; ask the customer to clarify"));
        } catch (PessimisticLockingFailureException exception) {
            return log.record(operation, new ToolResult(false, "BUSY", "Schedule is busy; retry shortly"));
        } catch (DataIntegrityViolationException exception) {
            return log.record(operation, new ToolResult(false, "CONFLICT", "Appointment time or call already has a reservation"));
        }
    }

    private UUID reservationId(String requested, ToolContext context) {
        var id = requested == null || requested.isBlank()
                ? (String) context.getContext().get("pendingReservationId") : requested;
        if (id == null || id.isBlank()) {
            throw BookingException.invalid("No current reservation to confirm or release");
        }

        return UUID.fromString(id);
    }

    private LocalDateTime parseTime(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            throw BookingException.invalid("dateTime is required, for example 2026-10-20T14:30:00");
        }

        return LocalDateTime.parse(dateTime);
    }

    private String callId(ToolContext context) {
        return (String) context.getContext().get("callId");
    }
}
