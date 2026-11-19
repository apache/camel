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
package org.apache.camel.component.ai.observability;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiToolDefinitionsTest {

    @Test
    void shouldRenderFunctionToolsInTheSemanticConventionsShape() {
        String json = new GenAiToolDefinitions()
                .function("getOrderStatus", "Get the status of an order",
                        Map.of("type", "object", "properties", Map.of("orderId", Map.of("type", "string"))))
                .function("ping", null, "{\"type\":\"object\"}")
                .toJson(100);

        assertThat(json)
                .startsWith(
                        "[{\"type\":\"function\",\"name\":\"getOrderStatus\",\"description\":\"Get the status of an order\"")
                .contains("\"parameters\":{", "\"orderId\":{\"type\":\"string\"}")
                .contains("{\"type\":\"function\",\"name\":\"ping\",\"parameters\":{\"type\":\"object\"}}");
    }

    @Test
    void shouldCutTheDescriptionButKeepTheSchema() {
        String json = new GenAiToolDefinitions()
                .function("lookup", "0123456789", Map.of("type", "object", "description", "abcdefghij"))
                .toJson(4);

        assertThat(json).contains("\"description\":\"0123...[truncated]\"").contains("\"description\":\"abcdefghij\"");
    }
}
