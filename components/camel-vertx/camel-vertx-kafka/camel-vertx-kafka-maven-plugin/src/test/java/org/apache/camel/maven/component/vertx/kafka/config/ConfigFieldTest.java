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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFieldTest {

    @Test
    void testIfReturnsCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = ConfigField
                .fromConfigKey(configKey)
                .isRequired()
                .withDefaultValue("I am overriden")
                .build();

        assertEquals("fieldTest", connectorConfigField.getVariableName());
        assertEquals("field.test", connectorConfigField.getName());
        assertEquals("setFieldTest", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getFieldTest", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertEquals("I am overriden", connectorConfigField.getDefaultValue());
        assertEquals("\"I am overriden\"", connectorConfigField.getDefaultValueAsAssignableFriendly());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
        assertFalse(connectorConfigField.isTimeField());
        assertTrue(connectorConfigField.getValidEnumsStrings().isEmpty());
        assertFalse(connectorConfigField.isSecurityType());

        final ConfigDef.ConfigKey configKeyBool = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.BOOLEAN, true,
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigFieldBool = ConfigField
                .fromConfigKey(configKeyBool)
                .isRequired()
                .build();

        assertEquals("fieldTest", connectorConfigFieldBool.getVariableName());
        assertEquals("setFieldTest", connectorConfigFieldBool.getFieldSetterMethodName());
        assertEquals("isFieldTest", connectorConfigFieldBool.getFieldGetterMethodName());
        assertEquals(boolean.class, connectorConfigFieldBool.getRawType());
        assertTrue((boolean) connectorConfigFieldBool.getDefaultValue());
        assertFalse(connectorConfigFieldBool.isDeprecated());
        assertTrue(connectorConfigFieldBool.isRequired());
        assertFalse(connectorConfigFieldBool.isTimeField());
    }

    @Test
    void testIfHandlesUnderscoreFieldsCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test_underscore", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = ConfigField
                .fromConfigKey(configKey)
                .isRequired()
                .build();

        assertEquals("fieldTestUnderscore", connectorConfigField.getVariableName());
        assertEquals("setFieldTestUnderscore", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getFieldTestUnderscore", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
        assertFalse(connectorConfigField.isTimeField());
    }

    @Test
    public void testIfDiscoversDurationFieldCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test_underscore.Ms", ConfigDef.Type.LONG, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = ConfigField
                .fromConfigKey(configKey)
                .isRequired()
                .build();

        assertTrue(connectorConfigField.isTimeField());

        final ConfigDef.ConfigKey configKey2 = new ConfigDef.ConfigKey(
                "field.test_underscore.ms", ConfigDef.Type.INT, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField2 = ConfigField
                .fromConfigKey(configKey2)
                .isRequired()
                .build();

        assertTrue(connectorConfigField2.isTimeField());

        final ConfigDef.ConfigKey configKey3 = new ConfigDef.ConfigKey(
                "field", ConfigDef.Type.INT, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField3 = ConfigField
                .fromConfigKey(configKey3)
                .isRequired()
                .build();

        assertFalse(connectorConfigField3.isTimeField());

        final ConfigDef.ConfigKey configKey4 = new ConfigDef.ConfigKey(
                "field.ms.field", ConfigDef.Type.LONG, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField4 = ConfigField
                .fromConfigKey(configKey4)
                .isRequired()
                .isDeprecated()
                .build();

        assertFalse(connectorConfigField4.isTimeField());
    }

    @Test
    void testIfOverrideProperty() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.CLASS, ConfigFieldTest.class,
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = ConfigField
                .fromConfigKey(configKey)
                .withVariableName("emptyField")
                .isRequired()
                .build();

        assertEquals("field.test", connectorConfigField.getName());
        assertEquals("emptyField", connectorConfigField.getVariableName());
        assertEquals("setEmptyField", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getEmptyField", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertEquals("\"org.apache.camel.maven.component.vertx.kafka.config.ConfigFieldTest\"",
                connectorConfigField.getDefaultValueAsAssignableFriendly());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
        assertFalse(connectorConfigField.isTimeField());
    }

    @Test
    void testIfTypeIsList() {
        final List<String> strings = new LinkedList<>();
        strings.add("check-1");
        strings.add("check-2");

        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.LIST, strings,
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField configField = ConfigField
                .fromConfigKey(configKey)
                .isRequired()
                .build();

        assertEquals(strings, configField.getDefaultValue());
        assertEquals("\"check-1,check-2\"", configField.getDefaultValueAsAssignableFriendly());

        final ConfigDef.ConfigKey configKey2 = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.LIST, Collections.emptyList(),
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField configField2 = ConfigField
                .fromConfigKey(configKey2)
                .isRequired()
                .build();

        assertNull(configField2.getDefaultValueAsAssignableFriendly());
    }

    @Test
    void testWithValidStrings() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.STRING, "test_valid_string_2",
                ConfigDef.ValidString.in("test_valid_string_1", "test_valid_string_2"), ConfigDef.Importance.MEDIUM, "testing",
                "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = ConfigField
                .fromConfigKey(configKey)
                .isRequired()
                .build();

        assertEquals(Arrays.asList("test_valid_string_1", "test_valid_string_2"), connectorConfigField.getValidEnumsStrings());

        final ConfigDef.ConfigKey configKeyList = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.LIST, "test_valid_string_2",
                ConfigDef.ValidList.in("test_valid_string_1", "test_valid_string_2"), ConfigDef.Importance.MEDIUM, "testing",
                "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigFieldList = ConfigField
                .fromConfigKey(configKeyList)
                .isRequired()
                .build();

        assertEquals(Arrays.asList("test_valid_string_1", "test_valid_string_2"),
                connectorConfigFieldList.getValidEnumsStrings());
    }

    @Test
    void testSecurityOption() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "ssl.field.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = ConfigField
                .fromConfigKey(configKey)
                .isRequired()
                .withDefaultValue("I am overriden")
                .build();

        assertTrue(connectorConfigField.isSecurityType());

        final ConfigDef.ConfigKey configKey2 = new ConfigDef.ConfigKey(
                "field.security.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField2 = ConfigField
                .fromConfigKey(configKey2)
                .isRequired()
                .withDefaultValue("I am overriden")
                .build();

        assertTrue(connectorConfigField2.isSecurityType());
    }

    @Test
    void testWithLabelsOption() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "ssl.field.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = ConfigField
                .fromConfigKey(configKey)
                .isRequired()
                .withLabels("test_label", "second_label")
                .build();

        final Set<String> expectedSet = new LinkedHashSet<>();
        expectedSet.add("test_label");
        expectedSet.add("second_label");
        expectedSet.add("security");

        assertEquals(expectedSet, connectorConfigField.getLabels());
    }
}
