package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Arrays;
import java.util.Collections;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFieldTest {

    @Test
    void testIfReturnsCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = new ConfigField(configKey, false, true, "I am overriden", null);

        assertEquals("fieldTest", connectorConfigField.getFieldName());
        assertEquals("field.test", connectorConfigField.getName());
        assertEquals("setFieldTest", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getFieldTest", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertEquals("I am overriden", connectorConfigField.getDefaultValue());
        assertEquals("\"I am overriden\"", connectorConfigField.getDefaultValueAsString());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
        assertFalse(connectorConfigField.isTimeField());
        assertTrue(connectorConfigField.getValidStrings().isEmpty());

        final ConfigDef.ConfigKey configKeyBool = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.BOOLEAN, true,
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigFieldBool = new ConfigField(configKeyBool, false, true, null, null);

        assertEquals("fieldTest", connectorConfigFieldBool.getFieldName());
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

        final ConfigField connectorConfigField = new ConfigField(configKey, false, true, null, null);

        assertEquals("fieldTestUnderscore", connectorConfigField.getFieldName());
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

        final ConfigField connectorConfigField = new ConfigField(configKey, false, true, null, null);

        assertTrue(connectorConfigField.isTimeField());

        final ConfigDef.ConfigKey configKey2 = new ConfigDef.ConfigKey(
                "field.test_underscore.ms", ConfigDef.Type.INT, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField2 = new ConfigField(configKey2, false, true, null, null);

        assertTrue(connectorConfigField2.isTimeField());

        final ConfigDef.ConfigKey configKey3 = new ConfigDef.ConfigKey(
                "field", ConfigDef.Type.INT, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField3 = new ConfigField(configKey3, false, true, null, null);

        assertFalse(connectorConfigField3.isTimeField());

        final ConfigDef.ConfigKey configKey4 = new ConfigDef.ConfigKey(
                "field.ms.field", ConfigDef.Type.LONG, "100",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField4 = new ConfigField(configKey4, false, true, null, null);

        assertFalse(connectorConfigField4.isTimeField());
    }

    @Test
    void testIfOverrideProperty() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = new ConfigField(configKey, false, true, "I am overriden", "emptyField");

        assertEquals("field.test", connectorConfigField.getName());
        assertEquals("emptyField", connectorConfigField.getFieldName());
        assertEquals("setEmptyField", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getEmptyField", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertEquals("I am overriden", connectorConfigField.getDefaultValue());
        assertEquals("\"I am overriden\"", connectorConfigField.getDefaultValueAsString());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
        assertFalse(connectorConfigField.isTimeField());
    }

    @Test
    void testWithValidStrings() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.STRING, "test_valid_string_2",
                ConfigDef.ValidString.in("test_valid_string_1", "test_valid_string_2"), ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigField = new ConfigField(configKey, false, true, null, null);

        assertEquals(Arrays.asList("test_valid_string_1", "test_valid_string_2"), connectorConfigField.getValidStrings());

        final ConfigDef.ConfigKey configKeyList = new ConfigDef.ConfigKey(
                "field.test", ConfigDef.Type.LIST, "test_valid_string_2",
                ConfigDef.ValidList.in("test_valid_string_1", "test_valid_string_2"), ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName",
                Collections.emptyList(),
                null, false);

        final ConfigField connectorConfigFieldList = new ConfigField(configKeyList, false, true, null, null);

        assertEquals(Arrays.asList("test_valid_string_1", "test_valid_string_2"), connectorConfigFieldList.getValidStrings());

    }
}