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
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24831: an {@code authorizationPolicy} on an {@code ai-tool} route guards the tool call by construction. The
 * policy runs before the route body; a denial surfaces as {@link AiToolResult.AuthorizationDenied} (a short refusal),
 * not as a route result or a rethrow.
 */
class AiToolAuthorizationPolicyTest extends CamelTestSupport {

    /**
     * Allows the call only when the exchange carries {@code subject == alice}; otherwise throws
     * {@link CamelAuthorizationException}, exactly as a real {@link AuthorizationPolicy} does.
     */
    static final class SubjectPolicy implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // nothing to prepare
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

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("subjectPolicy", new SubjectPolicy());
        registry.bind("allowAll", new AllowAll());
        registry.bind("exceptionDeny", new ExceptionDeny());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:guarded"
                     + "?tags=test"
                     + "&description=A guarded tool"
                     + "&authorizationPolicy=#subjectPolicy")
                        .setBody(constant("ok"));

                from("ai-tool:exceptionGuarded"
                     + "?tags=test"
                     + "&description=A tool whose policy sets the exception instead of throwing"
                     + "&authorizationPolicy=#exceptionDeny")
                        .setBody(constant("ok"));

                from("ai-tool:allowedButFails"
                     + "?tags=test"
                     + "&description=A tool that is allowed but whose route fails"
                     + "&authorizationPolicy=#allowAll")
                        .throwException(new RuntimeException("route boom"));
            }
        };
    }

    @Test
    void allowsTheCallWhenThePolicyPasses() {
        AiToolSpec spec = findSpec("guarded");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("subject", "alice");

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value()).isEqualTo("ok");
    }

    @Test
    void deniesTheCallAsARefusalWhenThePolicyRejects() {
        AiToolSpec spec = findSpec("guarded");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("subject", "mallory");

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), exchange);

        // a denial is its own result type (a short refusal), not a Success and not a generic ExecutionError
        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(((AiToolResult.AuthorizationDenied) result).message()).contains("guarded");
    }

    @Test
    void deniesWhenThePolicySetsTheExceptionInsteadOfThrowing() {
        AiToolSpec spec = findSpec("exceptionGuarded");
        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(context));
        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(((AiToolResult.AuthorizationDenied) result).message()).contains("exceptionGuarded");
    }

    @Test
    void aNormalRouteErrorIsAnExecutionErrorNotADenial() {
        // the policy allows, but the route then throws a non-authorization exception: it must come back as an
        // ExecutionError, not be misclassified as a denial
        AiToolSpec spec = findSpec("allowedButFails");
        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(context));
        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("test").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
