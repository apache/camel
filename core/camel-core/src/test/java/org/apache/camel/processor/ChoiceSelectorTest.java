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
package org.apache.camel.processor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoiceSelectorTest extends ContextTestSupport {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private Expression selector() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                calls.incrementAndGet();
                return exchange.getMessage().getHeader("department");
            }
        };
    }

    @Test
    void evaluatesOnceForEachEntryAndPreservesBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").choice(selector())
                    .when("billing").setHeader("selected", constant("billing"))
                    .when("technical").setHeader("selected", constant("technical"))
                    .otherwise().setHeader("selected", constant("other"))
                    .end().to("mock:result");
            }
        });
        context.start();
        for (String department : new String[] { "technical", "billing", "TECHNICAL", "unknown" }) {
            Exchange result = template.request("direct:start", e -> {
                e.getMessage().setBody("unchanged");
                e.getMessage().setHeader("department", department);
            });
            assertEquals("unchanged", result.getMessage().getBody());
            assertEquals(department.equals("billing") || department.equals("technical") ? department : "other",
                    result.getMessage().getHeader("selected"));
        }
        Exchange missing = template.request("direct:start", e -> e.getMessage().setBody("unchanged"));
        assertEquals("other", missing.getMessage().getHeader("selected"));
        assertEquals(5, calls.get());
    }

    @Test
    void nestedAndLoopedChoicesHaveIndependentScopes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").loop(3)
                    .choice(selector()).when("technical")
                        .choice(header("nested")).when("yes").to("mock:nested")
                        .otherwise().to("mock:wrong").end().endChoice()
                    .otherwise().to("mock:wrong").end()
                    .setHeader("department", constant("billing"))
                .end();
            }
        });
        context.start();
        getMockEndpoint("mock:nested").expectedMessageCount(1);
        getMockEndpoint("mock:wrong").expectedMessageCount(2);
        template.send("direct:start", e -> {
            e.getMessage().setHeader("department", "technical");
            e.getMessage().setHeader("nested", "yes");
        });
        assertMockEndpointsSatisfied();
        assertEquals(3, calls.get());
    }

    @Test
    void selectorFailureUsesErrorHandlingAndRetryReevaluates() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                Expression expression = new ExpressionAdapter() {
                    public Object evaluate(Exchange exchange) {
                        if (calls.incrementAndGet() == 1) {
                            throw new IllegalStateException("temporary failure");
                        }
                        return "ok";
                    }
                };
                from("direct:start").choice(expression).when("ok").to("mock:ok")
                        .otherwise().to("mock:wrong");
            }
        });
        context.start();
        getMockEndpoint("mock:ok").expectedMessageCount(1);
        getMockEndpoint("mock:wrong").expectedMessageCount(0);
        template.sendBody("direct:start", "hello");
        assertMockEndpointsSatisfied();
        assertEquals(2, calls.get());
    }

    @Test
    void rejectsPreconditionWithoutEvaluating() throws Exception {
        invalid("precondition", choice -> choice.precondition());
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsDuplicateValues() throws Exception {
        invalid("duplicate", choice -> choice.when("a").to("mock:duplicate"));
    }

    @Test
    void rejectsMixedBranches() throws Exception {
        invalid("without predicates", choice -> choice.when(exchange -> true).to("mock:mixed"));
    }

    private void invalid(String message, Consumer<ChoiceDefinition> configure) throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                ChoiceDefinition choice = from("direct:start").choice(selector()).when("a").to("mock:a");
                configure.accept(choice);
            }
        });
        Exception error = assertThrows(Exception.class, context::start);
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }
}
