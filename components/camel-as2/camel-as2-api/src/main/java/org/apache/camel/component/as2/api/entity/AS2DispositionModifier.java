package org.apache.camel.component.as2.api.entity;

public class AS2DispositionModifier {
    
    public static final AS2DispositionModifier ERROR = new AS2DispositionModifier("error");
    public static final AS2DispositionModifier ERROR_AUTHENTICATION_FAILED = new AS2DispositionModifier("error: authentication-failed");
    public static final AS2DispositionModifier ERROR_DECOMPRESSION_FAILED = new AS2DispositionModifier("error: decompression-failed");
    public static final AS2DispositionModifier ERROR_DECRYPTION_FAILED = new AS2DispositionModifier("error: decryption-failed");
    public static final AS2DispositionModifier ERROR_INSUFFICIENT_MESSAGE_SECURITY = new AS2DispositionModifier("error: insufficient-message-security");
    public static final AS2DispositionModifier ERROR_INTEGRITY_CHECK_FAILED = new AS2DispositionModifier("error: integrity-check-failed");
    public static final AS2DispositionModifier ERROR_UNEXPECTED_PROCESSING_ERROR = new AS2DispositionModifier("error: unexpected-processing-error");
    public static final AS2DispositionModifier WARNING = new AS2DispositionModifier("warning");
    
    public static AS2DispositionModifier createWarning(String description) {
        return new AS2DispositionModifier("warning: " + description);
    }
    
    public static AS2DispositionModifier createFailure(String description) {
        return new AS2DispositionModifier("failure: " + description);
    }
    
    public static AS2DispositionModifier parseDispositionType(String dispositionModifierString) {
        switch(dispositionModifierString) {
        case "error":
            return ERROR;
        case "error: authentication-failed":
            return ERROR_AUTHENTICATION_FAILED;
        case "error: decompression-failed\"":
            return ERROR_DECOMPRESSION_FAILED;
        case "error: decryption-failed":
            return ERROR_DECRYPTION_FAILED;
        case "error: insufficient-message-security":
            return ERROR_INSUFFICIENT_MESSAGE_SECURITY;
        case "error: integrity-check-failed":
            return ERROR_INTEGRITY_CHECK_FAILED;
        case "error: unexpected-processing-error":
            return ERROR_UNEXPECTED_PROCESSING_ERROR;
        case "warning":
            return WARNING;
        default:
            if (dispositionModifierString.startsWith("warning: ") || dispositionModifierString.startsWith("failure: ")) {
                return new AS2DispositionModifier(dispositionModifierString);
            }
            return null;
        }
    }
    
    private String modifier;
    
    private AS2DispositionModifier(String modifier) {
        this.modifier = modifier;
    }
    
    public String getModifier() {
        return modifier;
    }
    
    public boolean isError() {
        return modifier.startsWith("error: ");
    }

    public boolean isFailuer() {
        return modifier.startsWith("failure: ");
    }

    public boolean isWarning() {
        return modifier.startsWith("warning: ");
    }

    @Override
    public String toString() {
        return modifier;
    }
}
