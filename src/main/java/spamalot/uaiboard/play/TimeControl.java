package spamalot.uaiboard.play;

/**
 * How the engine is limited: a fixed time for every move, or a game clock for both sides (a base time and an increment added
 * after each move, in milliseconds).
 */
public sealed interface TimeControl {
    record MoveTime(long millis) implements TimeControl {
        public MoveTime {
            if (millis < 1) {
                throw new IllegalArgumentException("move time must be positive");
            }
        }
    }

    record Clock(long baseMillis, long incrementMillis) implements TimeControl {
        public Clock {
            if (baseMillis < 1 || incrementMillis < 0) {
                throw new IllegalArgumentException("clock needs a positive base time and no negative increment");
            }
        }
    }

    /** The text form stored in the settings file: {@code movetime:1000} or {@code clock:300000:2000}. */
    default String format() {
        return switch (this) {
            case MoveTime m -> "movetime:" + m.millis();
            case Clock c -> "clock:" + c.baseMillis() + ":" + c.incrementMillis();
        };
    }

    /** Parses {@link #format} output; returns {@code fallback} if the text is not understood. */
    static TimeControl parse(String text, TimeControl fallback) {
        try {
            String[] t = text.trim().split(":");
            if (t.length == 2 && t[0].equals("movetime")) {
                return new MoveTime(Long.parseLong(t[1]));
            }
            if (t.length == 3 && t[0].equals("clock")) {
                return new Clock(Long.parseLong(t[1]), Long.parseLong(t[2]));
            }
        } catch (RuntimeException e) {
            // fall through to the fallback
        }
        return fallback;
    }
}
