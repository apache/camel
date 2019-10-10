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

import org.apache.camel.component.debezium.configuration.MySqlConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.component.debezium.configuration.TestEmbeddedDebeziumConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DebeziumComponentTest {

    @Test
    public void testIfMySqlEndpointCreatedWithConfig() throws Exception {
        final Map<String, Object> params = new HashMap<>();
        params.put("name", "test_name");
        params.put("offsetStorageFileName", "/offset_test_file");
        params.put("databaseHostName", "localhost");
        params.put("databaseUser", "dbz");
        params.put("databasePassword", "pwd");
        params.put("databaseServerName", "test");
        params.put("databaseServerId", 1234);
        params.put("databaseHistoryFileName", "/db_history_file_test");

        final String remaining = "mysql";
        final String uri = "debezium:mysql?name=test_name&offsetStorageFileName=/test&"
                           + "databaseHostName=localhost&databaseServerId=1234&databaseUser=dbz&databasePassword=pwd&"
                           + "databaseServerName=test&databaseHistoryFileName=/test";

        final DebeziumComponent debeziumComponent = new DebeziumComponent(new DefaultCamelContext());
        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining, params);

        assertNotNull(debeziumEndpoint);

        // test for config
        final MySqlConnectorEmbeddedDebeziumConfiguration configuration = (MySqlConnectorEmbeddedDebeziumConfiguration)debeziumEndpoint
            .getConfiguration();
        assertEquals("test_name", configuration.getName());
        assertEquals("/offset_test_file", configuration.getOffsetStorageFileName());
        assertEquals("localhost", configuration.getDatabaseHostName());
        assertEquals("dbz", configuration.getDatabaseUser());
        assertEquals("pwd", configuration.getDatabasePassword());
        assertEquals("test", configuration.getDatabaseServerName());
        assertEquals(1234, configuration.getDatabaseServerId());
        assertEquals("/db_history_file_test", configuration.getDatabaseHistoryFileName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfHandlesInvalidConnectorType() throws Exception {
        final Map<String, Object> params = new HashMap<>();
        params.put("name", "test_name");
        params.put("offsetStorageFileName", "/offset_test_file");
        params.put("databaseHostName", "localhost");
        params.put("databaseUser", "dbz");
        params.put("databasePassword", "pwd");
        params.put("databaseServerName", "test");
        params.put("databaseServerId", 1234);
        params.put("databaseHistoryFileName", "/db_history_file_test");

        final String remaining = "dummy";
        final String uri = "debezium:dummy?name=test_name&offsetStorageFileName=/test&"
                           + "databaseHostName=localhost&databaseServerId=1234&databaseUser=dbz&databasePassword=pwd&"
                           + "databaseServerName=test&databaseHistoryFileName=/test";

        final DebeziumComponent debeziumComponent = new DebeziumComponent(new DefaultCamelContext());
        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining, params);
    }

    @Test
    public void testIfCreatesComponentWithExternalConfiguration() throws Exception {
        final TestEmbeddedDebeziumConfiguration configuration = new TestEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setOffsetStorageReplicationFactor(2);
        configuration.setTestField("test_field");
        configuration.setOffsetStorageFileName("/file");

        // the component should ignore the type in the remaining as long you set valid
        // configurations
        final String remaining = "dummy";
        final String uri = "debezium:dummy";
        final DebeziumComponent debeziumComponent = new DebeziumComponent(new DefaultCamelContext());

        // set configurations
        debeziumComponent.setConfiguration(configuration);

        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining,
                                                                                   Collections.emptyMap());

        assertNotNull(debeziumEndpoint);

        // assert configurations
        final TestEmbeddedDebeziumConfiguration actualConfigurations = (TestEmbeddedDebeziumConfiguration)debeziumEndpoint
            .getConfiguration();
        assertNotNull(actualConfigurations);
        assertEquals(configuration.getTestField(), actualConfigurations.getTestField());
        assertEquals(configuration.getOffsetStorageReplicationFactor(),
                     actualConfigurations.getOffsetStorageReplicationFactor());
        assertEquals(configuration.getName(), actualConfigurations.getName());
        assertEquals(configuration.getConnectorClass(), actualConfigurations.getConnectorClass());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfItHandlesNullExternalConfigurations() throws Exception {
        final String remaining = "";
        final String uri = "debezium:";
        final DebeziumComponent debeziumComponent = new DebeziumComponent(new DefaultCamelContext());

        // set configurations
        debeziumComponent.setConfiguration(null);

        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining,
                                                                                   Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfItHandlesNullExternalConfigurationsWithValidUri() throws Exception {
        final String remaining = "dummy";
        final String uri = "debezium:dummy";
        final DebeziumComponent debeziumComponent = new DebeziumComponent(new DefaultCamelContext());

        // set configurations
        debeziumComponent.setConfiguration(null);

        final DebeziumEndpoint debeziumEndpoint = debeziumComponent.createEndpoint(uri, remaining,
                                                                                   Collections.emptyMap());
    }
}
