package org.apache.camel.component.rabbitmq.testbeans;

import java.io.Serializable;

public class TestSerializableObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private String description;
    private String name;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}