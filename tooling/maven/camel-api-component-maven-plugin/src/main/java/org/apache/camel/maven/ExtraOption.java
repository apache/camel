package org.apache.camel.maven;

/**
 * Extra endpoint option to add to generated *EndpointConfiguration
 */
public class ExtraOption {

    private String type;

    private String name;

    public ExtraOption() {
    }

    public ExtraOption(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
