package spamalot.uaiboard.uai;

/** An engine did not start, did not answer in time, exited, or said something that cannot be understood. */
public class UaiException extends Exception {
    public UaiException(String message) {
        super(message);
    }

    public UaiException(String message, Throwable cause) {
        super(message, cause);
    }
}
