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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MCP engine accepts clients before the routes start, so all tools must be published before any route consumer
 * starts, otherwise an early {@code tools/list} returns a partial list.
 */
class McpServerBridgeStartupOrderTest extends CamelTestSupport {

    private final RecordingMcpServerEngine engine = new RecordingMcpServerEngine();
    private final List<String> seenByFirstConsumer = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("crm");
        camelContext.addService(new McpServerBridge(configuration));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // declared first: its consumer starts before the ai-tool consumers
                from(new ProbeEndpoint(getContext())).routeId("probe").log("probe");

                from("ai-tool:query_db?tags=crm&description=Query the database&parameter.id=string")
                        .setBody(constant("ok"));
            }
        };
    }

    @Test
    void toolsArePublishedBeforeTheFirstRouteConsumerStarts() {
        assertThat(seenByFirstConsumer).containsExactly("query_db");
    }

    private final class ProbeEndpoint extends DefaultEndpoint {

        ProbeEndpoint(CamelContext camelContext) {
            super("probe://first", null);
            setCamelContext(camelContext);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
                @Override
                protected void doStart() throws Exception {
                    super.doStart();
                    seenByFirstConsumer.addAll(engine.tools().keySet());
                }
            };
        }
    }
}
