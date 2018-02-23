package org.apache.camel.component.as2.api.entity;

public enum AS2DispositionType {
    PROCESSED("processed"),
    FAILED("failed");
    
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
