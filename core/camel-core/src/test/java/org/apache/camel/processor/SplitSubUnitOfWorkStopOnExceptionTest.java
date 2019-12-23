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
public class SplitSubUnitOfWorkStopOnExceptionTest extends ContextTestSupport {

    private static int counter;

    @Test
    public void testOK() throws Exception {
        counter = 0;

        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:a").expectedBodiesReceived("Tiger,Camel");
        getMockEndpoint("mock:b").expectedBodiesReceived("Tiger", "Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Tiger,Camel");
        getMockEndpoint("mock:line").expectedBodiesReceived("Tiger", "Camel");

        template.sendBody("direct:start", "Tiger,Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testError() throws Exception {
        counter = 0;

        getMockEndpoint("mock:dead").expectedBodiesReceived("Tiger,Donkey,Camel");
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedBodiesReceived("Tiger", "Donkey");
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:line").expectedBodiesReceived("Tiger");

        template.sendBody("direct:start", "Tiger,Donkey,Camel");

        assertMockEndpointsSatisfied();

        assertEquals(4, counter); // 1 first + 3 redeliveries
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").useOriginalMessage().maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start").to("mock:a").split(body().tokenize(",")).shareUnitOfWork().stopOnException().to("mock:b").to("direct:line").end().to("mock:result");

                from("direct:line").to("log:line").process(new MyProcessor()).to("mock:line");
            }
        };
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
