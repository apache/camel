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

class McpServerBridgeResourceTemplateTest extends CamelTestSupport {

    private final RecordingMcpServerEngine engine = new RecordingMcpServerEngine();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("crm,ops");
        camelContext.addService(new McpServerBridge(configuration));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-resource:customer_by_id?uriTemplate=crm://customers/{id}&tags=crm")
                        .routeId("customer-by-id-template-route")
                        .setBody(header("CamelAiResourceTemplateVariable.id"));

                from("ai-resource:customer_by_number?uriTemplate=crm://customers/{number}&tags=ops")
                        .routeId("customer-by-number-template-route")
                        .setBody(header("CamelAiResourceTemplateVariable.number"));
            }
        };
    }

    @Test
    void testOnlyOneOverlappingTemplateIsPublished() {
        assertThat(engine.resourceTemplates()).hasSize(1);
    }

    @Test
    void testRemainingOverlappingTemplateIsPublishedWhenAcceptedRouteStops() throws Exception {
        String acceptedTemplate = engine.resourceTemplates().keySet().iterator().next();
        String acceptedRoute = acceptedTemplate.endsWith("{id}")
                ? "customer-by-id-template-route" : "customer-by-number-template-route";
        String refusedTemplate = acceptedTemplate.endsWith("{id}")
                ? "crm://customers/{number}" : "crm://customers/{id}";

        context.getRouteController().stopRoute(acceptedRoute);

        assertThat(engine.resourceTemplates()).containsOnlyKeys(refusedTemplate);
    }
}
