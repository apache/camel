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
package org.apache.camel.component.ai.tool;

import java.util.Map;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24831 "guard by construction": an {@code authorizationPolicy} set on the ai-tool component guards every tool
 * route, and a per-endpoint {@code authorizationPolicy} overrides it.
 */
class AiToolComponentAuthorizationPolicyTest extends CamelTestSupport {

    static final class SubjectPolicy implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return exchange -> {
                if (!"alice".equals(exchange.getProperty("subject", String.class))) {
                    throw new CamelAuthorizationException("caller is not authorized", exchange);
                }
                processor.process(exchange);
            };
        }
    }

    static final class AllowAll implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor::process;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getRegistry().bind("allowAll", new AllowAll());
        // set the policy on the component, so it guards every tool route by construction
        AiToolComponent component = ctx.getComponent("ai-tool", AiToolComponent.class);
        component.getConfiguration().setAuthorizationPolicy(new SubjectPolicy());
        return ctx;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:inherits?tags=t&description=Inherits the component policy")
                        .setBody(constant("ok"));
                from("ai-tool:overrides?tags=t&description=Overrides&authorizationPolicy=#allowAll")
                        .setBody(constant("ok"));
            }
        };
    }

    @Test
    void componentPolicyGuardsRoutesWithoutAnEndpointOption() {
        AiToolSpec spec = findSpec("inherits");

        Exchange bob = new DefaultExchange(context);
        bob.setProperty("subject", "bob");
        assertThat(AiToolExecutor.execute(spec, Map.of(), bob))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);

        Exchange alice = new DefaultExchange(context);
        alice.setProperty("subject", "alice");
        assertThat(AiToolExecutor.execute(spec, Map.of(), alice))
                .isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void endpointPolicyOverridesTheComponentPolicy() {
        // bob would be denied by the component policy, but the endpoint overrides with allow-all
        AiToolSpec spec = findSpec("overrides");
        Exchange bob = new DefaultExchange(context);
        bob.setProperty("subject", "bob");
        assertThat(AiToolExecutor.execute(spec, Map.of(), bob))
                .isInstanceOf(AiToolResult.Success.class);
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("t").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
