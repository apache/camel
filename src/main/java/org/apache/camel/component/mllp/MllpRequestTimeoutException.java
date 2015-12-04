package org.apache.camel.component.mllp;

public class MllpRequestTimeoutException extends MllpException {
    public MllpRequestTimeoutException(String message) {
        super(message);
    }

    public MllpRequestTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public MllpRequestTimeoutException(Throwable cause) {
        super(cause);
    }

    public MllpRequestTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
