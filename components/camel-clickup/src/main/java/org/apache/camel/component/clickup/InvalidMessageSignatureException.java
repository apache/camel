package org.apache.camel.component.clickup;

public class InvalidMessageSignatureException extends RuntimeException {

    private final String message;
    private final String messageSignature;
    private final String calculatedSignature;

    public InvalidMessageSignatureException(String message, String messageSignature, String calculatedSignature) {
        this.message = message;
        this.messageSignature = messageSignature;
        this.calculatedSignature = calculatedSignature;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getMessageSignature() {
        return messageSignature;
    }

    public String getCalculatedSignature() {
        return calculatedSignature;
    }

}
