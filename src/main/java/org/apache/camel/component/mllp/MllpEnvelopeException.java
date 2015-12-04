package org.apache.camel.component.mllp;

public class MllpEnvelopeException extends MllpException {
    public MllpEnvelopeException(String message) {
        super(message);
    }

    public MllpEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }

    public MllpEnvelopeException(Throwable cause) {
        super(cause);
    }

    public MllpEnvelopeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
