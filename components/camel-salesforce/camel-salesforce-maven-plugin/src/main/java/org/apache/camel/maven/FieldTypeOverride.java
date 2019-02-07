package org.apache.camel.maven;

import java.util.Collections;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Allows redefinition for Picklist/Multipicklist/Date fields to other than standard java types.
 */
public final class FieldTypeOverride {


    private String objectName;
    private String fieldName;
    private String javaOverrideTypeName;

    public FieldTypeOverride() {
    }

    public FieldTypeOverride(String objectName, String fieldName, String javaOverrideTypeName) {
        this.objectName = Objects.requireNonNull(objectName);
        this.fieldName = Objects.requireNonNull(fieldName);
        this.javaOverrideTypeName = Objects.requireNonNull(javaOverrideTypeName);
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getJavaOverrideTypeName() {
        return javaOverrideTypeName;
    }

    public void setJavaOverrideTypeName(String javaOverrideTypeName) {
        this.javaOverrideTypeName = javaOverrideTypeName;
    }

    static final class Definition {

        private final String correspondingSalesforceFieldType;
        private final String correspondingSalesforceFieldSoapType;
        private final Set<String> javaTypeNames;

        Definition(String correspondingSalesforceFieldType, String correspondingSalesforceFieldSoapType, String javaTypeName) {
            this.correspondingSalesforceFieldType = Objects.requireNonNull(correspondingSalesforceFieldType);
            this.correspondingSalesforceFieldSoapType = Objects.requireNonNull(correspondingSalesforceFieldSoapType);
            this.javaTypeNames = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(javaTypeName)));
        }

        Definition(String correspondingSalesforceFieldType, String correspondingSalesforceFieldSoapType, String... javaTypeNames) {
            Objects.requireNonNull(javaTypeNames);
            this.correspondingSalesforceFieldType = Objects.requireNonNull(correspondingSalesforceFieldType);
            this.correspondingSalesforceFieldSoapType = Objects.requireNonNull(correspondingSalesforceFieldSoapType);
            this.javaTypeNames = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(javaTypeNames)));
        }

        String getCorrespondingSalesforceFieldType() {
            return correspondingSalesforceFieldType;
        }

        String getCorrespondingSalesforceFieldSoapType() {
            return correspondingSalesforceFieldSoapType;
        }

        Set<String> getJavaTypeNames() {
            return javaTypeNames;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Definition that = (Definition) o;
            return Objects.equals(correspondingSalesforceFieldType, that.correspondingSalesforceFieldType) &&
                    Objects.equals(correspondingSalesforceFieldSoapType, that.correspondingSalesforceFieldSoapType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(correspondingSalesforceFieldType, correspondingSalesforceFieldSoapType);
        }

        @Override
        public String toString() {
            return "Definition{" +
                    "correspondingSalesforceFieldType='" + correspondingSalesforceFieldType + '\'' +
                    ", correspondingSalesforceFieldSoapType='" + correspondingSalesforceFieldSoapType + '\'' +
                    '}';
        }
    }
}
