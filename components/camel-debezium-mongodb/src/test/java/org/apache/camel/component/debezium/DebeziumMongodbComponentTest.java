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
package org.apache.camel.component.debezium;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.debezium.configuration.MongoDbConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DebeziumMongodbComponentTest {

    @Test
    void testIfConnectorEndpointCreatedWithConfig() throws Exception {
        final Map<String, Object> params = new HashMap<>();
        params.put("offsetStorageFileName", "/offset_test_file");
        params.put("mongodbHosts", "localhost");
        params.put("mongodbUser", "dbz");
        params.put("mongodbPassword", "pwd");
        params.put("mongodbName", "test");
        params.put("databaseHistoryFileFilename", "/db_history_file_test");

        final String remaining = "test_name";
        final String uri = "debezium?name=test_name&offsetStorageFileName=/test&"
                           + "databaseHostName=localhost&databaseServerId=1234&databaseUser=dbz&databasePassword=pwd&"
                           + "databaseServerName=test&databaseHistoryFileName=/test";

        try (final DebeziumComponent debeziumComponent = new DebeziumMongodbComponent(new DefaultCamelContext())) {
            debeziumComponent.start();
            final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining, params);

            assertNotNull(debeziumEndpoint);

            // test for config
            final MongoDbConnectorEmbeddedDebeziumConfiguration configuration
                    = (MongoDbConnectorEmbeddedDebeziumConfiguration) debeziumEndpoint.getConfiguration();
            assertEquals("test_name", configuration.getName());
            assertEquals("/offset_test_file", configuration.getOffsetStorageFileName());
            assertEquals("localhost", configuration.getMongodbHosts());
            assertEquals("dbz", configuration.getMongodbUser());
            assertEquals("pwd", configuration.getMongodbPassword());
            assertEquals("test", configuration.getMongodbName());
            assertEquals("/db_history_file_test", configuration.getDatabaseHistoryFileFilename());
        }
    }

    @Test
    void testIfCreatesComponentWithExternalConfiguration() throws Exception {
        final MongoDbConnectorEmbeddedDebeziumConfiguration configuration = new MongoDbConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setMongodbUser("test_db");
        configuration.setMongodbPassword("pwd");
        configuration.setOffsetStorageFileName("/offset/file");
        configuration.setMongodbName("test");

        final String uri = "debezium:dummy";
        try (final DebeziumComponent debeziumComponent = new DebeziumMongodbComponent(new DefaultCamelContext())) {
            debeziumComponent.start();

            // set configurations
            debeziumComponent.setConfiguration(configuration);

            final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, null, Collections.emptyMap());

            assertNotNull(debeziumEndpoint);

            // assert configurations
            final MongoDbConnectorEmbeddedDebeziumConfiguration actualConfigurations
                    = (MongoDbConnectorEmbeddedDebeziumConfiguration) debeziumEndpoint.getConfiguration();
            assertNotNull(actualConfigurations);
            assertEquals(configuration.getName(), actualConfigurations.getName());
            assertEquals(configuration.getMongodbUser(), actualConfigurations.getMongodbUser());
            assertEquals(configuration.getConnectorClass(), actualConfigurations.getConnectorClass());
        }
    }

    @Test
    void testIfItHandlesNullExternalConfigurations() throws Exception {
        final String remaining = "";
        final String uri = "debezium:";
        try (final DebeziumComponent debeziumComponent = new DebeziumMongodbComponent(new DefaultCamelContext())) {
            debeziumComponent.start();

            // set configurations
            debeziumComponent.setConfiguration(null);

            assertThrows(IllegalArgumentException.class, () -> {
                debeziumComponent.createEndpoint(uri, remaining, Collections.emptyMap());
            });
        }
    }

    @Test
    void testIfItHandlesNullExternalConfigurationsWithValidUri() throws Exception {
        final String remaining = "dummy";
        final String uri = "debezium:dummy";
        try (final DebeziumComponent debeziumComponent = new DebeziumMongodbComponent(new DefaultCamelContext())) {
            debeziumComponent.start();

            // set configurations
            debeziumComponent.setConfiguration(null);

            assertThrows(IllegalArgumentException.class, () -> {
                debeziumComponent.createEndpoint(uri, remaining, Collections.emptyMap());
            });
        }
    }
}
