package tech.voice.icety.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.voice.icety.application.port.out.AgendaStore;
import tech.voice.icety.domain.model.Appointment;
import tech.voice.icety.domain.model.CallSession;

public class JdbcAgendaStore implements AgendaStore {
    private final JdbcTemplate jdbc;

    public JdbcAgendaStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CallSession> call(String id) {
        return jdbc.query("SELECT * FROM call_sessions WHERE id = ?", this::mapCall, id)
                .stream().findFirst();
    }

    @Override
    public void save(CallSession call) {
        jdbc.update("""
                INSERT INTO call_sessions (id, status, last_activity_at, expires_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status,
                    last_activity_at = EXCLUDED.last_activity_at, expires_at = EXCLUDED.expires_at
                """, call.id(), call.status().name(), Timestamp.from(call.lastActivityAt()),
                Timestamp.from(call.expiresAt()));
    }

    @Override
    public List<CallSession> expiredCalls(Instant now) {
        return jdbc.query("SELECT * FROM call_sessions WHERE status = 'ACTIVE' AND expires_at <= ?",
                this::mapCall, Timestamp.from(now));
    }

    @Override
    public Optional<Appointment> appointment(UUID id) {
        return jdbc.query("SELECT * FROM appointments WHERE id = ?", this::mapAppointment, id)
                .stream().findFirst();
    }

    @Override
    public Optional<Appointment> temporaryForCall(String callId) {
        return jdbc.query("SELECT * FROM appointments WHERE call_id = ? AND status = 'TEMPORARY'",
                this::mapAppointment, callId).stream().findFirst();
    }

    @Override
    public boolean occupied(LocalDateTime time, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM appointments a JOIN call_sessions c ON c.id = a.call_id
                    WHERE a.date_time = ? AND (a.status = 'CONFIRMED'
                        OR (a.status = 'TEMPORARY' AND c.status = 'ACTIVE' AND c.expires_at > ?))
                )
                """, Boolean.class, time, Timestamp.from(now)));
    }

    @Override
    public void save(Appointment appointment) {
        jdbc.update("""
                INSERT INTO appointments (id, call_id, date_time, customer_name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET customer_name = EXCLUDED.customer_name,
                    status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
                """, appointment.id(), appointment.callId(), appointment.dateTime(), appointment.customerName(),
                appointment.status().name(), Timestamp.from(appointment.createdAt()),
                Timestamp.from(appointment.updatedAt()));
    }

    private CallSession mapCall(ResultSet rs, int row) throws SQLException {
        return new CallSession(rs.getString("id"), CallSession.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("last_activity_at").toInstant(), rs.getTimestamp("expires_at").toInstant());
    }

    private Appointment mapAppointment(ResultSet rs, int row) throws SQLException {
        return new Appointment(rs.getObject("id", UUID.class), rs.getString("call_id"),
                rs.getObject("date_time", LocalDateTime.class), rs.getString("customer_name"),
                Appointment.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
