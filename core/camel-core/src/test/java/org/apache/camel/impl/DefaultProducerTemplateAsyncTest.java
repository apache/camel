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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultProducerTemplateAsyncTest extends ContextTestSupport {

    @Test
    public void testRequestAsync() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");

        StopWatch watch = new StopWatch();
        Future<Exchange> future = template.asyncSend("direct:start", exchange);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        Exchange result = future.get();

        long delta = watch.taken();
        assertEquals("Hello World", result.getIn().getBody());
        assertTrue(delta > 50, "Should take longer than: " + delta);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendAsyncProcessor() throws Exception {
        Future<Exchange> future = template.asyncSend("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello");
            }
        });

        StopWatch watch = new StopWatch();
        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        Exchange result = future.get();

        long delta = watch.taken();
        assertEquals("Hello World", result.getIn().getBody());
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncBody() throws Exception {
        StopWatch watch = new StopWatch();
        Future<Object> future = template.asyncRequestBody("direct:start", "Hello");

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // we can use extract body to convert to expect body type
        String result = template.extractFutureBody(future, String.class);

        long delta = watch.taken();
        assertEquals("Hello World", result);
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncBodyType() throws Exception {
        StopWatch watch = new StopWatch();
        Future<String> future = template.asyncRequestBody("direct:start", "Hello", String.class);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // or we can use parameter type in the requestBody method so the future
        // handle know its type
        String result = future.get();

        long delta = watch.taken();
        assertEquals("Hello World", result);
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncBodyAndHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);

        StopWatch watch = new StopWatch();
        Future<Object> future = template.asyncRequestBodyAndHeader("direct:start", "Hello", "foo", 123);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // we can use extract body to convert to expect body type
        String result = template.extractFutureBody(future, String.class);

        assertMockEndpointsSatisfied();

        long delta = watch.taken();
        assertEquals("Hello World", result);
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncBodyAndHeaderType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);

        StopWatch watch = new StopWatch();
        Future<String> future = template.asyncRequestBodyAndHeader("direct:start", "Hello", "foo", 123, String.class);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // or we can use parameter type in the requestBody method so the future
        // handle know its type
        String result = future.get();

        assertMockEndpointsSatisfied();

        long delta = watch.taken();
        assertEquals("Hello World", result);
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncBodyAndHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "cheese");

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", 123);
        headers.put("bar", "cheese");
        StopWatch watch = new StopWatch();
        Future<Object> future = template.asyncRequestBodyAndHeaders("direct:start", "Hello", headers);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // we can use extract body to convert to expect body type
        String result = template.extractFutureBody(future, String.class);

        assertMockEndpointsSatisfied();

        long delta = watch.taken();
        assertEquals("Hello World", result);
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncBodyAndHeadersType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "cheese");

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", 123);
        headers.put("bar", "cheese");
        StopWatch watch = new StopWatch();
        Future<String> future = template.asyncRequestBodyAndHeaders("direct:start", "Hello", headers, String.class);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        // or we can use parameter type in the requestBody method so the future
        // handle know its type
        String result = future.get();

        assertMockEndpointsSatisfied();

        long delta = watch.taken();
        assertEquals("Hello World", result);
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncErrorWhenProcessing() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");

        StopWatch watch = new StopWatch();
        Future<Object> future = template.asyncRequestBody("direct:error", exchange);

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        RuntimeCamelException e
                = assertThrows(RuntimeCamelException.class, () -> template.extractFutureBody(future, Exchange.class),
                        "Should have thrown exception");

        assertEquals("Damn forced by unit test", e.getCause().getMessage());

        long delta = watch.taken();
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Test
    public void testRequestAsyncBodyErrorWhenProcessing() throws Exception {
        StopWatch watch = new StopWatch();
        Future<Object> future = template.asyncRequestBody("direct:error", "Hello");

        // you can do other stuff
        String echo = template.requestBody("direct:echo", "Hi", String.class);
        assertEquals("HiHi", echo);

        RuntimeCamelException e
                = assertThrows(RuntimeCamelException.class, () -> template.extractFutureBody(future, String.class),
                        "Should have thrown exception");

        assertEquals("Damn forced by unit test", e.getCause().getMessage());

        long delta = watch.taken();
        assertTrue(delta > 50, "Should take longer than: " + delta);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").delay(200).asyncDelayed().transform(body().append(" World")).to("mock:result");

                from("direct:error").delay(200).asyncDelayed().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Damn forced by unit test");
                    }
                });

                from("direct:echo").transform(body().append(body()));

                from("direct:threads").threads(5).transform(body().append(body()));
            }
        };
    }

}
