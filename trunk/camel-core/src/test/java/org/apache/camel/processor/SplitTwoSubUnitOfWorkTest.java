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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class SplitTwoSubUnitOfWorkTest extends ContextTestSupport {

    private static int counter;

    public void testOK() throws Exception {
        counter = 0;

        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedBodiesReceived("Tiger", "Camel");
        getMockEndpoint("mock:c").expectedBodiesReceived("Elephant", "Lion");
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:line").expectedBodiesReceived("Tiger", "Camel", "Elephant", "Lion");

        MyBody body = new MyBody("Tiger,Camel", "Elephant,Lion");
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    public void testError() throws Exception {
        counter = 0;

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:dead").message(0).body().isInstanceOf(MyBody.class);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedBodiesReceived("Tiger", "Camel");
        getMockEndpoint("mock:c").expectedBodiesReceived("Elephant", "Donkey");
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:line").expectedBodiesReceived("Tiger", "Camel", "Elephant");

        MyBody body = new MyBody("Tiger,Camel", "Elephant,Donkey");
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        assertEquals(4, counter); // 1 first + 3 redeliveries

        MyBody dead = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getIn().getBody(MyBody.class);
        assertSame("Should be original message in DLC", body, dead);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").useOriginalMessage()
                        .maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start")
                    .to("mock:a")
                    .split(simple("${body.foo}")).shareUnitOfWork()
                        .to("mock:b")
                        .to("direct:line")
                    .end()
                    .split(simple("${body.bar}")).shareUnitOfWork()
                        .to("mock:c")
                        .to("direct:line")
                    .end()
                    .to("mock:result");

                from("direct:line")
                    .to("log:line")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);
                            if (body.contains("Donkey")) {
                                counter++;
                                throw new IllegalArgumentException("Donkey not allowed");
                            }
                        }
                    })
                    .to("mock:line");
            }
        };
    }

    public static final class MyBody {
        private String foo;
        private String bar;

        private MyBody(String foo, String bar) {
            this.foo = foo;
            this.bar = bar;
        }

        public String getFoo() {
            return foo;
        }

        public String getBar() {
            return bar;
        }
    }

}
