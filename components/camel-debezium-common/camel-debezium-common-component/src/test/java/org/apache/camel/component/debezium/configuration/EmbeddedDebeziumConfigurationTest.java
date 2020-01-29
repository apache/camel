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

import java.util.Collections;

import io.debezium.config.Configuration;
import io.debezium.embedded.EmbeddedEngine;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.junit.Test;

import static org.junit.Assert.*;

public class EmbeddedDebeziumConfigurationTest {

    @Test
    public void testIfCreatesConfig() {
        final TestEmbeddedDebeziumConfiguration configuration = new TestEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setOffsetStorageReplicationFactor(2);
        configuration.setTestField("test_field");

        final Configuration dbzEmbeddedConfiguration = configuration.createDebeziumConfiguration();

        assertEquals("Expect the same name", "test_config",
                dbzEmbeddedConfiguration.getString(EmbeddedEngine.ENGINE_NAME));
        assertEquals(2, dbzEmbeddedConfiguration
                .getInteger(EmbeddedEngine.OFFSET_STORAGE_KAFKA_REPLICATION_FACTOR));
        assertEquals(DebeziumConstants.DEFAULT_OFFSET_STORAGE,
                dbzEmbeddedConfiguration.getString(EmbeddedEngine.OFFSET_STORAGE));
        assertEquals("test_field", configuration.getTestField());
        assertEquals(Class.class.getName(), dbzEmbeddedConfiguration.getString(EmbeddedEngine.CONNECTOR_CLASS));
    }

    @Test
    public void testIfValidatesConfigurationCorrectly() {
        final TestEmbeddedDebeziumConfiguration configuration = new TestEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setOffsetStorageReplicationFactor(2);
        configuration.setOffsetStorageFileName("/file");

        // not all required fields being set
        assertFalse(configuration.validateConfiguration().isValid());

        // all required fields being set
        configuration.setTestField("test_field");

        assertTrue(configuration.validateConfiguration().isValid());
    }

    @Test
    public void testIfCreatesAdditionalProperties() {
        final TestEmbeddedDebeziumConfiguration configuration = new TestEmbeddedDebeziumConfiguration();
        configuration.setName("test_config");
        configuration.setAdditionalProperties(Collections.singletonMap("test.additional", "test_additional"));
        configuration.setTestField("test_field");

        final Configuration dbzEmbeddedConfiguration = configuration.createDebeziumConfiguration();

        assertEquals("test_config", dbzEmbeddedConfiguration.getString("name"));
        assertEquals("test_additional", dbzEmbeddedConfiguration.getString("test.additional"));
    }

}
