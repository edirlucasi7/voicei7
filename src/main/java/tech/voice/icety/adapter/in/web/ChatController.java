package tech.voice.icety.adapter.in.web;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import tech.voice.icety.adapter.in.web.dto.ChatMessageRequest;
import tech.voice.icety.adapter.in.web.dto.ChatMessageResponse;
import tech.voice.icety.application.port.in.AttendCallUseCase;

@RestController
public class ChatController {
    private final AttendCallUseCase attendance;

    public ChatController(AttendCallUseCase attendance) {
        this.attendance = attendance;
    }

    @PostMapping("/api/calls/{callId}/messages")
    public ChatMessageResponse message(@PathVariable String callId, @RequestBody ChatMessageRequest request) {
        return new ChatMessageResponse(attendance.reply(callId, request.text()));
    }
}
