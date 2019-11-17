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
import io.debezium.relational.history.FileDatabaseHistory;
import org.apache.kafka.connect.source.SourceConnector;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConnectorConfigGeneratorTest {

    @Test
    public void testIfCorrectlyGeneratedMySQLFile() {
        final Set<String> requiredFields = new HashSet<>(Arrays.asList(MySqlConnectorConfig.PASSWORD.name(), RelationalDatabaseConnectorConfig.SERVER_NAME.name()));
        final Map<String, Object> overrideFields = new HashMap<>();
        overrideFields.put(MySqlConnectorConfig.DATABASE_HISTORY.name(), FileDatabaseHistory.class);
        overrideFields.put(CommonConnectorConfig.TOMBSTONES_ON_DELETE.name(), false);
        overrideFields.put(MySqlConnectorConfig.SERVER_ID.name(), 1111);

        testIfCorrectlyGeneratedFile(new MySqlConnector(), MySqlConnectorConfig.class, requiredFields, overrideFields);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfItHandlesWrongClassInput() {
        final ConnectorConfigGenerator connectorConfigGenerator = ConnectorConfigGenerator.create(new MySqlConnector(), getClass(), Collections.emptySet(), Collections.emptyMap());
    }

    private void testIfCorrectlyGeneratedFile(final SourceConnector connector, final Class<?> configClass, final Set<String> requiredFields, final Map<String, Object> overrideFields) {
        final ConnectorConfigGenerator connectorConfigGenerator = ConnectorConfigGenerator.create(connector, configClass, requiredFields, overrideFields);
        final Map<String, ConnectorConfigField> connectorConfigFields = ConnectorConfigFieldsFactory.createConnectorFieldsAsMap(connector.config(), configClass, requiredFields, overrideFields);

        final String connectorFieldsAsString = connectorConfigGenerator.printClassAsString();

        assertNotNull(connectorFieldsAsString);

        // check if we have all fields
        connectorConfigFields.forEach((name, field) -> {
            if (!field.isDeprecated() && !field.isInternal()) {
                // check fields names
                assertTrue(field.getFieldName(), connectorFieldsAsString.contains(field.getFieldName()));

                // check setters
                assertTrue(field.getFieldSetterMethodName(), connectorFieldsAsString.contains(field.getFieldSetterMethodName()));

                // check getters
                assertTrue(field.getFieldGetterMethodName(), connectorFieldsAsString.contains(field.getFieldGetterMethodName()));
            }
        });
    }
}
