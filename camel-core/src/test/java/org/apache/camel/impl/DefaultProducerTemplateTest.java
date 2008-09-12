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

import org.apache.camel.ContextTestSupport;
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

        Object result = template.sendBody("direct:in", "Hello World");

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

        Object result = template.sendBody("direct:fault", "Hello World");

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

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
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
            }
        };
    }
}
