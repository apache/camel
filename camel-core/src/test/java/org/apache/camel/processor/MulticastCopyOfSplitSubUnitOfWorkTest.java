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
public class MulticastCopyOfSplitSubUnitOfWorkTest extends ContextTestSupport {

    private static int counter;

    public void testOK() throws Exception {
        counter = 0;

        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:line").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testError() throws Exception {
        counter = 0;

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:line").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello Donkey");

        assertMockEndpointsSatisfied();

        assertEquals(4, counter); // 1 first + 3 redeliveries
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                errorHandler(deadLetterChannel("mock:dead").useOriginalMessage()
                        .maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start")
                    .to("mock:a")
                    // share unit of work in the multicast, which tells Camel to propagate failures from
                    // processing the multicast messages back to the result of the splitter, which allows
                    // it to act as a combined unit of work
                    .multicast().shareUnitOfWork()
                        .to("mock:b")
                        .to("direct:line")
                    .end()
                    .to("mock:result");

                from("direct:line")
                    .to("log:line")
                    .process(new MyProcessor())
                    .to("mock:line");
                // END SNIPPET: e1
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
