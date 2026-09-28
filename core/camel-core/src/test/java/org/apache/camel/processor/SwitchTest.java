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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchTest extends ContextTestSupport {
    private final AtomicInteger evaluations = new AtomicInteger();

    @Test
    void scalarMatchingAndFallback() throws Exception {
        getMockEndpoint("mock:billing").expectedMessageCount(2);
        getMockEndpoint("mock:empty").expectedMessageCount(1);
        getMockEndpoint("mock:literal").expectedMessageCount(1);
        getMockEndpoint("mock:review").expectedMessageCount(3);
        getMockEndpoint("mock:after").expectedMessageCount(7);
        for (String value : new String[] { "billing", "BILLING", "", "billing*", "billing123", "other" }) {
            template.sendBodyAndHeader("direct:scalar", "message", "department", value);
        }
        template.sendBody("direct:scalar", "message");
        assertMockEndpointsSatisfied();
    }

    @Test
    void compositeMatchesNamedTypedValuesAndIgnoresExtraFields() throws Exception {
        getMockEndpoint("mock:urgent").expectedBodiesReceived("first", "second");
        getMockEndpoint("mock:billing").expectedBodiesReceived("third");
        getMockEndpoint("mock:review").expectedBodiesReceived("string", "unknown", "null");
        template.sendBodyAndHeader("direct:composite", "first", "decision", Map.of("department", "billing", "urgent", true));
        template.sendBodyAndHeader("direct:composite", "second", "decision",
                Map.of("urgent", true, "department", "BILLING", "score", 3));
        template.sendBodyAndHeader("direct:composite", "third", "decision", Map.of("urgent", false, "department", "billing"));
        template.sendBodyAndHeader("direct:composite", "string", "decision", Map.of("department", "billing", "urgent", "true"));
        template.sendBodyAndHeader("direct:composite", "unknown", "decision", Map.of("department", "other", "urgent", true));
        template.sendBody("direct:composite", "null");
        assertMockEndpointsSatisfied();
    }

    @Test
    void malformedCompositeDoesNotUseFallback() throws Exception {
        getMockEndpoint("mock:review").expectedMessageCount(0);
        for (Object result : new Object[] {
                "billing", Map.of("department", "billing"), Map.of("department", "billing", "urgent", Map.of()) }) {
            Exchange answer = template.request("direct:composite", e -> e.getIn().setHeader("decision", result));
            assertNotNull(answer.getException());
        }
        assertMockEndpointsSatisfied();
    }

    @Test
    void numericTypesShareAKeyButStringsRemainDistinct() throws Exception {
        getMockEndpoint("mock:number").expectedMessageCount(3);
        getMockEndpoint("mock:string").expectedMessageCount(1);
        for (Object number : new Object[] { 2, 2.0, new BigDecimal("2.00"), "2" }) {
            template.sendBodyAndHeader("direct:numbers", "message", "decision", Map.of("score", number));
        }
        assertMockEndpointsSatisfied();
    }

    @Test
    void noFallbackContinues() throws Exception {
        getMockEndpoint("mock:after").expectedMessageCount(2);
        template.sendBodyAndHeader("direct:noFallback", "other", "department", "other");
        template.sendBody("direct:noFallback", "null");
        assertMockEndpointsSatisfied();
    }

    @Test
    void repeatedEntryEvaluatesOncePerEntry() throws Exception {
        getMockEndpoint("mock:one").expectedMessageCount(1);
        getMockEndpoint("mock:two").expectedMessageCount(1);
        getMockEndpoint("mock:three").expectedMessageCount(1);
        template.sendBody("direct:loop", "message");
        assertEquals(3, evaluations.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    void nestedSwitchesSelectIndependently() throws Exception {
        getMockEndpoint("mock:urgent").expectedMessageCount(1);
        getMockEndpoint("mock:after").expectedMessageCount(1);
        template.sendBodyAndHeaders("direct:nested", "message",
                Map.of("department", "billing", "decision", Map.of("department", "billing", "urgent", true)));
        assertMockEndpointsSatisfied();
    }

    @Test
    void selectorExceptionFollowsErrorHandling() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:review").expectedMessageCount(0);
        getMockEndpoint("mock:after").expectedMessageCount(0);
        Exchange answer = template.request("direct:failure", e -> e.getIn().setBody("message"));
        assertNull(answer.getException());
        assertTrue(answer.getProperty(Exchange.EXCEPTION_CAUGHT) instanceof IllegalStateException);
        assertMockEndpointsSatisfied();
    }

    @Test
    void selectorRewindsStreamForDestination() throws Exception {
        getMockEndpoint("mock:stream").expectedBodiesReceived("hello");
        template.sendBody("direct:stream", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        assertMockEndpointsSatisfied();
    }

    @Test
    void normalizationDoesNotDependOnDefaultLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            getMockEndpoint("mock:billing").expectedMessageCount(1);
            template.sendBodyAndHeader("direct:scalar", "message", "department", "BILLING");
            assertMockEndpointsSatisfied();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:scalar").doSwitch().header("department")
                        .doCase("billing", "mock:billing").doCase("", "mock:empty").doCase("billing*", "mock:literal")
                        .otherwise("mock:review").end().to("mock:after");
                from("direct:composite").doSwitch(header("decision")).keys("department", "urgent")
                        .doCase().value("department", "billing").value("urgent", true).id("urgentCase").to("mock:urgent")
                        .doCase().value("urgent", false).value("department", "billing").to("mock:billing")
                        .otherwise("mock:review");
                from("direct:numbers").doSwitch(header("decision")).keys("score")
                        .doCase().value("score", 2).to("mock:number")
                        .doCase().value("score", "2").to("mock:string");
                from("direct:noFallback").doSwitch(header("department")).doCase("billing", "mock:billing")
                        .end().to("mock:after");
                from("direct:loop").loop(3).doSwitch(new ExpressionAdapter() {
                    @Override
                    public Object evaluate(Exchange exchange) {
                        return evaluations.incrementAndGet();
                    }
                }).doCase("1", "mock:one").doCase("2", "mock:two").doCase("3", "mock:three").end().end();
                from("direct:nested").choice().when(header("department").isEqualTo("billing"))
                        .doSwitch(header("department")).doCase("billing", "direct:composite")
                        .end().end().to("mock:after");
                from("direct:failure").onException(IllegalStateException.class).handled(true).to("mock:error").end()
                        .doSwitch(new ExpressionAdapter() {
                            @Override
                            public Object evaluate(Exchange exchange) { throw new IllegalStateException("selector failed");
                            }
                        }).otherwise("mock:review").end().to("mock:after");
                from("direct:stream").streamCaching().doSwitch(new ExpressionAdapter() {
                    @Override
                    public Object evaluate(Exchange exchange) {
                        try {
                            exchange.getIn().getBody(InputStream.class).readAllBytes();
                            return "read";
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }).doCase("read", "direct:readStream");
                from("direct:readStream").convertBodyTo(String.class).to("mock:stream");
            }
        };
    }
}
