package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFieldsBuilderTest {

    @Test
    void testIfCreatesFieldsMap() {
        final ConfigDef configDef = new ConfigDef()
                .define("test.field.1", ConfigDef.Type.STRING, ConfigDef.Importance.MEDIUM, "docs1")
                .define("test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("test.field.3", ConfigDef.Type.PASSWORD, ConfigDef.Importance.MEDIUM, "doc3")
                .define("test.field.4", ConfigDef.Type.INT, ConfigDef.Importance.MEDIUM, "doc4")
                .define("test.field.5", ConfigDef.Type.INT, ConfigDef.Importance.MEDIUM, "doc5");

        final Set<String> deprecatedFields = new HashSet<>(Collections.singletonList("test.field.2"));
        final Set<String> requiredFields = new HashSet<>(Collections.singletonList("test.field.1"));
        final Set<String> skippedFields = new HashSet<>(Collections.singletonList("test.field.5"));
        final Map<String, Object> overriddenFields = Collections.singletonMap("test.field.1", "I am overridden");
        final Map<String, String> overriddenVariableNames = Collections.singletonMap("test.field.4", "overriddenVariable");

        final Map<String, ConfigField> configFields = new ConfigFieldsBuilder()
                .setConfigs(configDef.configKeys())
                .setDeprecatedFields(deprecatedFields)
                .setRequiredFields(requiredFields)
                .setSkippedFields(skippedFields)
                .setOverriddenDefaultValues(overriddenFields)
                .setOverriddenVariableNames(overriddenVariableNames)
                .build();

        assertEquals(4, configFields.size());
        assertFalse(configFields.containsKey("test.field.5"));

        final ConfigField configField1 = configFields.get("test.field.1");

        assertEquals("testField1", configField1.getVariableName());
        assertEquals("I am overridden", configField1.getDefaultValue());
        assertTrue(configField1.isRequired());
        assertFalse(configField1.isDeprecated());

        final ConfigField configField2 = configFields.get("test.field.2");
        assertFalse(configField2.isRequired());
        assertTrue(configField2.isDeprecated());

        final ConfigField configField3 = configFields.get("test.field.4");
        assertEquals("test.field.4", configField3.getName());
        assertEquals("overriddenVariable", configField3.getVariableName());
        assertEquals("setOverriddenVariable", configField3.getFieldSetterMethodName());

    }

}
