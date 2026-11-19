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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code allowedRoles} check must hold in the window between a tool's early (warm-up) registration and the start of
 * its consumer, as an {@code authorizationPolicy} does (see {@link AiToolAuthorizationPolicyStartupTest}): a route with
 * a lower startupOrder can call the tool before the tool route's own consumer starts.
 */
class AiToolAllowedRolesStartupTest extends CamelTestSupport {

    private final AtomicReference<AiToolResult> withoutTheRole = new AtomicReference<>();
    private final AtomicReference<AiToolResult> withTheRole = new AtomicReference<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                // the caller route has started but the ai-tool route's consumer has not: the roles must already apply
                if (event instanceof CamelEvent.RouteStartedEvent rse && "caller".equals(rse.getRoute().getRouteId())) {
                    AiToolRegistry.getOrCreate(ctx).getToolsByTag("t").stream().findFirst().ifPresent(spec -> {
                        withoutTheRole.set(AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(ctx)));
                        Exchange finance = new DefaultExchange(ctx);
                        finance.setProperty(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of("finance"));
                        withTheRole.set(AiToolExecutor.execute(spec, Map.of(), finance));
                    });
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
                from("ai-tool:transfer?tags=t&description=Transfer&allowedRoles=finance")
                        .routeId("tool").startupOrder(2).setBody(constant("TRANSFERRED"));
            }
        };
    }

    @Test
    void theRolesAreCheckedDuringStartup() {
        assertThat(withoutTheRole.get())
                .as("a tool call during the warm-up/start window must be guarded, not fall back to the unguarded route")
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        // the check itself is in place by then, not only the refusal of a guard that is not ready
        assertThat(withTheRole.get()).isInstanceOf(AiToolResult.Success.class);
    }
}
