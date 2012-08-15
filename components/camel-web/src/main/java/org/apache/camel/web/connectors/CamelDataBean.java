package org.apache.camel.web.connectors;

import java.util.HashMap;
import java.util.Map;

/**
 * Camel Managed Bean representation
 */
public class CamelDataBean {

    private final Map<String, Object> properties = new HashMap<String, Object>();

    private String name;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
