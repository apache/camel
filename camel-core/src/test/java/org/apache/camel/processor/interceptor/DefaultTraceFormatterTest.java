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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class DefaultTraceFormatterTest extends ContextTestSupport {

    public void testDefaultTraceFormatter() {
        getFormatter();
    }

    public void testFormat() throws Exception {
        Tracer tracer = new Tracer();
        tracer.setFormatter(getFormatter());
        context.addInterceptStrategy(tracer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader("foo", 123);
                exchange.getOut().setBody("Bye World");
                exchange.getOut().setHeader("bar", 456);
                exchange.setProperty("quote", "Camel is cool");
            }
        });

        assertMockEndpointsSatisfied();
    }

    public void testWithException() throws Exception {
        Tracer tracer = new Tracer();
        tracer.setFormatter(getFormatter());
        context.addInterceptStrategy(tracer);

        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // ignore
        }
    }

    public void testNoShow() throws Exception {
        DefaultTraceFormatter formatter = getFormatter();
        formatter.setShowBreadCrumb(false);
        formatter.setShowExchangeId(false);
        formatter.setShowShortExchangeId(false);
        formatter.setShowNode(false);

        Tracer tracer = new Tracer();
        tracer.setFormatter(formatter);
        context.addInterceptStrategy(tracer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");

                from("direct:fail").to("mock:mid").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }

    private DefaultTraceFormatter getFormatter() {
        DefaultTraceFormatter formatter = new DefaultTraceFormatter();

        formatter.setBreadCrumbLength(30);
        assertEquals(30, formatter.getBreadCrumbLength());

        formatter.setMaxChars(500);
        assertEquals(500, formatter.getMaxChars());

        formatter.setNodeLength(20);
        assertEquals(20, formatter.getNodeLength());

        formatter.setShowBody(true);
        assertEquals(true, formatter.isShowBody());

        formatter.setBreadCrumbLength(40);
        assertEquals(40, formatter.getBreadCrumbLength());

        formatter.setShowBody(true);
        assertEquals(true, formatter.isShowBody());

        formatter.setShowBodyType(true);
        assertEquals(true, formatter.isShowBodyType());

        formatter.setShowBreadCrumb(true);
        assertEquals(true, formatter.isShowBreadCrumb());

        formatter.setShowExchangeId(true);
        assertEquals(true, formatter.isShowExchangeId());

        formatter.setShowException(true);
        assertEquals(true, formatter.isShowException());

        formatter.setShowExchangePattern(true);
        assertEquals(true, formatter.isShowExchangePattern());

        formatter.setShowHeaders(true);
        assertEquals(true, formatter.isShowHeaders());

        formatter.setShowNode(true);
        assertEquals(true, formatter.isShowNode());

        formatter.setShowOutBody(true);
        assertEquals(true, formatter.isShowOutBody());

        formatter.setShowOutBodyType(true);
        assertEquals(true, formatter.isShowOutBodyType());

        formatter.setShowOutHeaders(true);
        assertEquals(true, formatter.isShowOutHeaders());

        formatter.setShowProperties(true);
        assertEquals(true, formatter.isShowProperties());
        
        formatter.setMultiline(true);
        assertEquals(true, formatter.isMultiline());

        formatter.setShowShortExchangeId(true);
        assertEquals(true, formatter.isShowShortExchangeId());

        formatter.setShowRouteId(false);
        assertEquals(false, formatter.isShowRouteId());

        return formatter;
    }

}
