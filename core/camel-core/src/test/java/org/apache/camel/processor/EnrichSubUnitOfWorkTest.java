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
import org.junit.Test;

/**
 *
 */
public class EnrichSubUnitOfWorkTest extends ContextTestSupport {

    private static int counter;

    @Test
    public void testOK() throws Exception {
        counter = 0;

        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:start").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testError() throws Exception {
        counter = 0;

        // the DLC should receive the original message which is Bye World
        getMockEndpoint("mock:dead").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:start").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(4, counter); // 1 first + 3 redeliveries
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").useOriginalMessage().maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start").to("mock:start").process(new MyPreProcessor()).enrich("direct:b", null, false, true).to("mock:result");

                from("direct:b").process(new MyProcessor()).to("mock:b");
            }
        };
    }

    public static class MyPreProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            // if its a bye message then alter it to something with
            // Donkey to cause a failure in the sub unit of work
            // but the DLC should still receive the original input
            String body = exchange.getIn().getBody(String.class);
            if (body.startsWith("Bye")) {
                exchange.getIn().setBody("Donkey was here");
            }
        }
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if (body.contains("Donkey")) {
                counter++;
                throw new IllegalArgumentException("Donkey not allowed");
            }
        }
    }

}
