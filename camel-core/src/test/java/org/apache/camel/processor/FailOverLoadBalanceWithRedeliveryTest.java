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
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class FailOverLoadBalanceWithRedeliveryTest extends ContextTestSupport {

    private static int counter;

    public void testFailoverWithRedelivery() throws Exception {
        counter = 0;

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(3);

        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMessageCount(2);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:start")
                    .loadBalance().failover().to("direct:a", "direct:b");

                from("direct:a")
                    // disable redelivery here as most often your load balancer over external
                    // endpoints you do not have control off, such as a web service call
                    // but we use mock for unit testing so no error handler here please
                    .errorHandler(noErrorHandler())
                    .to("mock:a")
                    .throwException(new IllegalArgumentException("I cannot do this"));

                from("direct:b")
                    // disable redelivery here as most often your load balancer over external
                    // endpoints you do not have control off, such as a web service call
                    // but we use mock for unit testing so no error handler here please
                    .errorHandler(noErrorHandler())
                    .to("mock:b")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            // fail on the first try but succeed on the 2nd try
                            if (counter++ < 1) {
                                throw new IllegalArgumentException("I can still not do this");
                            }
                            exchange.getIn().setBody("Bye World");
                        }
                    }).to("mock:result");
            }
        };
    }
}
