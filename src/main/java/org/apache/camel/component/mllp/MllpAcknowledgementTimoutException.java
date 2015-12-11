package org.apache.camel.component.mllp;

public class MllpAcknowledgementTimoutException extends MllpTimeoutException {
    public MllpAcknowledgementTimoutException(String message) {
        super(message);
    }

    public MllpAcknowledgementTimoutException(String message, byte[] mllpPayload) {
        super(message, mllpPayload);
    }

    public MllpAcknowledgementTimoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public MllpAcknowledgementTimoutException(String message, byte[] mllpPayload, Throwable cause) {
        super(message, mllpPayload, cause);
    }
}
