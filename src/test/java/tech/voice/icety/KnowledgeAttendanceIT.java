package tech.voice.icety;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tech.voice.icety.adapter.out.ai.PgVectorKnowledgeIndex;
import tech.voice.icety.adapter.out.persistence.repository.KnowledgeSourceJpaRepository;
import tech.voice.icety.adapter.out.persistence.repository.KnowledgeCatalogJpaRepository;
import tech.voice.icety.application.port.in.AttendCallUseCase;
import tech.voice.icety.application.port.in.ManageAppointmentsUseCase;
import tech.voice.icety.application.port.out.AppointmentRepository;
import tech.voice.icety.domain.model.Appointment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(AppointmentTestConfiguration.class)
class KnowledgeAttendanceIT extends PostgresTestSupport {
    @Autowired VectorStore vectors;
    @Autowired KnowledgeSourceJpaRepository sources;
    @Autowired KnowledgeCatalogJpaRepository catalog;
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate jdbc;
    @Autowired AttendCallUseCase attendance;
    @Autowired ManageAppointmentsUseCase appointments;
    @Autowired AppointmentRepository repository;
    @TempDir Path directory;

    @BeforeEach
    void clearKnowledge() {
        jdbc.update("DELETE FROM vector_store");
        jdbc.update("DELETE FROM knowledge_source");
    }

    @Test
    void retrievesPortugueseKnowledgeAndAnswersUsingRealOllama() throws Exception {
        Files.writeString(directory.resolve("faq.txt"),
                "A Clínica Exemplo fica na Rua das Flores, 100, Fortaleza. Para a consulta leve documento de identificação.");
        var index = new PgVectorKnowledgeIndex(vectors, sources, catalog, transaction,
                directory.toString(), 4, false, 0.5);
        assertThat(index.ingest().updated()).isEqualTo(1);
        assertThat(index.search("Qual é o endereço da clínica?")).anySatisfy(passage ->
                assertThat(passage.text()).contains("Rua das Flores"));
        String callId = "real-rag-" + UUID.randomUUID();
        appointments.startCall(callId);
        try {
            assertThat(attendance.reply(callId, "Qual é o endereço da clínica?"))
                    .containsIgnoringCase("Rua das Flores");
        } finally {
            appointments.endCall(callId);
        }
    }

    @Test
    void reservesThenConfirmsUsingRealOllamaTools() {
        String callId = "real-tools-" + UUID.randomUUID();
        appointments.startCall(callId);
        try {
            var reply = attendance.reply(callId,
                    "Consulte se amanhã, 20/10/2026, às 14:30 está livre. Se estiver, reserve temporariamente. Ainda não confirme.");
            var reservation = repository.findTemporaryByCallId(callId)
                    .orElseThrow(() -> new AssertionError("Model did not reserve: " + reply));
            assertThat(reservation.status()).isEqualTo(Appointment.Status.TEMPORARY);
            assertThat(reservation.dateTime()).isEqualTo("2026-10-20T14:30:00");
            attendance.reply(callId,
                    "Sim, confirmo explicitamente o agendamento de 20/10/2026 às 14:30. Meu nome é Ana Silva.");
            var confirmed = repository.findById(reservation.id()).orElseThrow();
            assertThat(confirmed.status()).isEqualTo(Appointment.Status.CONFIRMED);
            assertThat(confirmed.customerName()).isEqualTo("Ana Silva");
        } finally {
            appointments.endCall(callId);
        }
    }
}
