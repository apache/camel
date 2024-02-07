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

import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SingleInputTypedExpressionDefinition;
import org.junit.jupiter.api.Test;

/**
 * The base class of tests willing to validate the behavior of a language that supports a result type and different
 * sources of input data.
 *
 * @param <T> the type of the builder used to build the language
 * @param <E> the type of the target expression
 */
public abstract class AbstractSingleInputTypedLanguageTest<
        T extends SingleInputTypedExpressionDefinition.AbstractBuilder<T, E>,
        E extends SingleInputTypedExpressionDefinition>
        extends AbstractTypedLanguageTest<T, E> {

    protected AbstractSingleInputTypedLanguageTest(String expression, Function<LanguageBuilderFactory, T> factory) {
        super(expression, factory);
    }

    @Test
    void testHeaderOnly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:header-only")
                    .setBody()
                    .expression(
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

        TestContext context = testWithoutTypeContext();

        MockEndpoint mockEndpoint = getMockEndpoint("mock:header-only");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:header-only", "foo", "someHeader", context.getContentToSend());
        assertMockEndpointsSatisfied();

        assertBodyReceived(context.getBodyReceived(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
        assertTypeInstanceOf(context.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Test
    void testPropertyOnly() throws Exception {
        TestContext testContext = testWithoutTypeContext();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:property-only")
                    .setBody()
                    .expression(
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
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndProperty("direct:property-only", "foo", "someProperty", testContext.getContentToSend());
        assertMockEndpointsSatisfied();

        assertBodyReceived(testContext.getBodyReceived(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
        assertTypeInstanceOf(testContext.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Test
    void testHeaderAndType() throws Exception {
        TestContext testContext = testWithTypeContext();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:header-and-type")
                    .setBody()
                    .expression(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .headerName("someHeader")
                                .resultType(testContext.getBodyReceivedType())
                                .end()
                        )
                    ).to("mock:header-and-type");
            }
        });
        context.start();

        MockEndpoint mockEndpoint = getMockEndpoint("mock:header-and-type");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:header-and-type", "foo", "someHeader", testContext.getContentToSend());
        assertMockEndpointsSatisfied();

        assertTypeInstanceOf(testContext.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

    @Test
    void testPropertyAndType() throws Exception {
        TestContext testContext = testWithTypeContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:property-and-type")
                    .setBody()
                    .expression(
                        expression(
                            factory.apply(expression())
                                .expression(expression)
                                .propertyName("someProperty")
                                .resultType(testContext.getBodyReceivedType())
                                .end()
                        )
                    ).to("mock:property-and-type");
            }
        });
        context.start();

        MockEndpoint mockEndpoint = getMockEndpoint("mock:property-and-type");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndProperty("direct:property-and-type", "foo", "someProperty", testContext.getContentToSend());
        assertMockEndpointsSatisfied();

        assertBodyReceived(testContext.getBodyReceived(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
        assertTypeInstanceOf(testContext.getBodyReceivedType(), mockEndpoint.getReceivedExchanges().get(0).getIn().getBody());
    }

}
