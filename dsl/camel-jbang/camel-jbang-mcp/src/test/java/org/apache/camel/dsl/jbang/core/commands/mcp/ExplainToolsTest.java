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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExplainToolsTest {

    private ExplainTools createTools() {
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.empty();

        ExplainTools tools = new ExplainTools();
        tools.catalogService = catalogService;
        return tools;
    }

    @Test
    void resultDoesNotEchoInputRoute() throws Exception {
        ExplainTools tools = createTools();
        // Distinctive markers that would only appear if the input was echoed back.
        String marker = "MARKER-CAMEL-23474-payload";
        String route = "- route:\n    from:\n      uri: timer:" + marker + "\n      steps:\n"
                       + "        - log: '${body}'\n";

        ExplainTools.RouteContextResult result
                = tools.camel_route_context(route, "yaml", null, null, null);

        // RouteContextResult must not carry a 'route' record component (CAMEL-23474).
        assertThat(Arrays.stream(ExplainTools.RouteContextResult.class.getRecordComponents())
                .map(RecordComponent::getName))
                .as("RouteContextResult must not echo input route")
                .doesNotContain("route");

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(result);

        // Distinctive input markers must not appear in the response payload.
        assertThat(json).doesNotContain(marker);
        assertThat(json).contains("\"format\":\"yaml\"");
    }

    @Test
    void blankRouteThrows() {
        ExplainTools tools = createTools();

        assertThatThrownBy(() -> tools.camel_route_context("", "yaml", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Route content is required");
    }

    @Test
    void extractsKnownComponent() {
        ExplainTools tools = createTools();
        String route = "- route:\n    from:\n      uri: timer:tick\n      steps:\n        - log: '${body}'\n";

        ExplainTools.RouteContextResult result
                = tools.camel_route_context(route, "yaml", null, null, null);

        assertThat(result.components())
                .as("timer component should be detected")
                .anyMatch(c -> "timer".equals(c.name()));
    }
}
