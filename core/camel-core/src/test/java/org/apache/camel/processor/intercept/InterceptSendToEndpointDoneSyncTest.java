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
package org.apache.camel.processor.intercept;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultInterceptSendToEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The producer of an intercepted endpoint keeps the contract of AsyncProcessor: process returns the value its callback
 * is called with, also when the before of the interceptor and the producer of the original endpoint differ (one
 * synchronous, the other not).
 */
class InterceptSendToEndpointDoneSyncTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // a component, so its endpoints are created when used, and the mock endpoints strategy wraps them
        context.addComponent("async", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new AsyncEndpoint(uri, getCamelContext());
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the before of this interceptor is a route of its own, which goes on through the reactive executor
                interceptSendToEndpoint("mock:result").to("mock:detour");

                from("direct:start").to("mock:result");
                // the routing slip goes on with its next step through the return value of the send, or through the
                // callback when the send returned false
                from("direct:slip").routingSlip(constant("mock:result,mock:next"));
            }
        };
    }

    @Test
    void anAsynchronousBeforeAndASynchronousProducer() throws Exception {
        getMockEndpoint("mock:detour").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Endpoint endpoint = context.getEndpoint("mock:result");
        assertInstanceOf(InterceptSendToEndpoint.class, endpoint);
        assertDoneSyncIsTheReturnedValue(endpoint);

        assertMockEndpointsSatisfied();
    }

    @Test
    void aSynchronousBeforeAndAnAsynchronousProducer() throws Exception {
        getMockEndpoint("mock:before").expectedMessageCount(1);

        // as advice with mockEndpoints() wraps an endpoint: the before is the producer of a mock, which is synchronous
        DefaultInterceptSendToEndpoint endpoint
                = new DefaultInterceptSendToEndpoint(context.getEndpoint("async:x"), false);
        endpoint.setBefore(context.getEndpoint("mock:before").createProducer());
        endpoint.start();
        boolean returned = assertDoneSyncIsTheReturnedValue(endpoint);

        // the producer of async:x calls back on its own thread
        assertFalse(returned);
        assertMockEndpointsSatisfied();
    }

    @Test
    void aRoutingSlipGoesOnAfterAnInterceptedEndpoint() throws Exception {
        getMockEndpoint("mock:detour").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:next").expectedBodiesReceived("Hello");

        template.asyncSendBody("direct:slip", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    void theRoutesStillWork() throws Exception {
        getMockEndpoint("mock:detour").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    private boolean assertDoneSyncIsTheReturnedValue(Endpoint endpoint) throws Exception {
        AsyncProducer producer = endpoint.createAsyncProducer();
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("Hello");
            CompletableFuture<Boolean> callback = new CompletableFuture<>();
            boolean returned = producer.process(exchange, callback::complete);
            assertEquals(returned, callback.get(10, TimeUnit.SECONDS), "the value of the callback");
            return returned;
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    /**
     * An endpoint whose producer calls its callback on a thread of its own.
     */
    private static final class AsyncEndpoint extends DefaultEndpoint {

        AsyncEndpoint(String uri, CamelContext camelContext) {
            super(uri, null);
            setCamelContext(camelContext);
        }

        @Override
        public Producer createProducer() {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    new Thread(() -> callback.done(false), "async-producer").start();
                    return false;
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException("producer only");
        }
    }
}
