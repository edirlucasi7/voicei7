package tech.voice.icety.adapter.in.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;

@RestController
@RequestMapping("/api/appointments")
public class ReservationController {
    private final ManageAppointmentsUseCase appointments;

    public ReservationController(ManageAppointmentsUseCase appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/availability")
    public Availability availability(@RequestParam LocalDateTime dateTime) {
        return new Availability(appointments.available(dateTime));
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserve(@RequestBody Reserve request) {
        var result = appointments.reserve(request.callId(), request.dateTime());
        return ResponseEntity.status(result.created() ? 201 : 200).body(response(result.value()));
    }

    @PostMapping("/{id}/confirm")
    public ReservationResponse confirm(@PathVariable UUID id, @RequestBody Confirm request) {
        return response(appointments.confirm(id, request.callId(), request.customerName()));
    }

    @PostMapping("/{id}/release")
    public ReservationResponse release(@PathVariable UUID id, @RequestBody Release request) {
        return response(appointments.release(id, request.callId()));
    }

    private ReservationResponse response(ManageAppointmentsUseCase.Reservation reservation) {
        var a = reservation.appointment();
        return new ReservationResponse(a.id(), a.callId(), a.dateTime(), a.customerName(),
                a.status().name(), reservation.expiresAt(), a.createdAt(), a.updatedAt());
    }

    public record Reserve(String callId, LocalDateTime dateTime) { }
    public record Confirm(String callId, String customerName) { }
    public record Release(String callId) { }
    public record Availability(boolean available) { }
    public record ReservationResponse(UUID id, String callId, LocalDateTime dateTime, String customerName,
                                      String status, Instant expiresAt, Instant createdAt, Instant updatedAt) { }
}
