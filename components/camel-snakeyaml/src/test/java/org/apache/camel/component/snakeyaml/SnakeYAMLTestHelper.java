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
package org.apache.camel.component.snakeyaml;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.snakeyaml.model.TestPojo;
import org.yaml.snakeyaml.nodes.Tag;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class SnakeYAMLTestHelper {

    protected SnakeYAMLTestHelper() {
    }

    public static TestPojo createTestPojo() {
        return new TestPojo("Camel");
    }

    public static Map<String, String> createTestMap() {
        Map<String, String> map = new HashMap<>();
        map.put("name", "Camel");

        return map;
    }

    public static SnakeYAMLDataFormat createDataFormat(final Class<?> type) {
        SnakeYAMLDataFormat format = new SnakeYAMLDataFormat();
        if (type != null) {
            format.setUnmarshalType(type);
        }

        return format;
    }

    public static SnakeYAMLDataFormat createPrettyFlowDataFormat(Class<?> type, boolean prettyFlow) {
        SnakeYAMLDataFormat format = createDataFormat(type);
        format.setPrettyFlow(prettyFlow);

        return format;
    }

    public static SnakeYAMLDataFormat createClassTagDataFormat(Class<?> type, Tag tag) {
        SnakeYAMLDataFormat format = createDataFormat(type);
        format.addTag(type, tag);

        return format;
    }

    public static void marshalAndUnmarshal(
            CamelContext context, Object body, String mockName, String directIn, String directBack, String expected)
            throws Exception {

        MockEndpoint mock = context.getEndpoint(mockName, MockEndpoint.class);
        assertNotNull(mock);

        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(body.getClass());
        mock.message(0).body().isEqualTo(body);

        ProducerTemplate template = context.createProducerTemplate();
        String result = assertDoesNotThrow(() -> template.requestBody(directIn, body, String.class));
        assertNotNull(result);
        assertEquals(expected, result.trim());

        assertDoesNotThrow(() -> template.sendBody(directBack, result));

        mock.assertIsSatisfied();
    }
}
