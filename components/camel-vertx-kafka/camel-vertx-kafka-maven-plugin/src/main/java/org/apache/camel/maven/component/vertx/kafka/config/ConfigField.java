package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.text.CaseUtils;
import org.apache.commons.text.WordUtils;
import org.apache.kafka.common.config.ConfigDef;

// Proxy class for ConfigKey
public class ConfigField {

    private static final String[] SECURITY_PREFIXES = { "sasl", "ssl" };
    private static final String[] SECURITY_KEYWORDS = { "security" };
    private static final String INTERNAL_PREFIX = "internal.";

    private final ConfigDef.ConfigKey fieldDef;
    private final boolean isDeprecated;
    private final boolean isRequired;
    private final boolean isUriPathOption;
    private final Object overrideDefaultValue;
    private final String overrideVariableName;

    private ConfigField(final ConfigDef.ConfigKey configKey, final boolean isDeprecated, final boolean isRequired,
                        final Object overrideDefaultValue, final String overrideVariableName, final boolean isUriPathOption) {
        ObjectHelper.notNull(configKey, "configKey");
        ObjectHelper.notNull(isDeprecated, "isDeprecated");
        ObjectHelper.notNull(isRequired, "isRequired");

        this.fieldDef = configKey;
        this.isDeprecated = isDeprecated;
        this.isRequired = isRequired;
        this.overrideDefaultValue = overrideDefaultValue;
        this.overrideVariableName = overrideVariableName;
        this.isUriPathOption = isUriPathOption;
    }

    public static ConfigFieldBuilder builder() {
        return new ConfigFieldBuilder();
    }

    public String getName() {
        return fieldDef.name;
    }

    public String getVariableName() {
        if (ObjectHelper.isNotEmpty(overrideVariableName)) {
            return overrideVariableName;
        }
        return getCamelCase(fieldDef.name);
    }

    public String getFieldSetterMethodName() {
        return getSetterMethodName(getVariableName());
    }

    public String getFieldGetterMethodName() {
        return getGetterMethodName(getVariableName(), fieldDef.type);
    }

    public Class<?> getRawType() {
        return getType(fieldDef.type);
    }

    public Object getDefaultValue() {
        if (overrideDefaultValue != null) {
            return overrideDefaultValue;
        }

        if (fieldDef.defaultValue == null || fieldDef.defaultValue.equals(ConfigDef.NO_DEFAULT_VALUE)) {
            return null;
        }

        return fieldDef.defaultValue;
    }

    public String getDefaultValueAsAssignableFriendly() {
        if (ObjectHelper.isNotEmpty(getDefaultValue())) {
            final String convertedValue = getDefaultValueForStringPassClassListTypes(fieldDef.type, getDefaultValue());

            if (ObjectHelper.isNotEmpty(convertedValue)) {
                return wrapString(convertedValue);
            }

            return getDefaultValue().toString();
        }
        return null;
    }

    public String getDefaultValueWrappedInAsString() {
        if (ObjectHelper.isNotEmpty(getDefaultValue())) {
            final String convertedValue = getDefaultValueForStringPassClassListTypes(fieldDef.type, getDefaultValue());

            if (ObjectHelper.isNotEmpty(convertedValue)) {
                return wrapString(convertedValue);
            }

            return wrapString(getDefaultValue().toString());
        }
        return null;
    }

    public String getDefaultValueAsTimeString() {
        if (isTimeField()) {
            final long defaultValueAsLong = Long.parseLong(getDefaultValueAsAssignableFriendly());

            return ConfigUtils.toTimeAsString(defaultValueAsLong);
        }

        return null;
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

    public boolean isUriPathOption() {
        return isUriPathOption;
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

    public boolean isInternal() {
        return fieldDef.name.startsWith(INTERNAL_PREFIX) || fieldDef.internalConfig;
    }

    public List<String> getValidStrings() {
        return getValidStringFromValidator(fieldDef.validator);
    }

    public boolean isSecurityType() {
        if (Arrays.stream(SECURITY_PREFIXES).anyMatch(word -> getName().toLowerCase().startsWith(word))) {
            return true;
        }

        return Arrays.stream(SECURITY_KEYWORDS).anyMatch(word -> getName().toLowerCase().contains(word));
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
                return getPrimitiveOrBoxed(Integer.TYPE, Integer.class);
            case SHORT:
                return getPrimitiveOrBoxed(Short.TYPE, Short.class);
            case DOUBLE:
                return getPrimitiveOrBoxed(Double.TYPE, Double.class);
            case STRING:
            case PASSWORD:
            case CLASS:
            case LIST:
                return String.class;
            case BOOLEAN:
                return Boolean.TYPE;
            case LONG:
                return getPrimitiveOrBoxed(Long.TYPE, Long.class);
            default:
                throw new IllegalArgumentException(String.format("Type '%s' is not supported", type.name()));
        }
    }

    private Class<?> getPrimitiveOrBoxed(final Class<?> primitiveType, final Class<?> boxedType) {
        if (ObjectHelper.isEmpty(getDefaultValue())) {
            return boxedType;
        }

        return primitiveType;
    }

    @SuppressWarnings("unchecked")
    private String getDefaultValueForStringPassClassListTypes(final ConfigDef.Type type, final Object value) {
        if (type == ConfigDef.Type.STRING || type == ConfigDef.Type.PASSWORD
                || type == ConfigDef.Type.CLASS) {
            return convertValueToStringAndUnwrap(value);
        }
        if (type == ConfigDef.Type.LIST) {
            return ((List<Object>) value)
                    .stream()
                    .map(this::convertValueToStringAndUnwrap)
                    .collect(Collectors.joining(","));
        }

        return null;
    }

    private String convertValueToStringAndUnwrap(final Object defaultValue) {
        if (defaultValue instanceof Class) {
            return ((Class) defaultValue).getName();
        }
        return defaultValue.toString();
    }

    private String wrapString(final String value) {
        return "\"" + value + "\"";
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

    public static final class ConfigFieldBuilder {
        private ConfigDef.ConfigKey fieldDef;
        private boolean isDeprecated;
        private boolean isRequired;
        private boolean isUriPathOption;
        private Object overrideDefaultValue = null;
        private String overrideVariableName = null;

        private ConfigFieldBuilder() {
        }

        public ConfigFieldBuilder withFieldDef(ConfigDef.ConfigKey fieldDef) {
            this.fieldDef = fieldDef;
            return this;
        }

        public ConfigFieldBuilder isDeprecated() {
            this.isDeprecated = true;
            return this;
        }

        public ConfigFieldBuilder isRequired() {
            this.isRequired = true;
            return this;
        }

        public ConfigFieldBuilder isDeprecated(final boolean isDeprecated) {
            this.isDeprecated = isDeprecated;
            return this;
        }

        public ConfigFieldBuilder isRequired(final boolean isRequired) {
            this.isRequired = isRequired;
            return this;
        }

        public ConfigFieldBuilder isUriPathOption(final boolean isUriPathOption) {
            this.isUriPathOption = isUriPathOption;
            return this;
        }

        public ConfigFieldBuilder withOverrideDefaultValue(Object overrideDefaultValue) {
            this.overrideDefaultValue = overrideDefaultValue;
            return this;
        }

        public ConfigFieldBuilder withOverrideVariableName(String overrideVariableName) {
            this.overrideVariableName = overrideVariableName;
            return this;
        }

        public ConfigField build() {
            return new ConfigField(
                    fieldDef, isDeprecated, isRequired, overrideDefaultValue, overrideVariableName, isUriPathOption);
        }
    }
}
