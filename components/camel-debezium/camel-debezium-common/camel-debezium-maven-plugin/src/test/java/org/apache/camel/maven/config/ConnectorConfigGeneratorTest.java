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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.connector.mysql.MySqlConnector;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.relational.RelationalDatabaseConnectorConfig;
import io.debezium.storage.file.history.FileSchemaHistory;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.connect.source.SourceConnector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConnectorConfigGeneratorTest {

    @Test
    void testIfCorrectlyGeneratedMySQLFile() {
        final Set<String> requiredFields = new HashSet<>(
                Arrays.asList(MySqlConnectorConfig.PASSWORD.name(), RelationalDatabaseConnectorConfig.TOPIC_PREFIX.name()));
        final Map<String, Object> overrideFields = new HashMap<>();
        overrideFields.put(MySqlConnectorConfig.SCHEMA_HISTORY.name(), FileSchemaHistory.class);
        overrideFields.put(CommonConnectorConfig.TOMBSTONES_ON_DELETE.name(), false);
        overrideFields.put(MySqlConnectorConfig.SERVER_ID.name(), 1111);

        testIfCorrectlyGeneratedFile(new MySqlConnector(), MySqlConnectorConfig.class, requiredFields, overrideFields);
    }

    @Test
    void testIfIgnoreUnWantedFieldsWithIllegal() {
        final Map<String, ConnectorConfigField> fields = ConnectorConfigFieldsFactory.createConnectorFieldsAsMap(
                new MySqlConnector().config(), MySqlConnectorConfig.class, Collections.EMPTY_SET, Collections.EMPTY_MAP);

        fields.forEach((name, connectorConfigField) -> assertFalse(
                StringUtils.containsAny(name, "%", "+", "[", "]", "*", "(", ")", "ˆ", "@", "%", "~"),
                "Illegal char found in the field name"));
    }

    @Test
    void testIfItHandlesWrongClassInput() {
        final MySqlConnector connector = new MySqlConnector();
        final Map<String, Object> overridenDefaultValues = Collections.emptyMap();
        final Set<String> requiredFields = Collections.emptySet();

        Class<?> clazz = getClass();

        assertThrows(IllegalArgumentException.class,
                () -> ConnectorConfigGenerator.create(connector, clazz, requiredFields, overridenDefaultValues));
    }

    private void testIfCorrectlyGeneratedFile(
            final SourceConnector connector, final Class<?> configClass, final Set<String> requiredFields,
            final Map<String, Object> overrideFields) {
        final ConnectorConfigGenerator connectorConfigGenerator
                = ConnectorConfigGenerator.create(connector, configClass, requiredFields, overrideFields);
        final Map<String, ConnectorConfigField> connectorConfigFields = ConnectorConfigFieldsFactory
                .createConnectorFieldsAsMap(connector.config(), configClass, requiredFields, overrideFields);

        final String connectorFieldsAsString = connectorConfigGenerator.printClassAsString();

        assertNotNull(connectorFieldsAsString);

        // check if we have all fields
        connectorConfigFields.forEach((name, field) -> {
            if (!field.isDeprecated() && !field.isInternal()) {
                // check fields names
                assertTrue(connectorFieldsAsString.contains(field.getFieldName()), field.getFieldName());

                // check setters
                assertTrue(connectorFieldsAsString.contains(field.getFieldSetterMethodName()),
                        field.getFieldSetterMethodName());

                // check getters
                assertTrue(connectorFieldsAsString.contains(field.getFieldGetterMethodName()),
                        field.getFieldGetterMethodName());
            }
        });
    }
}
