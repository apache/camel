package org.apache.camel.component.as2.api.entity;

public enum AS2DispositionType {
    PROCESSED("processed"),
    FAILED("failed");
    
    public static AS2DispositionType parseDispositionType(String dispositionTypeString) {
        switch(dispositionTypeString) {
        case "processed":
            return PROCESSED;
        case "failed":
            return FAILED;
        default:
            return null;
        }
    }
    
    private String type;
    
    private AS2DispositionType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
    
    @Override
    public String toString() {
        return type;
    }
}
