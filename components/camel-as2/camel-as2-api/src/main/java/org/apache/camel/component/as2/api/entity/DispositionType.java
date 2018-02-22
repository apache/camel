package org.apache.camel.component.as2.api.entity;

public enum DispositionType {
    DISPLAYED("displayed"),
    DISPATCHED("dispatched"),
    PROCESSED("processed"),
    DELETED("deleted");
    
    private String type;
    
    private DispositionType(String type) {
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
