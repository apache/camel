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

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DebeziumComponentTest extends CamelTestSupport {

    @Test
    public void testIfSetsAdditionalProperties() throws Exception {
        final DebeziumTestComponent component = new DebeziumTestComponent(context);
        final FileConnectorEmbeddedDebeziumConfiguration configuration = new FileConnectorEmbeddedDebeziumConfiguration();

        final Map<String, Object> params = new HashMap<>();
        params.put("extra.1", 789);
        params.put("extra.3", "test.extra.3");

        configuration.setOffsetStorageFileName("/test");
        configuration.setTestFilePath(Paths.get("."));
        configuration.setTopicConfig("test_conf");
        configuration.setAdditionalProperties(params);

        component.setConfiguration(configuration);

        final DebeziumTestEndpoint endpoint = (DebeziumTestEndpoint) component.createEndpoint("debezium-dummy:test_name?additionalProperties.extra.1=123&additionalProperties.extra.2=test");

        assertEquals("test_name", endpoint.getConfiguration().getName());
        assertEquals("test_conf", endpoint.getConfiguration().getTopicConfig());
        assertEquals("123", endpoint.getConfiguration().getAdditionalProperties().get("extra.1"));
        assertEquals("test", endpoint.getConfiguration().getAdditionalProperties().get("extra.2"));
        assertEquals("test.extra.3", endpoint.getConfiguration().getAdditionalProperties().get("extra.3"));
    }
}
