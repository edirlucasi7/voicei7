package tech.voice.icety.adapter.out.ai;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import tech.voice.icety.application.port.in.result.Reservation;
import tech.voice.icety.domain.model.Appointment;

final class AppointmentToolLog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private final List<AppointmentToolExecution> executions = new ArrayList<>();

    synchronized ToolResult record(String operation, ToolResult result) {
        executions.add(new AppointmentToolExecution(operation, result));
        return result;
    }

    synchronized boolean hasMutationAttempt() {
        return executions.stream().anyMatch(execution -> !execution.operation().equals("checkAvailability"));
    }

    synchronized String customerReply() {
        if (executions.isEmpty()) {
            return null;
        }

        var last = executions.getLast();
        if (!last.result().success()) {
            var previous = executions.stream().filter(execution -> execution.result().success()
                    && execution.result().result() instanceof Reservation).reduce((a, b) -> b);
            return previous.map(execution -> describe((Reservation) execution.result().result()) + " ").orElse("")
                    + failure(last);
        }

        if (last.result().result() instanceof Reservation reservation) {
            return describe(reservation);
        }

        if (last.result().result() instanceof Boolean available) {
            return available ? "Esse horário está disponível. Gostaria de reservá-lo?"
                    : "Esse horário não está disponível. Qual outra data e horário você prefere?";
        }

        return null;
    }

    static String describe(Reservation reservation) {
        var appointment = reservation.appointment();
        return switch (appointment.status()) {
            case TEMPORARY -> pending(appointment);
            case CONFIRMED -> "Agendamento confirmado para " + TIME.format(appointment.dateTime()) + ", "
                    + appointment.customerName() + ".";
            case RELEASED -> "Sua reserva temporária foi cancelada.";
            case EXPIRED -> "Sua reserva temporária expirou. Precisamos consultar o horário novamente.";
        };
    }

    static String pending(Appointment appointment) {
        return "O horário de " + TIME.format(appointment.dateTime())
                + " está reservado temporariamente. O agendamento ainda não está confirmado. "
                + "Informe seu nome e confirme se deseja esse horário.";
    }

    private String failure(AppointmentToolExecution execution) {
        return switch (execution.result().code()) {
            case "BUSY" -> "A agenda está ocupada no momento. Tente novamente em instantes.";
            case "NOT_FOUND" -> "Não encontrei essa reserva. Precisamos consultar o horário novamente.";
            default -> switch (execution.operation()) {
                case "confirmAppointment" -> "Não consegui confirmar o agendamento. Verifique se a reserva ainda está ativa e informe seu nome e a confirmação do horário.";
                case "reserveAppointment" -> "Não consegui reservar esse horário. Ele pode estar indisponível ou fora dos dias de atendimento. Escolha outra data e horário.";
                case "releaseReservation" -> "Não consegui liberar essa reserva. Agendamentos confirmados não podem ser cancelados por este atendimento.";
                default -> "Não consegui consultar esse horário. Informe a data e a hora novamente.";
            };
        };
    }
}
