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
package org.apache.camel.language;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24884: with resolveResource=true a result that is a String starting with resource: is loaded and its content is
 * the result. Off by default, the text is the result.
 * <p/>
 * CAMEL-25982: the name has no scheme and is loaded from the classpath; a name with a scheme or with .. fails.
 */
public class ResolveResourceExpressionTest extends ContextTestSupport {

    @Test
    public void testResultIsLoadedWithoutAScheme() throws Exception {
        getMockEndpoint("mock:resolved").expectedBodiesReceived("{\"orderId\": \"ORD-TEMPLATE\", \"status\": \"new\"}");
        template.sendBodyAndHeader("direct:resolved", "x", "name",
                "resource:org/apache/camel/language/resolve/order-template.json");
        assertMockEndpointsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "resource:classpath:org/apache/camel/language/resolve/order-template.json",
            "resource:file:src/test/resources/org/apache/camel/language/resolve/order-template.json",
            "resource:http://localhost/order-template.json",
            "resource:ref:myTemplate",
            "resource:bean:myBean.template" })
    public void testResultWithASchemeFails(String name) throws Exception {
        getMockEndpoint("mock:resolved").expectedMessageCount(0);
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:resolved", "x", "name", name));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("Give the name without a scheme"), cause.getMessage());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testResultWithAParentFolderFails() throws Exception {
        getMockEndpoint("mock:resolved").expectedMessageCount(0);
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:resolved", "x", "name",
                        "resource:org/apache/camel/language/../language/resolve/order-template.json"));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("with .. in its name"), cause.getMessage());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOtherResultsAreUntouched() throws Exception {
        getMockEndpoint("mock:resolved").expectedBodiesReceived("plain text");
        template.sendBodyAndHeader("direct:resolved", "x", "name", "plain text");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOffByDefault() throws Exception {
        getMockEndpoint("mock:plain")
                .expectedBodiesReceived("resource:classpath:org/apache/camel/language/resolve/order-template.json");
        template.sendBodyAndHeader("direct:plain", "x", "name",
                "resource:classpath:org/apache/camel/language/resolve/order-template.json");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                SimpleExpression resolving = new SimpleExpression("${header.name}");
                resolving.setResolveResource("true");
                from("direct:resolved").setBody(resolving).to("mock:resolved");

                from("direct:plain").setBody(new SimpleExpression("${header.name}")).to("mock:plain");
            }
        };
    }
}
