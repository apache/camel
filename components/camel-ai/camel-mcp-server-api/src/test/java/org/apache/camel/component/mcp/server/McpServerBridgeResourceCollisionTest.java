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

/**
 * Two routes claiming the same resource uri under different tags: the second one is refused rather than silently
 * replacing the first.
 */
class McpServerBridgeResourceCollisionTest extends CamelTestSupport {

    private final RecordingMcpServerEngine engine = new RecordingMcpServerEngine();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("crm,notify");
        camelContext.addService(new McpServerBridge(configuration));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-resource:first?resourceUri=camel:///config/app.json&tags=crm&description=First")
                        .setBody(constant("first"));

                from("ai-resource:second?resourceUri=camel:///config/app.json&tags=notify&description=Second")
                        .setBody(constant("second"));
            }
        };
    }

    @Test
    void testCollidingUriIsPublishedOnce() {
        assertThat(engine.resources())
                .as("A colliding resource uri is refused, never silently replaced")
                .hasSize(1)
                .containsKey("camel:///config/app.json");
    }
}
