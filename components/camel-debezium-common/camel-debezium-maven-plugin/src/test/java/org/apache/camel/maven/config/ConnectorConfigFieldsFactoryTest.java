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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectorConfigFieldsFactoryTest {

    @Test
    public void testIfCreatesFieldsMapWithDeprecatedFields() {
        final ConfigDef configDef = new ConfigDef()
                .define("test.field.1", ConfigDef.Type.STRING, ConfigDef.Importance.MEDIUM, "docs1")
                .define("test.field.2", ConfigDef.Type.CLASS, ConfigDef.Importance.MEDIUM, "docs2")
                .define("test.field.3", ConfigDef.Type.PASSWORD, ConfigDef.Importance.MEDIUM, "doc3")
                .define("test.field.4", ConfigDef.Type.INT, ConfigDef.Importance.MEDIUM, "doc4");

        final Set<String> deprecatedFields = new HashSet<>(Collections.singletonList("test.field.2"));
        final Set<String> requiredFields = new HashSet<>(Collections.singletonList("test.field.1"));
        final Map<String, Object> overridenFields = Collections.singletonMap("test.field.1", "I am overriden");

        final Map<String, ConnectorConfigField> connectorConfigToField = ConnectorConfigFieldsFactory.createConnectorFieldsAsMap(
                configDef, deprecatedFields, requiredFields, overridenFields);

        assertEquals(4, connectorConfigToField.size());

        final ConnectorConfigField connectorConfigField1 = connectorConfigToField.get("test.field.1");

        assertEquals("testField1", connectorConfigField1.getFieldName());
        assertEquals("I am overriden", connectorConfigField1.getDefaultValue());
        assertTrue(connectorConfigField1.isRequired());
        assertFalse(connectorConfigField1.isDeprecated());

        final ConnectorConfigField connectorConfigField2 = connectorConfigToField.get("test.field.2");
        assertFalse(connectorConfigField2.isRequired());
        assertTrue(connectorConfigField2.isDeprecated());
    }

}
