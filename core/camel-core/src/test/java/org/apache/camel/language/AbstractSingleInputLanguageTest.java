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

import java.util.function.Function;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Message;
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SingleInputExpressionDefinition;
import org.junit.jupiter.api.Test;

/**
 * The base class of tests willing to validate the behavior of a language that supports different sources of input data.
 *
 * @param <T> the type of the builder used to build the language
 * @param <E> the type of the target expression
 */
public abstract class AbstractSingleInputLanguageTest<
        T extends SingleInputExpressionDefinition.AbstractBuilder<T, E>, E extends SingleInputExpressionDefinition>
        extends ContextTestSupport {

    protected final String expression;
    protected final Function<LanguageBuilderFactory, T> factory;

    protected AbstractSingleInputLanguageTest(String expression, Function<LanguageBuilderFactory, T> factory) {
        this.expression = expression;
        this.factory = factory;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected abstract TestContext testContext();

    @Test
    void testExpressionOnly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:expression-only")
                    .split(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .end()
                        )
                    ).to("mock:expression-only");
            }
        });
        context.start();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:expression-only");
        TestContext context = testContext();
        mockEndpoint.expectedBodiesReceived(context.getBodyReceived());

        template.sendBody("direct:expression-only", context.getContentToSend());

        assertMockEndpointsSatisfied();
        assertIsInstanceOf(context.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Test
    void testHeaderOnly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:header-only")
                    .split(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .headerName("someHeader")
                                .end()
                        )
                    ).to("mock:header-only");
            }
        });
        context.start();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:header-only");
        TestContext context = testContext();
        mockEndpoint.expectedBodiesReceived(context.getBodyReceived());

        template.sendBodyAndHeader("direct:header-only", "foo", "someHeader", context.getContentToSend());

        assertMockEndpointsSatisfied();
        assertIsInstanceOf(context.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Test
    void testPropertyOnly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:property-only")
                    .split(
                        expression(factory.apply(expression())
                            .expression(expression)
                            .propertyName("someProperty")
                            .end()
                        )
                    ).to("mock:property-only");
            }
        });
        context.start();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:property-only");
        TestContext context = testContext();
        mockEndpoint.expectedBodiesReceived(context.getBodyReceived());

        template.sendBodyAndProperty("direct:property-only", "foo", "someProperty", context.getContentToSend());

        assertMockEndpointsSatisfied();
        assertIsInstanceOf(context.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Test
    void testAll() throws Exception {
        TestContext testContext = testContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:all")
                    .split(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .headerName("someHeader")
                                .propertyName("someProperty")
                                .end()
                        )
                    ).to("mock:all");
            }
        });
        context.start();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:all");
        mockEndpoint.expectedBodiesReceived(testContext.getBodyReceived());

        template.send("direct:all", exchange -> {
            Message message = exchange.getIn();
            message.setBody("foo");
            message.setHeader("someHeader", testContext.getContentToSend());
            exchange.setProperty("someProperty", "bar");
        });

        assertMockEndpointsSatisfied();
        assertIsInstanceOf(testContext.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    protected static class TestContext {

        private final Object contentToSend;
        private final Object bodyReceived;
        private final Class<?> bodyReceivedType;

        public TestContext(Object contentToSend, Object bodyReceived, Class<?> bodyReceivedType) {
            this.contentToSend = contentToSend;
            this.bodyReceived = bodyReceived;
            this.bodyReceivedType = bodyReceivedType;
        }

        public Object getContentToSend() {
            return contentToSend;
        }

        public Object getBodyReceived() {
            return bodyReceived;
        }

        public Class<?> getBodyReceivedType() {
            return bodyReceivedType;
        }
    }
}
