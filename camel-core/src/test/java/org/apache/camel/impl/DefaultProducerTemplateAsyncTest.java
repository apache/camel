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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.AsyncProcessorHelper;

/**
 * @version $Revision$
 */
public class DefaultProducerTemplateAsyncTest extends ContextTestSupport {

    private static String order;

    public void testRequestAsync() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");

        Future<Exchange> future = template.asyncSend("direct:start", exchange);
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        Exchange result = future.get();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result.getOut().getBody());
        assertTrue("Should take longer than: " + delta, delta > 250);

        assertMockEndpointsSatisfied();
    }

    public void testSendAsyncProcessor() throws Exception {
        Future<Exchange> future = template.asyncSend("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello");
            }
        });
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        Exchange result = future.get();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result.getOut().getBody());
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncBody() throws Exception {
        Future<Object> future = template.asyncRequestBody("direct:start", "Hello");
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // we can use extract body to convert to expect body type
        String result = template.extractFutureBody(future, String.class);

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncBodyType() throws Exception {
        Future<String> future = template.asyncRequestBody("direct:start", "Hello", String.class);
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // or we can use parameter type in the requestBody method so the future handle know its type
        String result = future.get();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncBodyAndHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);

        Future<Object> future = template.asyncRequestBodyAndHeader("direct:start", "Hello", "foo", 123);
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // we can use extract body to convert to expect body type
        String result = template.extractFutureBody(future, String.class);

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncBodyAndHeaderType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);

        Future<String> future = template.asyncRequestBodyAndHeader("direct:start", "Hello", "foo", 123, String.class);
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // or we can use parameter type in the requestBody method so the future handle know its type
        String result = future.get();

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncBodyAndHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "cheese");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", 123);
        headers.put("bar", "cheese");
        Future<Object> future = template.asyncRequestBodyAndHeaders("direct:start", "Hello", headers);
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // we can use extract body to convert to expect body type
        String result = template.extractFutureBody(future, String.class);

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncBodyAndHeadersType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "cheese");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", 123);
        headers.put("bar", "cheese");
        Future<String> future = template.asyncRequestBodyAndHeaders("direct:start", "Hello", headers, String.class);
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // or we can use parameter type in the requestBody method so the future handle know its type
        String result = future.get();

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncErrorWhenProcessing() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");

        Future<Object> future = template.asyncRequestBody("direct:error", exchange);
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        try {
            Exchange result = template.extractFutureBody(future, Exchange.class);
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            assertEquals("Damn forced by unit test", e.getCause().getMessage());
        }

        long delta = System.currentTimeMillis() - start;
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testRequestAsyncBodyErrorWhenProcessing() throws Exception {
        Future<Object> future = template.asyncRequestBody("direct:error", "Hello");
        long start = System.currentTimeMillis();

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        try {
            String result = template.extractFutureBody(future, String.class);
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            assertEquals("Damn forced by unit test", e.getCause().getMessage());
        }

        long delta = System.currentTimeMillis() - start;
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testAsyncProcessWithClassicAPI() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello");

        long start = System.currentTimeMillis();

        // produce it async so we use a helper
        Producer producer = endpoint.createProducer();
        // normally you will use a shared exectutor service with pools
        ExecutorService executor = Executors.newSingleThreadExecutor();
        // send it async with the help of this helper
        Future<Exchange> future = AsyncProcessorHelper.asyncProcess(executor, producer, exchange);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        String result = template.extractFutureBody(future, String.class);

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testAsyncProcessWithClassicAPIAndTimeout() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello");

        long start = System.currentTimeMillis();

        // produce it async so we use a helper
        Producer producer = endpoint.createProducer();
        // normally you will use a shared exectutor service with pools
        ExecutorService executor = Executors.newSingleThreadExecutor();
        // send it async with the help of this helper
        Future<Exchange> future = AsyncProcessorHelper.asyncProcess(executor, producer, exchange);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        String result = template.extractFutureBody(future, 5, TimeUnit.SECONDS, String.class);

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        assertEquals("Hello World", result);
        assertTrue("Should take longer than: " + delta, delta > 250);
    }

    public void testAsyncCallbackExchangeInOnly() throws Exception {
        order = "";

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        final CountDownLatch latch = new CountDownLatch(1);

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        exchange.getIn().setBody("Hello");

        template.asyncCallback("direct:start", exchange, new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("Hello World", exchange.getIn().getBody());
                latch.countDown();
            }
        });

        order += "A";
        latch.await(10, TimeUnit.SECONDS);
        order += "C";

        assertMockEndpointsSatisfied();
        assertEquals("ABC", order);
    }

    public void testAsyncCallbackExchangeInOut() throws Exception {
        order = "";

        final CountDownLatch latch = new CountDownLatch(1);

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        exchange.getIn().setBody("Hello");
        exchange.setPattern(ExchangePattern.InOut);

        template.asyncCallback("direct:echo", exchange, new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("HelloHello", exchange.getOut().getBody());
                latch.countDown();
            }
        });

        order += "A";
        latch.await(10, TimeUnit.SECONDS);
        order += "C";

        assertEquals("ABC", order);
    }

    public void testAsyncCallbackExchangeInOnlyGetResult() throws Exception {
        order = "";

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        exchange.getIn().setBody("Hello");

        Future<Exchange> future = template.asyncCallback("direct:start", exchange, new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("Hello World", exchange.getIn().getBody());
            }
        });

        order += "A";
        Exchange reply = future.get(10, TimeUnit.SECONDS);
        order += "C";

        assertMockEndpointsSatisfied();
        assertEquals("ABC", order);
        assertNotNull(reply);
    }

    public void testAsyncCallbackExchangeInOutGetResult() throws Exception {
        order = "";

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        exchange.getIn().setBody("Hello");
        exchange.setPattern(ExchangePattern.InOut);

        Future<Exchange> future = template.asyncCallback("direct:echo", exchange, new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("HelloHello", exchange.getOut().getBody());
            }
        });

        order += "A";
        Exchange reply = future.get(10, TimeUnit.SECONDS);
        order += "C";

        assertEquals("ABC", order);
        assertNotNull(reply);
        assertEquals("HelloHello", reply.getOut().getBody());
    }

    public void testAsyncCallbackBodyInOnly() throws Exception {
        order = "";

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        final CountDownLatch latch = new CountDownLatch(1);

        template.asyncCallbackSendBody("direct:start", "Hello", new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("Hello World", exchange.getIn().getBody());
                latch.countDown();
            }
        });

        order += "A";
        latch.await(10, TimeUnit.SECONDS);
        order += "C";

        assertMockEndpointsSatisfied();
        assertEquals("ABC", order);
    }

    public void testAsyncCallbackBodyInOut() throws Exception {
        order = "";

        final CountDownLatch latch = new CountDownLatch(1);

        template.asyncCallbackRequestBody("direct:echo", "Hello", new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("HelloHello", exchange.getOut().getBody());
                latch.countDown();
            }
        });

        order += "A";
        latch.await(10, TimeUnit.SECONDS);
        order += "C";

        assertEquals("ABC", order);
    }

    public void testAsyncCallbackBodyInOnlyGetResult() throws Exception {
        order = "";

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Future<Object> future = template.asyncCallbackSendBody("direct:start", "Hello", new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("Hello World", exchange.getIn().getBody());
            }
        });

        order += "A";
        Object reply = future.get(10, TimeUnit.SECONDS);
        order += "C";

        assertMockEndpointsSatisfied();
        assertEquals("ABC", order);
        // no reply when in only
        assertEquals(null, reply);
    }

    public void testAsyncCallbackBodyInOutGetResult() throws Exception {
        order = "";

        Future<Object> future = template.asyncCallbackRequestBody("direct:echo", "Hello", new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("HelloHello", exchange.getOut().getBody());
            }
        });

        order += "A";
        Object reply = future.get(10, TimeUnit.SECONDS);
        order += "C";

        assertEquals("ABC", order);
        assertEquals("HelloHello", reply);
    }

    public void testAsyncCallbackInOnlyProcessor() throws Exception {
        order = "";

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        final CountDownLatch latch = new CountDownLatch(1);

        template.asyncCallback("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello");
            }
        }, new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("Hello World", exchange.getIn().getBody());
                latch.countDown();
            }
        });

        order += "A";
        latch.await(10, TimeUnit.SECONDS);
        order += "C";

        assertMockEndpointsSatisfied();
        assertEquals("ABC", order);
    }

    public void testAsyncCallbackInOutProcessor() throws Exception {
        order = "";

        final CountDownLatch latch = new CountDownLatch(1);

        template.asyncCallback("direct:echo", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello");
                exchange.setPattern(ExchangePattern.InOut);
            }
        }, new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                order += "B";
                assertEquals("HelloHello", exchange.getOut().getBody());
                latch.countDown();
            }
        });

        order += "A";
        latch.await(10, TimeUnit.SECONDS);
        order += "C";

        assertEquals("ABC", order);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .delay(400)
                    .transform(body().append(" World")).to("mock:result");

                from("direct:error")
                    .delay(400)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            throw new IllegalArgumentException("Damn forced by unit test");
                        }
                    });

                from("direct:echo").transform(body().append(body()));
            }
        };
    }
}
