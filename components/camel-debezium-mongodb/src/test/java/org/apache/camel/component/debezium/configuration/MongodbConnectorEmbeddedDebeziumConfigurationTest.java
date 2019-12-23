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
import io.debezium.connector.mongodb.MongoDbConnector;
import io.debezium.connector.mongodb.MongoDbConnectorConfig;
import io.debezium.embedded.EmbeddedEngine;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.junit.Test;

import static org.junit.Assert.*;

public class MongodbConnectorEmbeddedDebeziumConfigurationTest {

    @Test
    public void testIfCreatesConfig() {
        final MongoDbConnectorEmbeddedDebeziumConfiguration configuration = new MongoDbConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setMongodbUser("test_user");
        configuration.setMaxQueueSize(1212);

        final Configuration dbzConfigurations = configuration.createDebeziumConfiguration();

        assertEquals("test_config", dbzConfigurations.getString(EmbeddedEngine.ENGINE_NAME));
        assertEquals("test_user", dbzConfigurations.getString(MongoDbConnectorConfig.USER));
        assertEquals(1212, dbzConfigurations.getInteger(CommonConnectorConfig.MAX_QUEUE_SIZE));
        assertEquals(MongoDbConnector.class.getName(), dbzConfigurations.getString(EmbeddedEngine.CONNECTOR_CLASS));
        assertEquals(DebeziumConstants.DEFAULT_OFFSET_STORAGE,
                dbzConfigurations.getString(EmbeddedEngine.OFFSET_STORAGE));
    }

    @Test
    public void testIfValidatesConfigurationCorrectly() {
        final MongoDbConnectorEmbeddedDebeziumConfiguration configuration = new MongoDbConnectorEmbeddedDebeziumConfiguration();

        configuration.setName("test_config");
        configuration.setMongodbUser("test_db");
        configuration.setMongodbName("test_server");
        configuration.setOffsetStorageFileName("/offset/file");
        configuration.setDatabaseHistoryFileFilename("/database_history/file");

        assertFalse(configuration.validateConfiguration().isValid());

        configuration.setMongodbHosts("localhost");
        configuration.setMongodbPassword("test_pwd");

        assertTrue(configuration.validateConfiguration().isValid());
    }

    @Test
    public void testValidateConfigurationsForAllRequiredFields() {
        final MongoDbConnectorEmbeddedDebeziumConfiguration configuration = new MongoDbConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setMongodbUser("test_db");
        configuration.setMongodbHosts("localhost");
        configuration.setMongodbPassword("test_pwd");
        configuration.setMongodbName("test_server");
        configuration.setOffsetStorageFileName("/offset/file");
        configuration.setDatabaseHistoryFileFilename("/database_history/file");

        final ConfigurationValidation validation = configuration.validateConfiguration();
        assertTrue(validation.isValid());

        assertEquals("test_config", configuration.getName());
        assertEquals("test_db", configuration.getMongodbUser());
        assertEquals("localhost", configuration.getMongodbHosts());
        assertEquals("test_pwd", configuration.getMongodbPassword());
        assertEquals("test_server", configuration.getMongodbName());
        assertEquals("/offset/file", configuration.getOffsetStorageFileName());
        assertEquals("/database_history/file", configuration.getDatabaseHistoryFileFilename());
    }

}
