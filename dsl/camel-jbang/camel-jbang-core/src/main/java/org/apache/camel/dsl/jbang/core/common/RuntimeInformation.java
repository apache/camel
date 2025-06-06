package org.apache.camel.dsl.jbang.core.common;

import static org.apache.camel.dsl.jbang.core.common.CatalogLoader.QUARKUS_GROUP_ID;

public class RuntimeInformation {
    private RuntimeType type;
    private String camelVersion;
    private String camelQuarkusVersion;
    private String springBootVersion;

    private String quarkusVersion;
    private String quarkusGroupId;

    public RuntimeInformation() {
        this.quarkusGroupId = QUARKUS_GROUP_ID;
    }

    public RuntimeType getType() {
        return type;
    }

    public void setType(RuntimeType type) {
        this.type = type;
    }

    public String getCamelVersion() {
        return camelVersion;
    }

    public void setCamelVersion(String camelVersion) {
        this.camelVersion = camelVersion;
    }

    public String getCamelQuarkusVersion() {
        return camelQuarkusVersion;
    }

    public void setCamelQuarkusVersion(String camelQuarkusVersion) {
        this.camelQuarkusVersion = camelQuarkusVersion;
    }

    public String getSpringBootVersion() {
        return springBootVersion;
    }

    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }

    public String getQuarkusVersion() {
        return quarkusVersion;
    }

    public void setQuarkusVersion(String quarkusVersion) {
        this.quarkusVersion = quarkusVersion;
    }

    public String getQuarkusGroupId() {
        return quarkusGroupId;
    }

    public void setQuarkusGroupId(String quarkusGroupId) {
        this.quarkusGroupId = quarkusGroupId;
    }
}
