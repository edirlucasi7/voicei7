package tech.voice.icety;

import java.time.Clock;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import tech.voice.icety.adapter.out.ai.AppointmentTools;
import tech.voice.icety.adapter.out.ai.OllamaConversationAssistant;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.in.ManageKnowledgeUseCase;
import tech.voice.icety.application.port.out.CallSessionRepository;
import tech.voice.icety.domain.model.CallSession;
import tech.voice.icety.domain.model.BusinessSchedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationConcurrencyTest {
    @Test
    void serializesOneCallWhileAnotherCallWithSameHashCanProgress() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneId.of("America/Fortaleza"));
        var calls = mock(CallSessionRepository.class);
        when(calls.findById(anyString())).thenAnswer(invocation -> Optional.of(
                CallSession.start(invocation.getArgument(0), clock.instant(), Duration.ofMinutes(2))));
        var knowledge = mock(ManageKnowledgeUseCase.class);
        when(knowledge.search(anyString())).thenReturn(List.of());
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OllamaChatOptions.builder().model("mock").build());
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var sameCallRequested = new CountDownLatch(1);
        var sameCallEnteredModel = new CountDownLatch(1);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            var text = prompt.getUserMessage().getText();
            if (prompt.getSystemMessage().getText().contains("Classifique o pedido atual")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"action\":\"NONE\",\"explicitlyConfirmed\":false}"))));
            }

            if (text.equals("first")) {
                firstEntered.countDown();
                assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
            } else if (text.equals("same")) {
                sameCallEnteredModel.countDown();
            }

            return new ChatResponse(List.of(new Generation(new AssistantMessage("reply " + text))));
        });
        var tools = new AppointmentTools(mock(ManageAppointmentsUseCase.class), calls, clock);
        var assistant = new OllamaConversationAssistant(ChatClient.builder(model), tools, knowledge, calls, clock,
                new BusinessSchedule(Set.of(DayOfWeek.SUNDAY)), mock(tech.voice.icety.application.port.out.AppointmentRepository.class));
        // These distinct ids have the same String hash code.
        assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());
        try (var executor = Executors.newFixedThreadPool(3)) {
            var first = executor.submit(() -> assistant.reply("Aa", "first"));
            try {
                if (!firstEntered.await(5, TimeUnit.SECONDS)) {
                    first.get(1, TimeUnit.SECONDS);
                    throw new AssertionError("First request did not reach the model");
                }
                var same = executor.submit(() -> {
                    sameCallRequested.countDown();
                    return assistant.reply("Aa", "same");
                });
                assertThat(sameCallRequested.await(5, TimeUnit.SECONDS)).isTrue();
                var other = executor.submit(() -> assistant.reply("BB", "other"));
                assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo("reply other");
                assertThat(sameCallEnteredModel.getCount()).isEqualTo(1);
                releaseFirst.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("reply first");
                assertThat(same.get(5, TimeUnit.SECONDS)).isEqualTo("reply same");
            } finally {
                releaseFirst.countDown();
            }
        }
    }
}
