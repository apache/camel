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

import java.util.Collections;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectorConfigFieldTest {

    @Test
    public void testIfReturnsCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey("field.test", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName", Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField = new ConnectorConfigField(configKey, false, true, "I am overriden");

        assertEquals("fieldTest", connectorConfigField.getFieldName());
        assertEquals("setFieldTest", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getFieldTest", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertEquals("I am overriden", connectorConfigField.getDefaultValue());
        assertEquals("\"I am overriden\"", connectorConfigField.getDefaultValueAsString());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
    }

    @Test
    public void testIfHandlesUnderscoreFieldsCorrectly() {
        final ConfigDef.ConfigKey configKey = new ConfigDef.ConfigKey("field.test_underscore", ConfigDef.Type.STRING, "empty",
                null, ConfigDef.Importance.MEDIUM, "testing", "testGroup", 1, ConfigDef.Width.MEDIUM, "displayName", Collections.emptyList(),
                null, false);

        final ConnectorConfigField connectorConfigField = new ConnectorConfigField(configKey, false, true, null);

        assertEquals("fieldTestUnderscore", connectorConfigField.getFieldName());
        assertEquals("setFieldTestUnderscore", connectorConfigField.getFieldSetterMethodName());
        assertEquals("getFieldTestUnderscore", connectorConfigField.getFieldGetterMethodName());
        assertEquals(String.class, connectorConfigField.getRawType());
        assertFalse(connectorConfigField.isDeprecated());
        assertTrue(connectorConfigField.isRequired());
    }
}
