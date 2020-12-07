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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                .fromConfigKeys(configDef.configKeys())
                .addConfig(ConfigField.withName("additional.test.field.1").withType(ConfigDef.Type.STRING)
                        .withDocumentation("docs1").build())
                .addConfig(ConfigField.withName("additional.test.field.2").withType(ConfigDef.Type.CLASS)
                        .withDocumentation("docs2").build())
                .setDeprecatedFields(deprecatedFields)
                .setRequiredFields(requiredFields)
                .setSkippedFields(skippedFields)
                .setOverriddenDefaultValues(overriddenFields)
                .setOverriddenVariableNames(overriddenVariableNames)
                .build();

        assertEquals(6, configFields.size());
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
