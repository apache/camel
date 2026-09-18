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
package org.apache.camel.component.mcp.server;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerBridgePrefixTagTest extends CamelTestSupport {

    private final RecordingMcpServerEngine engine = new RecordingMcpServerEngine();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("crm*,notify");
        camelContext.addService(new McpServerBridge(configuration));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:tool_a?tags=crm&description=CRM tool")
                        .setBody(constant("a"));

                from("ai-tool:tool_b?tags=crm-sales&description=CRM sales tool")
                        .setBody(constant("b"));

                from("ai-tool:tool_c?tags=notify&description=Notify tool")
                        .setBody(constant("c"));

                from("ai-tool:tool_d?tags=billing&description=Billing tool")
                        .setBody(constant("d"));

                from("ai-tool:hidden?description=Untagged tool")
                        .setBody(constant("hidden"));
            }
        };
    }

    @Test
    void testPrefixPatternMatchesTags() {
        assertThat(engine.tools())
                .containsKeys("tool_a", "tool_b", "tool_c")
                .doesNotContainKeys("tool_d", "hidden");
    }
}
