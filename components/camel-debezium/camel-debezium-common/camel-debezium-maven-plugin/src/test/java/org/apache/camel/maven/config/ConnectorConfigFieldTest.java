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

import java.util.Arrays;
import java.util.Collections;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorConfigFieldTest {

    @Test
    void testIfReturnsCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField = new ConnectorConfigField(configKey, false, true, "I am overridden");

        assertEquals("fieldTest", connectorConfigField.getFieldName());
        assertEquals("setFieldTest", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getFieldTest", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertEquals("I am overridden", connectorConfigField.getDefaultValue());
        assertEquals("\"I am overridden\"", connectorConfigField.getDefaultValueAsString());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
        assertFalse(connectorConfigField.isTimeField());
    }

    @Test
    void testIfHandlesUnderscoreFieldsCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test_underscore", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField = new ConnectorConfigField(configKey, false, true, null);

        assertEquals("fieldTestUnderscore", connectorConfigField.getFieldName());
        assertEquals("setFieldTestUnderscore", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getFieldTestUnderscore", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
        assertFalse(connectorConfigField.isTimeField());
    }

    @Test
    void testIfDiscoversDurationFieldCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test_underscore.Ms", ConfigDef.Type.LONG, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField = new ConnectorConfigField(configKey, false, true, null);

        assertTrue(connectorConfigField.isTimeField());

        final ConfigDef.ConfigKey configKey2 = new ConfigDef.ConfigKey(
                "field.test_underscore.ms", ConfigDef.Type.INT, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField2 = new ConnectorConfigField(configKey2, false, true, null);

        assertTrue(connectorConfigField2.isTimeField());

        final ConfigDef.ConfigKey configKey3 = new ConfigDef.ConfigKey(
                "field", ConfigDef.Type.INT, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField3 = new ConnectorConfigField(configKey3, false, true, null);

        assertFalse(connectorConfigField3.isTimeField());

        final ConfigDef.ConfigKey configKey4 = new ConfigDef.ConfigKey(
                "field.ms.field", ConfigDef.Type.LONG, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField4 = new ConnectorConfigField(configKey4, false, true, null);

        assertFalse(connectorConfigField4.isTimeField());
    }

    @ParameterizedTest
    @EnumSource
    void testDefaultValueAsString(DefaultValueAsStringInputData data) {
        assertEquals(data.expectedDefaultValueAsString,
                new ConnectorConfigField(data.asConfigKey(), false, false, null).getDefaultValueAsString());
    }

    @ParameterizedTest
    @EnumSource
    void testDefaultValueAsStringLiteral(DefaultValueAsStringInputData data) {
        assertEquals(data.expectedDefaultValueAsStringLiteral,
                new ConnectorConfigField(data.asConfigKey(), false, false, data.overrideDefaultValue)
                        .getDefaultValueAsStringLiteral());
    }

    enum DefaultValueAsStringInputData {
        BOOLEAN(ConfigDef.Type.BOOLEAN, true, "true", false),
        STRING(ConfigDef.Type.STRING, "Some Value", "\"Some Value\""),
        INT(ConfigDef.Type.INT, 17, "17", false),
        SHORT(ConfigDef.Type.SHORT, (short) 7, "7", false),
        LONG(ConfigDef.Type.LONG, (long) 47, "47", false),
        DOUBLE(ConfigDef.Type.DOUBLE, 2.5, "2.5", false),
        LIST(ConfigDef.Type.LIST, Arrays.asList("1", "2", "3"), "\"1,2,3\""),
        CLASS(ConfigDef.Type.CLASS, String.class, "\"java.lang.String\""),
        CLASS_AS_STRING(ConfigDef.Type.CLASS, String.class.getName(), String.class.getName(), "\"java.lang.String\"", true),
        PASSWORD(ConfigDef.Type.PASSWORD, "Some Pwd", "\"[hidden]\"");

        final ConfigDef.Type type;
        final Object defaultValue;
        final Object overrideDefaultValue;
        final String expectedDefaultValueAsString;
        final String expectedDefaultValueAsStringLiteral;

        DefaultValueAsStringInputData(ConfigDef.Type type, Object defaultValue, String expectedDefaultValueAsString) {
            this(type, defaultValue, expectedDefaultValueAsString, true);
        }

        DefaultValueAsStringInputData(ConfigDef.Type type, Object defaultValue, String expectedDefaultValueAsString,
                                      boolean isStringLiteral) {
            this(type, defaultValue, null, expectedDefaultValueAsString, isStringLiteral);
        }

        DefaultValueAsStringInputData(ConfigDef.Type type, Object defaultValue, Object overrideDefaultValue,
                                      String expectedDefaultValueAsString, boolean isStringLiteral) {
            this.type = type;
            this.defaultValue = defaultValue;
            this.overrideDefaultValue = overrideDefaultValue;
            this.expectedDefaultValueAsString = expectedDefaultValueAsString;
            this.expectedDefaultValueAsStringLiteral
                    = isStringLiteral ? expectedDefaultValueAsString : String.format("\"%s\"", expectedDefaultValueAsString);
        }

        ConfigDef.ConfigKey asConfigKey() {
            return new ConfigDef.ConfigKey(
                    "field.ms.field", type, defaultValue,
                    null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                    Collections.emptyList(),
                    null, false);
        }
    }

}
