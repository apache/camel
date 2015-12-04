package org.apache.camel.component.mllp;

public class MllpException extends Exception {
    public MllpException(String message) {
        super(message);
    }

    public MllpException(String message, Throwable cause) {
        super(message, cause);
    }

    public MllpException(Throwable cause) {
        super(cause);
    }

    public MllpException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
