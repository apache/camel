package org.apache.camel.component.as2.api.entity;

public enum Importance {
    REQUIRED("required"),
    OPTIONAL("optional");

    public static Importance get(String importance) {
        switch(importance.toLowerCase()) {
        case "required":
            return REQUIRED;
        case "optional":
            return OPTIONAL;
         default:
             return null;
        }
    }
    
    private String importance;
    
    private Importance(String importance) {
        this.importance = importance;
    }
    
    public String getImportance() {
        return importance;
    }
    
    @Override
    public String toString() {
        return importance;
    }

}
