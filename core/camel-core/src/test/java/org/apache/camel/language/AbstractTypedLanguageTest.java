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
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.TypedExpressionDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The base class of tests willing to validate the behavior of a language that supports a result type.
 *
 * @param <T> the type of the builder used to build the language
 * @param <E> the type of the target expression
 */
public abstract class AbstractTypedLanguageTest<
        T extends TypedExpressionDefinition.AbstractBuilder<T, E>, E extends TypedExpressionDefinition>
        extends ContextTestSupport {

    protected final String expression;
    protected final Function<LanguageBuilderFactory, T> factory;

    protected AbstractTypedLanguageTest(String expression, Function<LanguageBuilderFactory, T> factory) {
        this.expression = expression;
        this.factory = factory;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected void assertResult(String uriSuffix, TestContext context) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:%s", uriSuffix));
        mockEndpoint.expectedMessageCount(1);
        template.sendBody(String.format("direct:%s", uriSuffix), context.getContentToSend());
        assertMockEndpointsSatisfied();

        assertTypeInstanceOf(context.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
        assertBodyReceived(context.getBodyReceived(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    protected void assertTypeInstanceOf(Class<?> expected, Object body) {
        if (expected != null) {
            assertIsInstanceOf(expected, body);
        }
    }

    protected void assertBodyReceived(Object expected, Object body) {
        if (expected != null) {
            if (expected instanceof Integer && body instanceof Integer) {
                // java objects for number crap
                Assertions.assertEquals((int) expected, (int) body);
            } else {
                Assertions.assertEquals(expected, body);
            }
        }
    }

    protected Object defaultContentToSend() {
        return "1";
    }

    protected TestContext testWithoutTypeContext() {
        return new TestContext(defaultContentToSend(), "1", String.class);
    }

    @Test
    void testExpressionOnly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:expression-only")
                    .setBody()
                    .expression(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .end()
                        )
                    ).to("mock:expression-only");
            }
        });
        context.start();
        assertResult("expression-only", testWithoutTypeContext());
    }

    protected TestContext testWithTypeContext() {
        return new TestContext(defaultContentToSend(), 1, Integer.class);
    }

    @Test
    void testTypedWithClass() throws Exception {
        TestContext testContext = testWithTypeContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:typed-with-class")
                    .setBody()
                    .expression(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .resultType(testContext.getBodyReceivedType())
                                .end()
                        )
                    ).to("mock:typed-with-class");
            }
        });
        context.start();
        assertResult("typed-with-class", testContext);
    }

    @Test
    void testTypedWithName() throws Exception {
        TestContext testContext = testWithTypeContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:typed-with-name")
                    .split(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .resultTypeName(testContext.getBodyReceivedType().getName())
                                .end()
                        )
                    ).to("mock:typed-with-name");
            }
        });
        context.start();
        assertResult("typed-with-name", testContext);
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
