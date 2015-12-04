package org.apache.camel.component.mllp;

public class MllpResponseTimeoutException extends MllpException {
    public MllpResponseTimeoutException(String message) {
        super(message);
    }

    public MllpResponseTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public MllpResponseTimeoutException(Throwable cause) {
        super(cause);
    }

    public MllpResponseTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
