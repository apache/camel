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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test with multi route specific error handlers
 */
public class MultiErrorHandlerInRouteTest extends ContextTestSupport {
    private MyProcessor outer = new MyProcessor();
    private MyProcessor inner = new MyProcessor();

    @Test
    public void testNoErrors() throws Exception {
        outer.setName("Claus");
        inner.setName("James");

        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedHeaderReceived("name", "James");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOuterError() throws Exception {
        outer.setName("Error");
        inner.setName("James");

        MockEndpoint mock = getMockEndpoint("mock:outer");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInnerError() throws Exception {
        outer.setName("Claus");
        inner.setName("Error");

        MockEndpoint mock = getMockEndpoint("mock:inner");
        mock.expectedHeaderReceived("name", "Claus");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").errorHandler(deadLetterChannel("mock:outer").maximumRedeliveries(1).redeliveryDelay(0)).process(outer).to("direct:outer");

                from("direct:outer").errorHandler(deadLetterChannel("mock:inner").maximumRedeliveries(2).redeliveryDelay(0)).process(inner).to("mock:end");
            }
        };
    }

    private static class MyProcessor implements Processor {

        private String name;

        @Override
        public void process(Exchange exchange) throws Exception {
            if (name.equals("Error")) {
                throw new IllegalArgumentException("Forced exception by unit test");
            }
            exchange.getIn().setHeader("name", name);
        }

        public void setName(String name) {
            this.name = name;
        }
    }

}
