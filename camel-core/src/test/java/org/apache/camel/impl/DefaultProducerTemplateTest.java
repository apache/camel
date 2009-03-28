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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for DefaultProducerTemplate extractResultBody method.
 */
public class DefaultProducerTemplateTest extends ContextTestSupport {

    public void testIn() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        Object result = template.requestBody("direct:in", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", result);
    }

    public void testInOut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Bye World");

        Object result = template.requestBody("direct:out", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("Bye Bye World", result);
    }

    public void testFault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Object result = template.requestBody("direct:fault", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("Faulty World", result);
    }

    public void testException() throws Exception {
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

    public void testRequestBody() throws Exception {
        // with endpoint as string uri
        Integer out = template.requestBody("direct:inout", "Hello", Integer.class);
        assertEquals(new Integer(123), out);

        out = template.requestBodyAndHeader("direct:inout", "Hello", "foo", "bar", Integer.class);
        assertEquals(new Integer(123), out);

        Map headers = new HashMap();
        out = template.requestBodyAndHeaders("direct:inout", "Hello", headers, Integer.class);
        assertEquals(new Integer(123), out);

        // with endpoint object
        Endpoint endpoint = context.getEndpoint("direct:inout");
        out = template.requestBody(endpoint, "Hello", Integer.class);
        assertEquals(new Integer(123), out);

        out = template.requestBodyAndHeader(endpoint, "Hello", "foo", "bar", Integer.class);
        assertEquals(new Integer(123), out);

        headers = new HashMap();
        out = template.requestBodyAndHeaders(endpoint, "Hello", headers, Integer.class);
        assertEquals(new Integer(123), out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // for faster unit test
                errorHandler(noErrorHandler());

                from("direct:in").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setBody("Bye World");
                    }
                }).to("mock:result");

                from("direct:out").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("Bye Bye World");
                    }
                }).to("mock:result");

                from("direct:fault").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getFault().setBody("Faulty World");
                    }
                }).to("mock:result");

                from("direct:exception").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced exception by unit test");
                    }
                }).to("mock:result");

                from("direct:inout").transform(constant(123));
            }
        };
    }
}
