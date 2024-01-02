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
package org.apache.camel.builder;

import java.util.concurrent.Future;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.DefaultFluentProducerTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for FluentProducerTemplate
 */
public class FluentProducerTemplateTest extends ContextTestSupport {

    @Test
    public void testNoEndpoint() {
        FluentProducerTemplate fluent = context.createFluentProducerTemplate();

        FluentProducerTemplate helloWorld = fluent.withBody("Hello World");
        assertThrows(IllegalArgumentException.class, () -> helloWorld.send(),
                "Should have thrown exception");

        assertThrows(IllegalArgumentException.class, () -> helloWorld.request(),
                "Should have thrown exception");
    }

    @Test
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

    @Test
    public void testFromCamelContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();

        Object result = fluent.withBody("Hello World").to("direct:in").request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, fluent.getCamelContext());
    }

    @Test
    public void testToF() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        FluentProducerTemplate on = DefaultFluentProducerTemplate.on(context);
        Object result = on.withBody("Hello World").toF("direct:%s", "in").request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, template.getCamelContext());
    }

    @Test
    public void testWithDefaultEndpoint() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        FluentProducerTemplate template = DefaultFluentProducerTemplate.on(context, "direct:in");

        Object result = template.withBody("Hello World").request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, template.getCamelContext());
    }

    @Test
    public void testIn() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        Object result = DefaultFluentProducerTemplate.on(context).withBody("Hello World").to("direct:in").request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);

        assertSame(context, template.getCamelContext());
    }

    @Test
    public void testInTwice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World", "Bye World");

        FluentProducerTemplate template = DefaultFluentProducerTemplate.on(context);

        Object result = template.withBody("Hello World").to("direct:in").request();
        Object result2 = template.withBody("Hello World Again").to("direct:in").request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);
        assertEquals("Bye World", result2);

        assertSame(context, template.getCamelContext());
    }

    @Test
    public void testInOut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Bye World");

        Object result = DefaultFluentProducerTemplate.on(context).withBody("Hello World").to("direct:out").request();

        assertMockEndpointsSatisfied();

        assertEquals("Bye Bye World", result);
    }

    @Test
    public void testInOutWithBodyConversion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(11);

        Object result = DefaultFluentProducerTemplate.on(context).withBodyAs("10", Integer.class).to("direct:sum").request();

        assertMockEndpointsSatisfied();

        assertEquals(11, result);
    }

    @Test
    public void testInOutWithBodyConversionFault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            DefaultFluentProducerTemplate.on(context).withBodyAs("10", Double.class).to("direct:sum").request();
        } catch (CamelExecutionException e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertEquals("Expected body of type Integer", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionUsingBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context).withBody("Hello World").to("direct:exception").send();

        assertTrue(out.isFailed());
        boolean b = out.getException() instanceof IllegalArgumentException;
        assertTrue(b);
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionUsingProcessor() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context)
                .withProcessor(exchange -> exchange.getIn().setBody("Hello World")).to("direct:exception").send();

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionUsingExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context).withExchange(() -> {
            Exchange exchange = context.getEndpoint("direct:exception").createExchange();
            exchange.getIn().setBody("Hello World");
            return exchange;
        }).to("direct:exception").send();

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionUsingProcessorAndBody() {
        assertThrows(IllegalArgumentException.class, () -> DefaultFluentProducerTemplate.on(context)
                .withBody("World")
                .withProcessor(exchange -> exchange.getIn().setHeader("foo", 123)).to("direct:async").send(), "");
    }

    @Test
    public void testRequestExceptionUsingBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            DefaultFluentProducerTemplate.on(context).withBody("Hello World").to("direct:exception").request();
            fail("Should have thrown RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRequestExceptionUsingProcessor() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out
                = DefaultFluentProducerTemplate.on(context).withProcessor(exchange -> exchange.getIn().setBody("Hello World"))
                        .to("direct:exception").request(Exchange.class);

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRequestExceptionUsingExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = DefaultFluentProducerTemplate.on(context).withExchange(() -> {
            Exchange exchange = context.getEndpoint("direct:exception").createExchange(ExchangePattern.InOut);
            exchange.getIn().setBody("Hello World");
            return exchange;
        }).to("direct:exception").send();

        assertTrue(out.isFailed());
        assertEquals("Forced exception by unit test", out.getException().getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithExchange() {
        Exchange exchange = ExchangeBuilder.anExchange(context).withBody("Hello!").withPattern(ExchangePattern.InOut).build();

        exchange = context.createFluentProducerTemplate().withExchange(exchange).to("direct:in").send();

        assertEquals("Bye World", exchange.getMessage().getBody());

        String str = "withExchange not supported on FluentProducerTemplate.request method. Use send method instead.";

        Exchange finalExchange = exchange;
        Exception e = assertThrows(IllegalArgumentException.class, () -> context.createFluentProducerTemplate()
                .withExchange(finalExchange)
                .to("direct:in")
                .request(String.class), "Should throw exception");

        assertEquals(str, e.getMessage());
    }

    @Test
    public void testRequestBody() {
        // with endpoint as string uri
        FluentProducerTemplate template = DefaultFluentProducerTemplate.on(context);

        final Integer expectedResult = Integer.valueOf(123);

        assertEquals(expectedResult,
                template.withBody("Hello").to("direct:inout").request(Integer.class));

        assertEquals(expectedResult, template.withHeader("foo", "bar").withBody("Hello")
                .to("direct:inout").request(Integer.class));

        assertEquals(expectedResult,
                template.withBody("Hello").to("direct:inout").request(Integer.class));

        assertEquals(expectedResult, template.withBody("Hello")
                .to(context.getEndpoint("direct:inout")).request(Integer.class));

        assertEquals(expectedResult, template.withHeader("foo", "bar").withBody("Hello")
                .to(context.getEndpoint("direct:inout")).request(Integer.class));

        assertEquals(expectedResult, template.withBody("Hello")
                .to(context.getEndpoint("direct:inout")).request(Integer.class));
    }

    @Test
    public void testWithVariable() {
        FluentProducerTemplate template = DefaultFluentProducerTemplate.on(context);

        assertEquals("Hello World", template.withVariable("foo", "World").withBody("Hello")
                .to("direct:var").request(String.class));

        assertEquals("Hello Moon", template.withVariable("foo", "Moon").withVariable("global:planet", "Mars").withBody("Hello")
                .to("direct:var").request(String.class));
        assertEquals("Mars", context.getVariable("planet"));
    }

    @Test
    public void testWithExchangeProperty() {
        FluentProducerTemplate template = DefaultFluentProducerTemplate.on(context);

        assertEquals("Hello World", template.withExchangeProperty("foo", "World").withBody("Hello")
                .to("direct:ep").request(String.class));

        assertEquals("Hello Moon",
                template.withExchangeProperty("foo", "Moon").withExchangeProperty("planet", "Mars").withBody("Hello")
                        .to("direct:ep").request(String.class));
    }

    @Test
    public void testAsyncRequest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:async");
        mock.expectedMessageCount(2);
        mock.expectedHeaderValuesReceivedInAnyOrder("action", "action-1", "action-2");
        mock.expectedBodiesReceivedInAnyOrder("body-1", "body-2");

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();
        Future<String> future1
                = fluent.to("direct:async").withHeader("action", "action-1").withBody("body-1").asyncRequest(String.class);
        Future<String> future2
                = fluent.to("direct:async").withHeader("action", "action-2").withBody("body-2").asyncRequest(String.class);

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

    @Test
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

    @Test
    public void testWithCustomizer() throws Exception {
        getMockEndpoint("mock:custom").expectedBodiesReceived("Hello World");

        FluentProducerTemplate fluent
                = context.createFluentProducerTemplate().withTemplateCustomizer(t -> t.setDefaultEndpointUri("mock:custom"));

        fluent.withBody("Hello World").send();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUseTwoTimesSameThread() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:echo");
        mock.expectedBodiesReceived("Camel", "World");
        mock.message(0).header("foo").isEqualTo("!");
        mock.message(1).header("foo").isNull();

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();
        Object result = fluent.withBody("Camel").withHeader("foo", "!").to("direct:echo").request();
        Object result2 = fluent.withBody("World").to("direct:echo").request();
        assertEquals("CamelCamel!", result);
        assertEquals("WorldWorld", result2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUseFourTimesSameThread() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:echo");
        mock.expectedBodiesReceived("Camel", "Beer");
        mock.message(0).header("foo").isEqualTo("!");
        mock.message(1).header("foo").isNull();

        FluentProducerTemplate fluent = context.createFluentProducerTemplate();
        fluent.setDefaultEndpointUri("direct:red");
        Object result = fluent.withBody("Camel").withHeader("foo", "!").to("direct:echo").request();
        Object result2 = fluent.withBody("World").to("direct:hi").request();
        Object result3 = fluent.withBody("Beer").to("direct:echo").request();
        Object result4 = fluent.withBody("Wine").request();
        assertEquals("CamelCamel!", result);
        assertEquals("Hi World", result2);
        assertEquals("BeerBeer", result3);
        assertEquals("Red Wine", result4);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPerformance() {
        FluentProducerTemplate fluent = context.createFluentProducerTemplate();
        for (int i = 0; i < 1000; i++) {
            Object result = fluent.withBody("Camel").withHeader("foo", "" + i).to("direct:echo").request();
            assertEquals("CamelCamel" + i, result);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // for faster unit test
                errorHandler(noErrorHandler());

                from("direct:in").process(exchange -> exchange.getIn().setBody("Bye World")).to("mock:result");
                from("direct:sum").process(exchange -> {
                    Object body = exchange.getIn().getBody();
                    if (body instanceof Integer) {
                        exchange.getIn().setBody((Integer) body + 1);
                    } else {
                        throw new IllegalArgumentException("Expected body of type Integer");
                    }
                }).to("mock:result");
                from("direct:out").process(exchange -> exchange.getMessage().setBody("Bye Bye World")).to("mock:result");

                from("direct:exception").process(exchange -> {
                    throw new IllegalArgumentException("Forced exception by unit test");
                }).to("mock:result");

                from("direct:inout").transform(constant(123));

                from("direct:async").to("mock:async");

                from("direct:echo").to("mock:echo").setBody().simple("${body}${body}${header.foo}");

                from("direct:hi").setBody().simple("Hi ${body}");

                from("direct:red").setBody().simple("Red ${body}");

                from("direct:var").transform().simple("${body} ${variable.foo}");

                from("direct:ep").transform().simple("${body} ${exchangeProperty.foo}");
            }
        };
    }
}
