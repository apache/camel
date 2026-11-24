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
package org.apache.camel.processor.async;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.DynamicRouter;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A dynamic router or routing slip whose step completed asynchronously goes on as when the step completed
 * synchronously: a failure of the router expression after that step fails the exchange, the router is not asked again
 * after a failed step, and an invalid last endpoint is ignored with ignoreInvalidEndpoints.
 */
public class AsyncEndpointDynamicRouterFailureTest extends ContextTestSupport {

    private static final String ASYNC_HI = "async:hi:camel?delay=0";
    private static final String ASYNC_BYE = "async:bye:camel?delay=0";
    private static final String INVALID = "file:target/data/slip?fileExist=Append&tempPrefix=hello";

    private final List<String> steps = new CopyOnWriteArrayList<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();

    @Test
    public void testRouterFailsAfterSyncStepThenAsyncStep() throws Exception {
        steps.addAll(List.of("direct:foo", ASYNC_BYE));

        assertRouterFailureFailsExchange();
    }

    @Test
    public void testRouterFailsAfterTwoAsyncSteps() throws Exception {
        steps.addAll(List.of(ASYNC_HI, ASYNC_BYE));

        assertRouterFailureFailsExchange();
    }

    @Test
    public void testRouterFailsAfterOneAsyncStep() throws Exception {
        steps.add(ASYNC_BYE);

        assertRouterFailureFailsExchange();
    }

    @Test
    public void testRouterFailsAfterTwoSyncSteps() throws Exception {
        steps.addAll(List.of("direct:foo", "direct:bar"));

        assertRouterFailureFailsExchange();
    }

    @Test
    public void testAnnotatedRouterFailsAfterSyncStepThenAsyncStep() throws Exception {
        steps.addAll(List.of("direct:foo", ASYNC_BYE));
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:annotated", "Hello Camel");

        assertMockEndpointsSatisfied();
        assertRouterFailure(error);
    }

    @Test
    public void testRouterFailureCaughtByDoCatch() throws Exception {
        steps.addAll(List.of("direct:foo", "direct:bar"));
        getMockEndpoint("mock:caught").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBody("direct:try", "Hello Camel");

        assertMockEndpointsSatisfied();
        assertEquals(steps.size() + 1, calls.size());
    }

    @Test
    public void testRouterNotCalledAfterFailedAsyncStep() throws Exception {
        steps.addAll(List.of("direct:foo", "async:bye:camel?delay=0&failFirstAttempts=1", "mock:more"));
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:more").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();
        // the router is asked for the 1st and the 2nd step only, as when the failed step completed synchronously
        assertEquals(2, calls.size());
    }

    @Test
    public void testRoutingSlipIgnoresInvalidLastEndpointAfterAsyncStep() throws Exception {
        getMockEndpoint("mock:end").expectedBodiesReceived("Bye Camel");

        template.sendBodyAndHeader("direct:slip", "Hello Camel", "slip", ASYNC_BYE + "," + INVALID);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutingSlipIgnoresInvalidLastEndpointAfterSyncStep() throws Exception {
        getMockEndpoint("mock:end").expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("direct:slip", "Hello Camel", "slip", "direct:foo," + INVALID);

        assertMockEndpointsSatisfied();
    }

    private void assertRouterFailureFailsExchange() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();
        assertRouterFailure(error);
    }

    private void assertRouterFailure(MockEndpoint error) {
        Exception caught = error.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        Throwable cause = caught;
        while (cause != null && !(cause instanceof IllegalStateException)) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalStateException.class, cause, "the router's failure: " + caught);
        assertEquals(steps.size() + 1, calls.size());
    }

    @DynamicRouter
    public String route(String body) {
        return slip(body);
    }

    public String slip(String body) {
        int n = calls.size();
        calls.add(body);
        if (n < steps.size()) {
            return steps.get(n);
        }
        throw new IllegalStateException("No route for " + body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("async", new MyAsyncComponent());

                onException(IllegalStateException.class, CamelExchangeException.class).handled(true).to("mock:error");

                // an expression that throws (method() sets the exception on the exchange instead)
                from("direct:start").dynamicRouter(new ExpressionAdapter() {
                    @Override
                    public Object evaluate(Exchange exchange) {
                        return slip(exchange.getMessage().getBody(String.class));
                    }
                }).to("mock:result");

                from("direct:try")
                        .doTry()
                        .dynamicRouter(new ExpressionAdapter() {
                            @Override
                            public Object evaluate(Exchange exchange) {
                                return slip(exchange.getMessage().getBody(String.class));
                            }
                        }).end()
                        .to("mock:result")
                        .doCatch(IllegalStateException.class)
                        .to("mock:caught")
                        .end();

                // a bean method annotated with @DynamicRouter (its expression throws what the method throws)
                from("direct:annotated").bean(AsyncEndpointDynamicRouterFailureTest.this, "route").to("mock:result");

                from("direct:slip").routingSlip(header("slip")).ignoreInvalidEndpoints().to("mock:end");

                from("direct:foo").transform(constant("Bye World"));
                from("direct:bar").transform(constant("Bye Bar"));
            }
        };
    }
}
