package org.apache.camel.processor.mllp;

public class Hl7AcknowledgementGenerationException extends Exception {
    private byte[] hl7Message = null;

    public Hl7AcknowledgementGenerationException(String message) {
        super(message);
    }
    public Hl7AcknowledgementGenerationException(String message, byte[] hl7Message) {
        super(message);
        this.hl7Message = hl7Message;
    }

    public Hl7AcknowledgementGenerationException(String message, byte[] hl7Message, Throwable cause) {
        super(message, cause);
        this.hl7Message = hl7Message;
    }


    public byte[] getHl7Message() {
        return hl7Message;
    }
}
