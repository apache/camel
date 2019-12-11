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

import org.apache.camel.component.debezium.configuration.SqlServerConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DebeziumSqlserverComponentTest {

    @Test
    public void testIfConnectorEndpointCreatedWithConfig() throws Exception {
        final Map<String, Object> params = new HashMap<>();
        params.put("offsetStorageFileName", "/offset_test_file");
        params.put("databaseHostname", "localhost");
        params.put("databaseUser", "dbz");
        params.put("databasePassword", "pwd");
        params.put("databaseServerName", "test");
        params.put("databaseServerId", 1234);
        params.put("databaseHistoryFileFilename", "/db_history_file_test");

        final String remaining = "test_name";
        final String uri = "debezium?name=test_name&offsetStorageFileName=/test&"
                + "databaseHostName=localhost&databaseServerId=1234&databaseUser=dbz&databasePassword=pwd&"
                + "databaseServerName=test&databaseHistoryFileName=/test";

        final DebeziumComponent debeziumComponent = new DebeziumSqlserverComponent(new DefaultCamelContext());
        debeziumComponent.start();
        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining, params);

        assertNotNull(debeziumEndpoint);

        // test for config
        final SqlServerConnectorEmbeddedDebeziumConfiguration configuration = (SqlServerConnectorEmbeddedDebeziumConfiguration)debeziumEndpoint
                .getConfiguration();
        assertEquals("test_name", configuration.getName());
        assertEquals("/offset_test_file", configuration.getOffsetStorageFileName());
        assertEquals("localhost", configuration.getDatabaseHostname());
        assertEquals("dbz", configuration.getDatabaseUser());
        assertEquals("pwd", configuration.getDatabasePassword());
        assertEquals("test", configuration.getDatabaseServerName());
        assertEquals("/db_history_file_test", configuration.getDatabaseHistoryFileFilename());
    }

    @Test
    public void testIfCreatesComponentWithExternalConfiguration() throws Exception {
        final SqlServerConnectorEmbeddedDebeziumConfiguration configuration = new SqlServerConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setDatabaseUser("test_db");
        configuration.setDatabasePassword("pwd");
        configuration.setOffsetStorageFileName("/offset/file");
        configuration.setDatabaseServerName("test");

        final String uri = "debezium:dummy";
        final DebeziumComponent debeziumComponent = new DebeziumSqlserverComponent(new DefaultCamelContext());
        debeziumComponent.start();

        // set configurations
        debeziumComponent.setConfiguration(configuration);

        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, null,
                Collections.emptyMap());

        assertNotNull(debeziumEndpoint);

        // assert configurations
        final SqlServerConnectorEmbeddedDebeziumConfiguration actualConfigurations = (SqlServerConnectorEmbeddedDebeziumConfiguration)debeziumEndpoint
                .getConfiguration();
        assertNotNull(actualConfigurations);
        assertEquals(configuration.getName(), actualConfigurations.getName());
        assertEquals(configuration.getDatabaseUser(),
                actualConfigurations.getDatabaseUser());
        assertEquals(configuration.getConnectorClass(), actualConfigurations.getConnectorClass());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfItHandlesNullExternalConfigurations() throws Exception {
        final String remaining = "";
        final String uri = "debezium:";
        final DebeziumComponent debeziumComponent = new DebeziumSqlserverComponent(new DefaultCamelContext());
        debeziumComponent.start();

        // set configurations
        debeziumComponent.setConfiguration(null);

        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining,
                Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfItHandlesNullExternalConfigurationsWithValidUri() throws Exception {
        final String remaining = "dummy";
        final String uri = "debezium:dummy";
        final DebeziumComponent debeziumComponent = new DebeziumSqlserverComponent(new DefaultCamelContext());
        debeziumComponent.start();

        // set configurations
        debeziumComponent.setConfiguration(null);

        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining,
                Collections.emptyMap());
    }
}
