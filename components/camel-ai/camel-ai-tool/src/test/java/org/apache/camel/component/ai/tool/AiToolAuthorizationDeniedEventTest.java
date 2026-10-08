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
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25404: an {@code authorizationPolicy} denial on an {@code ai-tool} route is caught in front of the route, so it
 * fires no exchange-lifecycle event or route span. This verifies the component instead emits an
 * {@link AiToolAuthorizationDeniedEvent} on every denial (so operators can observe and alert), while the model-facing
 * behaviour is unchanged (still an {@link AiToolResult.AuthorizationDenied} refusal), and that the event is
 * <em>not</em> emitted for a normal route error or a successful call.
 */
class AiToolAuthorizationDeniedEventTest extends CamelTestSupport {

    private final List<AiToolAuthorizationDeniedEvent> events = new CopyOnWriteArrayList<>();

    /** Allows the call only when {@code subject == alice}; otherwise throws, as a real policy does. */
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

    /** Denies by setting the exception on the exchange rather than throwing (as Spring Security's policy does). */
    static final class ExceptionDeny implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return exchange -> exchange.setException(new CamelAuthorizationException("denied via exchange", exchange));
        }
    }

    /** Allows every call (delegates straight to the route). */
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
    protected void bindToRegistry(Registry registry) {
        registry.bind("subjectPolicy", new SubjectPolicy());
        registry.bind("exceptionDeny", new ExceptionDeny());
        registry.bind("allowAll", new AllowAll());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:guarded?tags=test&description=A guarded tool&authorizationPolicy=#subjectPolicy")
                        .setBody(constant("ok"));

                from("ai-tool:exceptionGuarded?tags=test&description=A tool whose policy sets the exception"
                     + "&authorizationPolicy=#exceptionDeny")
                        .setBody(constant("ok"));

                from("ai-tool:allowedButFails?tags=test&description=A tool allowed but whose route fails"
                     + "&authorizationPolicy=#allowAll")
                        .throwException(new RuntimeException("route boom"));
            }
        };
    }

    @BeforeEach
    void registerEventNotifier() {
        events.clear();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof AiToolAuthorizationDeniedEvent denied) {
                    events.add(denied);
                }
            }
        });
    }

    @Test
    void firesAnEventWhenThePolicyThrows() {
        AiToolSpec spec = findSpec("guarded");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("subject", "mallory");

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), exchange);

        // model-facing behaviour is unchanged: still a refusal
        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        // ...and the denial is now observable as a single event
        assertThat(events).hasSize(1);
        AiToolAuthorizationDeniedEvent event = events.get(0);
        assertThat(event.getType()).isEqualTo(CamelEvent.Type.Custom);
        assertThat(event.getToolName()).isEqualTo("guarded");
        assertThat(event.getExchange()).isSameAs(exchange);
        assertThat(event.getCause()).isInstanceOf(CamelAuthorizationException.class);
    }

    @Test
    void firesAnEventWhenThePolicySetsTheExceptionInsteadOfThrowing() {
        AiToolSpec spec = findSpec("exceptionGuarded");

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(context));

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getToolName()).isEqualTo("exceptionGuarded");
    }

    @Test
    void doesNotFireForANonAuthorizationRouteError() {
        AiToolSpec spec = findSpec("allowedButFails");

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(context));

        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
        assertThat(events).isEmpty();
    }

    @Test
    void doesNotFireWhenThePolicyAllows() {
        AiToolSpec spec = findSpec("guarded");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("subject", "alice");

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(events).isEmpty();
    }

    @Test
    void aNotifierThrowingAnErrorDoesNotBreakTheRefusal() {
        // ManagementStrategy.notify does not isolate a failing notifier, so without a Throwable guard a notifier that
        // throws an Error would escape and turn the denial into a propagating failure. The refusal must survive.
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof AiToolAuthorizationDeniedEvent) {
                    throw new AssertionError("broken notifier");
                }
            }
        });

        AiToolSpec spec = findSpec("guarded");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("subject", "mallory");

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("test").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
