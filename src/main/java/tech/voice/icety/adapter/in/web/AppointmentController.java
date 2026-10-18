package tech.voice.icety.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.voice.icety.application.port.in.ParseAppointmentUseCase;
import tech.voice.icety.domain.model.AppointmentRequest;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final ParseAppointmentUseCase parseAppointment;

    public AppointmentController(ParseAppointmentUseCase parseAppointment) {
        this.parseAppointment = parseAppointment;
    }

    @PostMapping("/parse")
    public ResponseEntity<AppointmentRequest> parse(@RequestBody ParseAppointmentRequest request) {
        if (request.text() == null || request.text().isBlank()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(parseAppointment.parse(request.text()));
    }

    public record ParseAppointmentRequest(String text) { }
}
