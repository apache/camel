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
package org.apache.camel.builder;

import java.util.concurrent.Future;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for FluentProducerTemplate
 */
public class FluentProducerTemplateTest extends ContextTestSupport {

    public void testNoEndpoint() throws Exception {
        FluentProducerTemplate fluent = context.createFluentProducerTemplate();

        try {
            fluent.withBody("Hello World").send();
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            fluent.withBody("Hello World").request();
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testDefaultEndpoint() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();
        fluent.setDefaultEndpointUri("direct:in");

        Object result = fluent.withBody("Hello World").request();
        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, fluent.getCamelContext());
    }

    public void testFromCamelContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();

        Object result = fluent
            .withBody("Hello World")
            .to("direct:in")
            .request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, fluent.getCamelContext());
    }

    public void testIn() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        Object result = DefaultFluentProducerTemplate.on(context)
            .withBody("Hello World")
            .to("direct:in")
            .request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, template.getCamelContext());
    }

    public void testInOut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Bye World");

        Object result = DefaultFluentProducerTemplate.on(context)
            .withBody("Hello World")
            .to("direct:out")
            .request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye Bye World", result);
    }

    public void testInOutWithBodyConversion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(11);

        Object result = DefaultFluentProducerTemplate.on(context)
            .withBodyAs("10", Integer.class)
            .to("direct:sum")
            .request();

        assertMockEndpointsSatisfied();

        assertEquals(11, result);
    }

    public void testInOutWithBodyConversionFault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            DefaultFluentProducerTemplate.on(context)
                .withBodyAs("10", Double.class)
                .to("direct:sum")
                .request();
        } catch (CamelExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("Expected body of type Integer", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testFault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Object result = DefaultFluentProducerTemplate.on(context)
            .withBody("Hello World")
            .to("direct:fault")
            .request();

        assertMockEndpointsSatisfied();

        assertEquals("Faulty World", result);
    }

    public void testExceptionUsingBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context)
            .withBody("Hello World")
            .to("direct:exception")
            .send();

        assertTrue(out.isFailed());
        assertTrue(out.getException() instanceof IllegalArgumentException);
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    public void testExceptionUsingProcessor() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context)
            .withProcessor(exchange -> exchange.getIn().setBody("Hello World"))
            .to("direct:exception")
            .send();

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    public void testExceptionUsingExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context)
                .withExchange(() -> {
                    Exchange exchange = context.getEndpoint("direct:exception").createExchange();
                    exchange.getIn().setBody("Hello World");
                    return exchange;
                })
                .to("direct:exception")
                .send();

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    public void testRequestExceptionUsingBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            DefaultFluentProducerTemplate.on(context)
                .withBody("Hello World")
                .to("direct:exception")
                .request();

            fail("Should have thrown RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testRequestExceptionUsingProcessor() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context)
            .withProcessor(exchange -> exchange.getIn().setBody("Hello World"))
            .to("direct:exception")
            .request(Exchange.class);

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    public void testRequestExceptionUsingExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context)
            .withExchange(() -> {
                Exchange exchange = context.getEndpoint("direct:exception").createExchange(ExchangePattern.InOut);
                exchange.getIn().setBody("Hello World");
                return exchange;
            })
            .to("direct:exception")
            .send();

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    public void testRequestBody() throws Exception {
        // with endpoint as string uri
        FluentProducerTemplate template = DefaultFluentProducerTemplate.on(context);

        final Integer expectedResult = new Integer(123);

        assertEquals(
            expectedResult,
            template.clearBody()
                .clearHeaders()
                .withBody("Hello")
                .to("direct:inout")
                .request(Integer.class)
        );

        assertEquals(
            expectedResult,
            template.clearBody()
                .clearHeaders()
                .withHeader("foo", "bar")
                .withBody("Hello")
                .to("direct:inout")
                .request(Integer.class)
        );

        assertEquals(
            expectedResult,
            template.clearBody()
                .clearHeaders()
                .withBody("Hello")
                .to("direct:inout")
                .request(Integer.class)
        );

        assertEquals(
            expectedResult,
            template.clearBody()
                .clearHeaders()
                .withBody("Hello")
                .to(context.getEndpoint("direct:inout"))
                .request(Integer.class)
        );

        assertEquals(
            expectedResult,
            template.clearBody()
                .clearHeaders()
                .withHeader("foo", "bar")
                .withBody("Hello")
                .to(context.getEndpoint("direct:inout"))
                .request(Integer.class)
        );

        assertEquals(
            expectedResult,
            template.clearBody()
                .clearHeaders()
                .withBody("Hello")
                .to(context.getEndpoint("direct:inout"))
                .request(Integer.class)
        );
    }

    public void testAsyncRequest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:async");
        mock.expectedMessageCount(2);
        mock.expectedHeaderValuesReceivedInAnyOrder("action", "action-1", "action-2");
        mock.expectedBodiesReceivedInAnyOrder("body-1", "body-2");

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();
        Future<String> future1 = fluent.to("direct:async").withHeader("action", "action-1").withBody("body-1").asyncRequest(String.class);
        Future<String> future2 = fluent.to("direct:async").withHeader("action", "action-2").withBody("body-2").asyncRequest(String.class);

        String result1 = future1.get();
        String result2 = future2.get();

        mock.assertIsSatisfied();

        assertEquals("body-1", result1);
        assertEquals("body-2", result2);

        String action = mock.getExchanges().get(0).getIn().getHeader("action", String.class);
        if (action.equals("action-1")) {
            assertEquals("body-1", mock.getExchanges().get(0).getIn().getBody(String.class));
        }
        if (action.equals("action-2")) {
            assertEquals("body-2", mock.getExchanges().get(0).getIn().getBody(String.class));
        }
    }

    public void testAsyncSend() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:async");
        mock.expectedMessageCount(2);

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();

        Future<Exchange> future1 = fluent.to("direct:async").withHeader("action", "action-1").withBody("body-1").asyncSend();
        Future<Exchange> future2 = fluent.to("direct:async").withHeader("action", "action-2").withBody("body-2").asyncSend();

        Exchange exchange1 = future1.get();
        Exchange exchange2 = future2.get();

        assertEquals("action-1", exchange1.getIn().getHeader("action", String.class));
        assertEquals("body-1", exchange1.getIn().getBody(String.class));

        assertEquals("action-2", exchange2.getIn().getHeader("action", String.class));
        assertEquals("body-2", exchange2.getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // for faster unit test
                errorHandler(noErrorHandler());

                from("direct:in")
                    .process(exchange -> exchange.getIn().setBody("Bye World"))
                    .to("mock:result");
                from("direct:sum")
                    .process(exchange -> {
                        Object body = exchange.getIn().getBody();
                        if (body instanceof Integer) {
                            exchange.getIn().setBody((Integer) body + 1);
                        } else {
                            throw new IllegalArgumentException("Expected body of type Integer");
                        }
                    })
                    .to("mock:result");
                from("direct:out")
                    .process(exchange -> exchange.getOut().setBody("Bye Bye World"))
                    .to("mock:result");
                from("direct:fault")
                    .process(exchange -> {
                        exchange.getOut().setFault(true);
                        exchange.getOut().setBody("Faulty World");
                    })
                    .to("mock:result");

                from("direct:exception")
                    .process(exchange -> {
                        throw new IllegalArgumentException("Forced exception by unit test");
                    })
                    .to("mock:result");

                from("direct:inout").transform(constant(123));

                from("direct:async")
                    .to("mock:async");
            }
        };
    }
}
