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
package org.apache.camel.processor.interceptor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class DefaultTraceEventMessageTest extends ContextTestSupport {

    public void testDefaultTraceEventMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        MockEndpoint traced = getMockEndpoint("mock:traced");
        traced.expectedMessageCount(2);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                exchange.setProperty("foo", 123);
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader("bar", "456");
                exchange.getOut().setBody("Bye World");
                exchange.getOut().setHeader("cheese", 789);
            }
        });

        assertMockEndpointsSatisfied();

        DefaultTraceEventMessage em = traced.getReceivedExchanges().get(0).getIn().getBody(DefaultTraceEventMessage.class);
        assertNotNull(em);
        assertNotNull(em.getTimestamp());
        assertEquals("direct://start", em.getFromEndpointUri());
        assertEquals(null, em.getPreviousNode());
        assertEquals("mock://foo", em.getToNode());
        assertNotNull(em.getExchangeId());
        assertNotNull(em.getShortExchangeId());
        assertEquals("InOut", em.getExchangePattern());
        assertTrue(em.getProperties().contains("foo=123"));
        assertTrue(em.getProperties().contains("CamelToEndpoint=direct://start"));
        assertTrue(em.getProperties().contains("CamelCreatedTimestamp"));
        assertTrue(em.getHeaders().contains("bar=456"));
        assertEquals("Hello World", em.getBody());
        assertEquals("String", em.getBodyType());
        assertEquals("Bye World", em.getOutBody());
        assertEquals("String", em.getOutBodyType());
        assertEquals("{cheese=789}", em.getOutHeaders());
    }
    
    public void testDefaultTraceEventMessageBody() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("target/test"));
        DefaultTraceEventMessage em = new DefaultTraceEventMessage(new Date(), null, exchange);
        
        assertEquals("Get a wrong body string", "[Body is file based: target" + File.separator + "test]", em.getBody());
        
        exchange.getIn().setBody(new ByteArrayInputStream("target/test".getBytes()));
        em = new DefaultTraceEventMessage(new Date(), null, exchange);
        
        assertEquals("Get a wrong body string", "[Body is instance of java.io.InputStream]", em.getBody());
    }
 
    public void testDefaultTraceEventMessageOptions() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        DefaultTraceEventMessage em = new DefaultTraceEventMessage(new Date(), null, exchange);
        
        em.setBody("Hello World");
        assertEquals("Hello World", em.getBody());

        em.setBodyType("String");
        assertEquals("String", em.getBodyType());

        em.setCausedByException("Damn");
        assertEquals("Damn", em.getCausedByException());

        em.setExchangeId("123");
        assertEquals("123", em.getExchangeId());

        em.setExchangePattern("InOnly");
        assertEquals("InOnly", em.getExchangePattern());

        em.setFromEndpointUri("direct://start");
        assertEquals("direct://start", em.getFromEndpointUri());

        em.setHeaders("{foo=123}");
        assertEquals("{foo=123}", em.getHeaders());

        em.setOutBody("123");
        assertEquals("123", em.getOutBody());

        em.setOutBodyType("Integer");
        assertEquals("Integer", em.getOutBodyType());

        em.setOutHeaders("{cheese=789}");
        assertEquals("{cheese=789}", em.getOutHeaders());

        em.setProperties("{foo=123}");
        assertEquals("{foo=123}", em.getProperties());

        em.setPreviousNode("A");
        assertEquals("A", em.getPreviousNode());

        em.setToNode("B");
        assertEquals("B", em.getToNode());

        em.setTimestamp(new Date());
        assertNotNull(em.getTimestamp());

        em.setShortExchangeId("123");
        assertEquals("123", em.getShortExchangeId());

        assertSame(exchange, em.getTracedExchange());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = Tracer.createTracer(context);
                tracer.setDestinationUri("mock:traced");
                context.addInterceptStrategy(tracer);

                from("direct:start").to("mock:foo").to("mock:result");
            }
        };
    }
}
