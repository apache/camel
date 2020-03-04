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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

/**
 * Unit test for DefaultProducerTemplate
 */
public class DefaultProducerTemplateTest extends ContextTestSupport {

    @Test
    public void testIn() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        Object result = template.requestBody("direct:in", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, template.getCamelContext());
    }

    @Test
    public void testInOut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Bye World");

        Object result = template.requestBody("direct:out", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("Bye Bye World", result);
    }

    @Test
    public void testExceptionUsingBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:exception", "Hello World");
            fail("Should have thrown RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionOnRequestBodyWithResponseType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.requestBody("direct:exception", "Hello World", Integer.class);
            fail("Should have thrown RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionUsingProcessor() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = template.send("direct:exception", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionUsingExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange exchange = context.getEndpoint("direct:exception").createExchange();
        exchange.getIn().setBody("Hello World");

        Exchange out = template.send("direct:exception", exchange);
        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRequestExceptionUsingBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.requestBody("direct:exception", "Hello World");
            fail("Should have thrown RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRequestExceptionUsingProcessor() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = template.request("direct:exception", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRequestExceptionUsingExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange exchange = context.getEndpoint("direct:exception").createExchange(ExchangePattern.InOut);
        exchange.getIn().setBody("Hello World");

        Exchange out = template.send("direct:exception", exchange);
        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRequestBody() throws Exception {
        // with endpoint as string uri
        Integer out = template.requestBody("direct:inout", "Hello", Integer.class);
        assertEquals(new Integer(123), out);

        out = template.requestBodyAndHeader("direct:inout", "Hello", "foo", "bar", Integer.class);
        assertEquals(new Integer(123), out);

        Map<String, Object> headers = new HashMap<>();
        out = template.requestBodyAndHeaders("direct:inout", "Hello", headers, Integer.class);
        assertEquals(new Integer(123), out);

        // with endpoint object
        Endpoint endpoint = context.getEndpoint("direct:inout");
        out = template.requestBody(endpoint, "Hello", Integer.class);
        assertEquals(new Integer(123), out);

        out = template.requestBodyAndHeader(endpoint, "Hello", "foo", "bar", Integer.class);
        assertEquals(new Integer(123), out);

        headers = new HashMap<>();
        out = template.requestBodyAndHeaders(endpoint, "Hello", headers, Integer.class);
        assertEquals(new Integer(123), out);
    }

    @Test
    public void testRequestUsingDefaultEndpoint() throws Exception {
        ProducerTemplate producer = new DefaultProducerTemplate(context, context.getEndpoint("direct:out"));
        producer.start();

        Object out = producer.requestBody("Hello");
        assertEquals("Bye Bye World", out);

        out = producer.requestBodyAndHeader("Hello", "foo", 123);
        assertEquals("Bye Bye World", out);

        Map<String, Object> headers = new HashMap<>();
        out = producer.requestBodyAndHeaders("Hello", headers);
        assertEquals("Bye Bye World", out);

        out = producer.requestBodyAndHeaders("Hello", null);
        assertEquals("Bye Bye World", out);

        producer.stop();
    }

    @Test
    public void testSendUsingDefaultEndpoint() throws Exception {
        ProducerTemplate producer = new DefaultProducerTemplate(context, context.getEndpoint("direct:in"));
        producer.start();

        getMockEndpoint("mock:result").expectedMessageCount(3);

        producer.sendBody("Hello");
        producer.sendBodyAndHeader("Hello", "foo", 123);
        Map<String, Object> headers = new HashMap<>();
        producer.sendBodyAndHeaders("Hello", headers);

        assertMockEndpointsSatisfied();

        producer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // for faster unit test
                errorHandler(noErrorHandler());

                from("direct:in").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setBody("Bye World");
                    }
                }).to("mock:result");

                from("direct:out").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("Bye Bye World");
                    }
                }).to("mock:result");

                from("direct:exception").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced exception by unit test");
                    }
                }).to("mock:result");

                from("direct:inout").transform(constant(123));
            }
        };
    }

    @Test
    public void testCacheProducers() throws Exception {
        ProducerTemplate template = new DefaultProducerTemplate(context);
        template.setMaximumCacheSize(500);
        template.start();

        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());

        // test that we cache at most 500 producers to avoid it eating to much
        // memory
        for (int i = 0; i < 503; i++) {
            Endpoint e = context.getEndpoint("seda:queue:" + i);
            template.sendBody(e, "Hello");
        }

        // the eviction is async so force cleanup
        template.cleanUp();
        await().atMost(1, TimeUnit.SECONDS).until(() -> template.getCurrentCacheSize() == 500);
        assertEquals("Size should be 500", 500, template.getCurrentCacheSize());
        template.stop();

        // should be 0
        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());
    }

    @Test
    public void testCacheProducersFromContext() throws Exception {
        ProducerTemplate template = context.createProducerTemplate(500);

        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());

        // test that we cache at most 500 producers to avoid it eating to much
        // memory
        for (int i = 0; i < 503; i++) {
            Endpoint e = context.getEndpoint("seda:queue:" + i);
            template.sendBody(e, "Hello");
        }

        // the eviction is async so force cleanup
        template.cleanUp();
        await().atMost(1, TimeUnit.SECONDS).until(() -> template.getCurrentCacheSize() == 500);
        assertEquals("Size should be 500", 500, template.getCurrentCacheSize());
        template.stop();

        // should be 0
        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());
    }
}
