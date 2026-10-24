package tech.voice.icety.application.port.out;

import java.util.function.Supplier;

public interface AgendaTransaction {
    <T> T locked(Supplier<T> action);
}
