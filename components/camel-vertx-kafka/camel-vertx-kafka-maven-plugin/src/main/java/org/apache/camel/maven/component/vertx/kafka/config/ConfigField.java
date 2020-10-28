package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.text.CaseUtils;
import org.apache.commons.text.WordUtils;
import org.apache.kafka.common.config.ConfigDef;

// Proxy class for ConfigKey
public class ConfigField {

    private final ConfigDef.ConfigKey fieldDef;
    private final boolean isDeprecated;
    private final boolean isRequired;
    private final Object overrideDefaultValue;
    private final String overridePropertyName;
    private final CamelFieldType[] camelFieldTypes;

    public ConfigField(final ConfigDef.ConfigKey configKey, final boolean isDeprecated, final boolean isRequired,
                                final Object overrideDefaultValue, final String overridePropertyName, final CamelFieldType[] camelFieldTypes) {
        ObjectHelper.notNull(configKey, "configKey");
        ObjectHelper.notNull(isDeprecated, "isDeprecated");
        ObjectHelper.notNull(isRequired, "isRequired");

        this.fieldDef = configKey;
        this.isDeprecated = isDeprecated;
        this.isRequired = isRequired;
        this.overrideDefaultValue = overrideDefaultValue;
        this.overridePropertyName = overridePropertyName;
        this.camelFieldTypes = camelFieldTypes;
    }

    public ConfigField(final ConfigDef.ConfigKey configKey, final boolean isDeprecated, final boolean isRequired,
                       final Object overrideDefaultValue, final String overridePropertyName) {

        this(configKey, isDeprecated, isRequired, overrideDefaultValue, overridePropertyName, new CamelFieldType[]{});
    }

    public String getName() {
        return fieldDef.name;
    }

    public String getFieldName() {
        if (ObjectHelper.isNotEmpty(overridePropertyName)) {
            return overridePropertyName;
        }
        return getCamelCase(fieldDef.name);
    }

    public String getFieldSetterMethodName() {
        return getSetterMethodName(getFieldName());
    }

    public String getFieldGetterMethodName() {
        return getGetterMethodName(getFieldName(), fieldDef.type);
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
        return getDefaultValueWrappedInString(fieldDef);
    }

    public ConfigDef.Importance getImportance() {
        return fieldDef.importance;
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public String getDescription() {
        if (fieldDef.documentation != null) {
            return removeNonAsciiChars(fieldDef.documentation);
        }
        return "";
    }

    public boolean isTimeField() {
        // since we don't really have an info if the field is a time or not, we use a hack that if the field name ends with `ms` and of type
        // int or long. Not pretty but is the only feasible workaround here.
        return isMillSecondsInTheFieldName(fieldDef.name)
                && (fieldDef.type == ConfigDef.Type.INT || fieldDef.type == ConfigDef.Type.LONG);
    }

    public List<String> getValidStrings() {
        return getValidStringFromValidator(fieldDef.validator);
    }

    public CamelFieldType[] getCamelFieldTypes() {
        return camelFieldTypes;
    }

    public enum CamelFieldType {
        PRODUCER("producer"),
        CONSUMER("consumer"),
        COMMON("common"),
        SECURITY("security");

        private final String asString;
        CamelFieldType(final String asString) {
            this.asString = asString;
        }

        @Override
        public String toString() {
            return asString;
        }
    }

    private boolean isMillSecondsInTheFieldName(final String name) {
        final String[] parts = name.split("\\.");
        return parts.length > 0 && parts[parts.length - 1].equalsIgnoreCase("ms");
    }

    private String getSetterMethodName(final String name) {
        return appendMethodPrefix("set", name);
    }

    private String getGetterMethodName(final String name, ConfigDef.Type type) {
        if (type == ConfigDef.Type.BOOLEAN) {
            return appendMethodPrefix("is", name);
        } else {
            return appendMethodPrefix("get", name);
        }
    }

    private String getCamelCase(final String name) {
        return CaseUtils.toCamelCase(name, false, '.', '_');
    }

    private String appendMethodPrefix(final String prefix, final String name) {
        return prefix + WordUtils.capitalize(name);
    }

    private Class<?> getType(final ConfigDef.Type type) {
        switch (type) {
            case INT:
                return Integer.TYPE;
            case SHORT:
                return Short.TYPE;
            case DOUBLE:
                return Double.TYPE;
            case STRING:
            case PASSWORD:
            case CLASS:
            case LIST:
                return String.class;
            case BOOLEAN:
                return Boolean.TYPE;
            case LONG:
                return Long.TYPE;
            default:
                throw new IllegalArgumentException(String.format("Type '%s' is not supported", type.name()));
        }
    }

    private String getDefaultValueWrappedInString(final ConfigDef.ConfigKey field) {
        if (getDefaultValue() != null) {
            if (field.type() == ConfigDef.Type.STRING || field.type() == ConfigDef.Type.PASSWORD
                    || field.type() == ConfigDef.Type.CLASS) {
                if (getDefaultValue() instanceof Class) {
                    return "\"" + ((Class) getDefaultValue()).getName() + "\"";
                }
                return "\"" + getDefaultValue().toString() + "\"";
            }
            return getDefaultValue().toString();
        }
        return null;
    }

    private String removeNonAsciiChars(final String text) {
        return text.replaceAll("[^\\x00-\\x7F]", "");
    }

    private List<String> getValidStringFromValidator(final ConfigDef.Validator validator) {
        if (validator instanceof ConfigDef.ValidString || validator instanceof ConfigDef.ValidList) {
            // remove any '[', ']' or spaces
            final String cleanedValidStrings = validator.toString()
                    .replace("[", "")
                    .replace("]", "")
                    .replaceAll("\\s", "")
                    .trim();

            return Arrays.asList(cleanedValidStrings.split(",").clone());
        }

        return Collections.emptyList();
    }
}
