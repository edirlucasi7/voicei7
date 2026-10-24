package tech.voice.icety.domain.model;

public class BookingException extends RuntimeException {
    public enum Kind { INVALID, NOT_FOUND, CONFLICT, BUSY }

    private final Kind kind;

    public BookingException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static BookingException invalid(String message) {
        return new BookingException(Kind.INVALID, message);
    }

    public static BookingException conflict(String message) {
        return new BookingException(Kind.CONFLICT, message);
    }
}
