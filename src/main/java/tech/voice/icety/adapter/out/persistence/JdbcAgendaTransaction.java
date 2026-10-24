package tech.voice.icety.adapter.out.persistence;

import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tech.voice.icety.application.port.out.AgendaTransaction;
import tech.voice.icety.domain.model.BookingException;

public class JdbcAgendaTransaction implements AgendaTransaction {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcAgendaTransaction(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T locked(Supplier<T> action) {
        try {
            return transaction.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                jdbc.queryForObject("SELECT id FROM agenda WHERE id = 1 FOR UPDATE", Short.class);
                return action.get();
            });
        } catch (DataAccessException e) {
            if (e instanceof CannotAcquireLockException || hasSqlState(e, "55P03")) {
                throw new BookingException(BookingException.Kind.BUSY, "Agenda is busy; retry shortly");
            }

            if (e instanceof DuplicateKeyException || hasSqlState(e, "23505")) {
                throw BookingException.conflict("Appointment time or call already has an active reservation");
            }

            throw e;
        }
    }

    private boolean hasSqlState(Throwable error, String state) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return true;
            }
        }

        return false;
    }
}
