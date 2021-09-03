/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    private final Set<String> labels;
    private final Set<String> enums;
    private final String name;
    private final String documentation;
    private final Object defaultValue;
    private final String variableName;
    private final ConfigDef.Importance importance;
    private final ConfigDef.Type type;
    private final ConfigDef.Validator validator;
    private final boolean isDeprecated;
    private final boolean isRequired;
    private final boolean isUriPathOption;
    private final boolean isInternal;

    // only used for reflection
    // use the builder instead
    public ConfigField() {
        this.name = "";
        this.documentation = null;
        this.defaultValue = null;
        this.variableName = null;
        this.importance = null;
        this.type = null;
        this.validator = null;
        this.isDeprecated = false;
        this.isRequired = false;
        this.isUriPathOption = false;
        this.isInternal = false;
        this.labels = Collections.emptySet();
        this.enums = Collections.emptySet();
    }

    private ConfigField(String name, String documentation, Object defaultValue, String variableName,
                        ConfigDef.Importance importance,
                        ConfigDef.Type type, ConfigDef.Validator validator, boolean isDeprecated, boolean isRequired,
                        boolean isUriPathOption, boolean isInternal, Set<String> labels) {
        this.name = name;
        this.documentation = documentation;
        this.defaultValue = defaultValue;
        this.variableName = variableName;
        this.importance = importance;
        this.type = type;
        this.validator = validator;
        this.isDeprecated = isDeprecated;
        this.isRequired = isRequired;
        this.isUriPathOption = isUriPathOption;
        this.isInternal = isInternal;
        this.labels = labels;
        this.enums = Collections.emptySet();

        if (isSecurityType()) {
            labels.add("security");
        }
    }

    public static ConfigFieldBuilder fromConfigKey(final ConfigDef.ConfigKey configKey) {
        return new ConfigFieldBuilder(configKey);
    }

    public static ConfigFieldBuilder withName(final String name) {
        return new ConfigFieldBuilder(name);
    }

    public String getName() {
        return name;
    }

    public String getVariableName() {
        if (ObjectHelper.isNotEmpty(variableName)) {
            return variableName;
        }
        return getCamelCase(getName());
    }

    public String getFieldSetterMethodName() {
        return getSetterMethodName(getVariableName());
    }

    public String getFieldGetterMethodName() {
        return getGetterMethodName(getVariableName(), type);
    }

    public Class<?> getRawType() {
        return getType(type);
    }

    public Object getDefaultValue() {

        if (defaultValue == null || defaultValue.equals(ConfigDef.NO_DEFAULT_VALUE)) {
            return null;
        }

        return defaultValue;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public String getDefaultValueAsAssignableFriendly() {
        if (ObjectHelper.isNotEmpty(getDefaultValue())) {
            final String convertedValue = getDefaultValueForStringPassClassListTypes(type, getDefaultValue());

            if (ObjectHelper.isNotEmpty(convertedValue)) {
                return wrapString(convertedValue);
            }

            return getDefaultValue().toString();
        }
        return null;
    }

    public String getDefaultValueWrappedInAsString() {
        if (ObjectHelper.isNotEmpty(getDefaultValue())) {
            final String convertedValue = getDefaultValueForStringPassClassListTypes(type, getDefaultValue());

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
        return importance;
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
        if (documentation != null) {
            return removeNonAsciiChars(documentation);
        }
        return "";
    }

    public boolean isTimeField() {
        // since we don't really have an info if the field is a time or not, we use a hack that if the field name ends with `ms` and of type
        // int or long. Not pretty but is the only feasible workaround here.
        return isMillSecondsInTheFieldName(name)
                && (type == ConfigDef.Type.INT || type == ConfigDef.Type.LONG);
    }

    public boolean isInternal() {
        return getName().startsWith(INTERNAL_PREFIX) || isInternal;
    }

    public List<String> getValidEnumsStrings() {
        if (enums.isEmpty()) {
            return getValidStringFromValidator(validator);
        }

        return new ArrayList<>(enums);
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
            // in case the value already in string joined with comma
            if (value instanceof String) {
                return (String) value;
            }
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
        private String name;
        private String documentation;
        private Object defaultValue;
        private String variableName;
        private ConfigDef.Importance importance;
        private ConfigDef.Type type;
        private ConfigDef.Validator validator;
        private boolean isDeprecated;
        private boolean isRequired;
        private boolean isUriPathOption;
        private boolean isInternal;
        private final Set<String> labels = new LinkedHashSet<>();

        private ConfigFieldBuilder(ConfigDef.ConfigKey fieldDef) {
            this.name = fieldDef.name;
            this.documentation = fieldDef.documentation;
            this.defaultValue = fieldDef.defaultValue;
            this.importance = fieldDef.importance;
            this.type = fieldDef.type;
            this.validator = fieldDef.validator;
            this.isInternal = fieldDef.internalConfig;
        }

        private ConfigFieldBuilder(String name) {
            this.name = name;
        }

        public ConfigFieldBuilder withName(final String name) {
            this.name = name;
            return this;
        }

        public ConfigFieldBuilder withDocumentation(final String documentation) {
            this.documentation = documentation;
            return this;
        }

        public ConfigFieldBuilder withImportance(final ConfigDef.Importance importance) {
            this.importance = importance;
            return this;
        }

        public ConfigFieldBuilder withType(final ConfigDef.Type type) {
            this.type = type;
            return this;
        }

        public ConfigFieldBuilder withValidator(final ConfigDef.Validator validator) {
            this.validator = validator;
            return this;
        }

        public ConfigFieldBuilder withDefaultValue(Object overrideDefaultValue) {
            this.defaultValue = overrideDefaultValue;
            return this;
        }

        public ConfigFieldBuilder withVariableName(String overrideVariableName) {
            this.variableName = overrideVariableName;
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

        public ConfigFieldBuilder isUriPathOption() {
            this.isUriPathOption = true;
            return this;
        }

        public ConfigFieldBuilder isInternal() {
            this.isInternal = true;
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

        public ConfigFieldBuilder withLabels(final String... labelsArg) {
            ObjectHelper.notNull(labelsArg, "labelsArg");

            labels.addAll(Arrays.stream(labelsArg).collect(Collectors.toSet()));
            return this;
        }

        public ConfigField build() {
            ObjectHelper.notNull(name, "name");
            ObjectHelper.notNull(type, "type");

            return new ConfigField(
                    name, documentation, defaultValue, variableName, importance, type, validator, isDeprecated, isRequired,
                    isUriPathOption, isInternal, labels);
        }
    }
}
