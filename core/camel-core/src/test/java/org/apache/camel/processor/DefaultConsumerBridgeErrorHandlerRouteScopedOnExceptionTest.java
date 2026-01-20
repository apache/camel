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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class DefaultConsumerBridgeErrorHandlerRouteScopedOnExceptionTest extends ContextTestSupport {

    protected final CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testDefaultConsumerBridgeErrorHandler() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello World");
        getMockEndpoint("mock:a").expectedBodiesReceived("Cannot process");
        // the other routes does not have onException / error-handler and the bridge will cause
        // these routes to fail with a new exception (current behavior in camel)
        // so we do not expect the exchange to be routed to mocks
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        latch.countDown();

        assertMockEndpointsSatisfied();

        Exception cause = getMockEndpoint("mock:a").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT,
                Exception.class);
        assertNotNull(cause);
        assertEquals("Simulated", cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // START SNIPPET: e1
        return new RouteBuilder() {
            @Override
            public void configure() {
                // register our custom component
                getContext().addComponent("my", new MyComponent());

                // configure the consumer to bridge with the Camel error
                // handler,
                // so the above error handler will trigger if exceptions also
                // occurs inside the consumer
                from("my:foo?bridgeErrorHandler=true")
                        // configure route scoped on exception
                        .onException(Exception.class).handled(true).to("mock:a").to("direct:error").to("mock:dead").end()
                        .to("log:foo").to("mock:result");

                from("direct:error").to("mock:b").log("Error happened due ${exception.message}");
            }
        };
        // END SNIPPET: e1
    }

    public class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new MyEndpoint(uri, this);
        }
    }

    public class MyEndpoint extends DefaultEndpoint {

        public MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            Consumer answer = new MyConsumer(this, processor);
            configureConsumer(answer);
            return answer;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    public class MyConsumer extends DefaultConsumer {

        private int invoked;

        public MyConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        public void doSomething() {
            try {
                if (invoked++ == 0) {
                    throw new IllegalArgumentException("Simulated");
                }

                Exchange exchange = getEndpoint().createExchange();
                exchange.getIn().setBody("Hello World");
                getProcessor().process(exchange);

            } catch (Exception e) {
                getExceptionHandler().handleException("Cannot process", e);
            }
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();

            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        // do not start before the mocks has been setup and is
                        // ready
                        latch.await(5, TimeUnit.SECONDS);
                        doSomething();
                        doSomething();
                        doSomething();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            };
            thread.start();
        }
    }
}
