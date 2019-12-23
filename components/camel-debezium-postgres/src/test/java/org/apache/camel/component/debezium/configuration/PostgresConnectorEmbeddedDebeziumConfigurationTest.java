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
package org.apache.camel.component.debezium.configuration;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.Configuration;
import io.debezium.connector.postgresql.PostgresConnector;
import io.debezium.connector.postgresql.PostgresConnectorConfig;
import io.debezium.embedded.EmbeddedEngine;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.junit.Test;

import static org.junit.Assert.*;

public class PostgresConnectorEmbeddedDebeziumConfigurationTest {

    @Test
    public void testIfCreatesConfig() {
        final PostgresConnectorEmbeddedDebeziumConfiguration configuration = new PostgresConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setDatabaseUser("test_user");
        configuration.setMaxQueueSize(1212);

        final Configuration dbzConfigurations = configuration.createDebeziumConfiguration();

        assertEquals("test_config", dbzConfigurations.getString(EmbeddedEngine.ENGINE_NAME));
        assertEquals("test_user", dbzConfigurations.getString(PostgresConnectorConfig.USER));
        assertEquals(1212, dbzConfigurations.getInteger(CommonConnectorConfig.MAX_QUEUE_SIZE));
        assertEquals(PostgresConnector.class.getName(), dbzConfigurations.getString(EmbeddedEngine.CONNECTOR_CLASS));
        assertEquals(DebeziumConstants.DEFAULT_OFFSET_STORAGE,
                dbzConfigurations.getString(EmbeddedEngine.OFFSET_STORAGE));
    }

    @Test
    public void testIfValidatesConfigurationCorrectly() {
        final PostgresConnectorEmbeddedDebeziumConfiguration configuration = new PostgresConnectorEmbeddedDebeziumConfiguration();

        configuration.setName("test_config");
        configuration.setDatabaseUser("test_db");
        configuration.setDatabaseServerName("test_server");
        configuration.setOffsetStorageFileName("/offset/file");
        configuration.setDatabaseHistoryFileFilename("/database_history/file");

        assertFalse(configuration.validateConfiguration().isValid());

        configuration.setDatabaseHostname("localhost");
        configuration.setDatabasePassword("test_pwd");

        assertTrue(configuration.validateConfiguration().isValid());
    }

    @Test
    public void testValidateConfigurationsForAllRequiredFields() {
        final PostgresConnectorEmbeddedDebeziumConfiguration configuration = new PostgresConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setDatabaseUser("test_db");
        configuration.setDatabaseHostname("localhost");
        configuration.setDatabasePassword("test_pwd");
        configuration.setDatabaseServerName("test_server");
        configuration.setOffsetStorageFileName("/offset/file");
        configuration.setDatabaseHistoryFileFilename("/database_history/file");

        final ConfigurationValidation validation = configuration.validateConfiguration();
        assertTrue(validation.isValid());

        assertEquals("test_config", configuration.getName());
        assertEquals("test_db", configuration.getDatabaseUser());
        assertEquals("localhost", configuration.getDatabaseHostname());
        assertEquals("test_pwd", configuration.getDatabasePassword());
        assertEquals("test_server", configuration.getDatabaseServerName());
        assertEquals("/offset/file", configuration.getOffsetStorageFileName());
        assertEquals("/database_history/file", configuration.getDatabaseHistoryFileFilename());
    }

}
