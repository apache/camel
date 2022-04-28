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
package org.apache.camel.maven.config;

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

    public ConnectorConfigField(final ConfigDef.ConfigKey configKey, final boolean isDeprecated, final boolean isRequired,
                                final Object overrideDefaultValue) {
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
        return getGetterMethodName(fieldDef.name, fieldDef.type);
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

    /**
     * @return the default value as a {@code String} between 2 double quotes.
     */
    public String getDefaultValueAsStringLiteral() {
        Object defaultValue = getDefaultValue();
        if (defaultValue == null) {
            return null;
        }
        if (fieldDef.type() == ConfigDef.Type.LIST
                || fieldDef.type() == ConfigDef.Type.CLASS && defaultValue instanceof Class) {
            defaultValue = ConfigDef.convertToString(defaultValue, fieldDef.type());
        }
        return String.format("\"%s\"", defaultValue);
    }

    public String getDefaultValueAsString() {
        return getDefaultValueWrappedInString(fieldDef);
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

    private boolean isMillSecondsInTheFieldName(final String name) {
        final String[] parts = name.split("\\.");
        return parts.length > 0 && parts[parts.length - 1].equalsIgnoreCase("ms");
    }

    private String getSetterMethodName(final String name) {
        return getCamelCase("set." + name);
    }

    private String getGetterMethodName(final String name, ConfigDef.Type type) {
        if (type == ConfigDef.Type.BOOLEAN) {
            return getCamelCase("is." + name);
        } else {
            return getCamelCase("get." + name);
        }
    }

    private String getCamelCase(final String name) {
        return CaseUtils.toCamelCase(name, false, '.', '_');
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
        final Object defaultValue = getDefaultValue();
        if (defaultValue == null) {
            return null;
        }
        if (fieldDef.type() == ConfigDef.Type.LIST
                || fieldDef.type() == ConfigDef.Type.CLASS && defaultValue instanceof Class) {
            return String.format("\"%s\"", ConfigDef.convertToString(defaultValue, fieldDef.type()));
        } else if (field.type() == ConfigDef.Type.STRING || field.type() == ConfigDef.Type.PASSWORD
                || field.type() == ConfigDef.Type.CLASS) {
            return String.format("\"%s\"", defaultValue);
        }
        return defaultValue.toString();
    }

    private String removeNonAsciiChars(final String text) {
        return text.replaceAll("[^\\x00-\\x7F]", "");
    }
}
