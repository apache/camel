package org.apache.camel.maven.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.debezium.config.Field;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.text.CaseUtils;
import org.apache.kafka.common.config.ConfigDef;

// Proxy class for ConfigKey
public class ConnectorConfigField {

    private final ConfigDef.ConfigKey fieldDef;
    private final boolean isDeprecated;
    private final boolean isRequired;
    private final Object overrideDefaultValue;

    public ConnectorConfigField(final ConfigDef.ConfigKey configKey, final boolean isDeprecated, final boolean isRequired, final Object overrideDefaultValue) {
        ObjectHelper.notNull(configKey, "configKey");
        ObjectHelper.notNull(isDeprecated, "isDeprecated");
        ObjectHelper.notNull(isRequired, "isRequired");

        this.fieldDef = configKey;
        this.isDeprecated = isDeprecated;
        this.isRequired = isRequired;
        this.overrideDefaultValue = overrideDefaultValue;
    }

    public String getRawName() {
        return fieldDef.name;
    }

    public String getFieldName() {
        return getCamelCase(fieldDef.name);
    }

    public String getFieldSetterMethodName() {
        return getSetterMethodName(fieldDef.name);
    }

    public String getFieldGetterMethodName() {
        return getGetterMethodName(fieldDef.name);
    }

    public Class<?> getRawType() {
        return getType(fieldDef.type);
    }

    public Object getDefaultValue() {
        if (overrideDefaultValue != null) {
            return overrideDefaultValue;
        }
        return fieldDef.defaultValue;
    }

    public String getDefaultValueAsString() {
        return getDefaultValue(fieldDef);
    }

    public boolean isInternal() {
        return fieldDef.name.startsWith(Field.INTERNAL_PREFIX);
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public String getDescription() {
        return fieldDef.documentation;
    }

    private String getSetterMethodName(final String name) {
        return getCamelCase("set." + name);
    }

    private String getGetterMethodName(final String name) {
        return getCamelCase("get." + name);
    }

    private String getCamelCase(final String name) {
        return CaseUtils.toCamelCase(name, false, '.');
    }

    private Class<?> getType(final ConfigDef.Type type) {
        switch (type) {
            case INT: return Integer.TYPE;
            case LIST: return List.class;
            case SHORT: return Short.TYPE;
            case DOUBLE: return Double.TYPE;
            case STRING:
            case PASSWORD:
            case CLASS:
                return String.class;
            case BOOLEAN: return Boolean.TYPE;
            case LONG: return Long.TYPE;
            default: throw new IllegalArgumentException(String.format("Type '%s' is not supported", type.name()));
        }
    }

    private String getDefaultValue(final ConfigDef.ConfigKey field) {
        if (getDefaultValue() != null) {
            if (field.type() == ConfigDef.Type.STRING || field.type() == ConfigDef.Type.PASSWORD) {
                return "\"" + getDefaultValue().toString() + "\"";
            }
            if (field.type() == ConfigDef.Type.CLASS) {
                return "\"" + ((Class) getDefaultValue()).getName() + "\"";
            }
            return getDefaultValue().toString();
        }
        return null;
    }
}
