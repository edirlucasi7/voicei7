package tech.voice.icety.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.voice.icety.adapter.in.web.dto.StartCall;
import tech.voice.icety.adapter.in.web.dto.CallResponse;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.domain.model.CallSession;

@RestController
@RequestMapping("/api/calls")
public class CallController {
    private final ManageAppointmentsUseCase appointments;

    public CallController(ManageAppointmentsUseCase appointments) {
        this.appointments = appointments;
    }

    @PostMapping
    public ResponseEntity<CallResponse> start(@RequestBody StartCall request) {
        var result = appointments.startCall(request.callId());
        return ResponseEntity.status(result.created() ? 201 : 200).body(response(result.value()));
    }

    @PostMapping("/{callId}/heartbeat")
    public CallResponse heartbeat(@PathVariable String callId) {
        return response(appointments.heartbeat(callId));
    }

    @PostMapping("/{callId}/end")
    public CallResponse end(@PathVariable String callId) {
        return response(appointments.endCall(callId));
    }

    private CallResponse response(CallSession call) {
        return new CallResponse(call.id(), call.status().name(), call.lastActivityAt(), call.expiresAt(),
                appointments.heartbeatInterval().toSeconds());
    }
}
