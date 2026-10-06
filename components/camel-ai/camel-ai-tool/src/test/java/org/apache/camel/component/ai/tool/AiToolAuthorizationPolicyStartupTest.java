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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24831: the authorization guard must hold during the window between a tool's early (warm-up) registration and
 * its consumer start. Since CAMEL-25000 the tool is published during warm-up, so a route with a lower startupOrder can
 * invoke it before the tool route's own consumer starts; the guard must already be in place by then (applied in
 * {@code prepare()} before {@code register()}), and {@code getToolProcessor()} must fail closed otherwise.
 */
class AiToolAuthorizationPolicyStartupTest extends CamelTestSupport {

    private static final AtomicReference<AiToolResult> EARLY = new AtomicReference<>();

    static final class DenyAll implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // nothing to prepare
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return exchange -> {
                throw new CamelAuthorizationException("denied", exchange);
            };
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getRegistry().bind("denyAll", new DenyAll());
        ctx.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                // the caller route has started but the ai-tool route's consumer has not: the guard must already apply
                if (event instanceof CamelEvent.RouteStartedEvent rse && "caller".equals(rse.getRoute().getRouteId())) {
                    AiToolRegistry.getOrCreate(ctx).getToolsByTag("t").stream().findFirst()
                            .ifPresent(spec -> EARLY.set(
                                    AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(ctx))));
                }
            }
        });
        return ctx;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:caller").routeId("caller").startupOrder(1).log("caller started");
                from("ai-tool:transfer?tags=t&description=Transfer&authorizationPolicy=#denyAll")
                        .routeId("tool").startupOrder(2).setBody(constant("TRANSFERRED"));
            }
        };
    }

    @Test
    void callDuringStartupMustBeDenied() {
        assertThat(EARLY.get())
                .as("a tool call during the warm-up/start window must be guarded, not fall back to the unguarded route")
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }
}
