/**
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
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 *
 */
public class DefaultConsumerBridgeErrorHandlerTest extends ContextTestSupport {

    protected final CountDownLatch latch = new CountDownLatch(1);

    public void testDefaultConsumerBridgeErrorHandler() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello World");
        getMockEndpoint("mock:dead").expectedBodiesReceived("Cannot process");

        latch.countDown();

        assertMockEndpointsSatisfied();

        Exception cause = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);
        assertEquals("Simulated", cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // START SNIPPET: e1
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // register our custom component
                getContext().addComponent("my", new MyComponent());

                // configure error handler
                errorHandler(deadLetterChannel("mock:dead"));

                // configure the consumer to bridge with the Camel error handler,
                // so the above error handler will trigger if exceptions also
                // occurs inside the consumer
                from("my:foo?consumer.bridgeErrorHandler=true")
                    .to("log:foo")
                    .to("mock:result");
            }
        };
        // END SNIPPET: e1
    }

    public class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyEndpoint(uri, this);
        }
    }

    public class MyEndpoint extends DefaultEndpoint {

        public MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() throws Exception {
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

        public void doSomething() throws Exception {
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
                        // do not start before the mocks has been setup and is ready
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
