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
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test to verify that using DLC with redelivery and delays that we are not blocking
 * the caller thread, for instance if we have delays in miniutes.
 *
 * @version $Revision$
 */
public class DeadLetterChannelRedeliverWithDelayNotBlockingTest extends ContextTestSupport {

    private static int counter;

    public void testRedeliverWithDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // TODO: when we get the internal API reworked we should be able to receive in this order
        // mock.expectedBodiesReceived("Message 2", "Message 1");

        mock.expectedBodiesReceived("Message 1", "Message 2");
        mock.expectedHeaderReceived("foo", "bar");

        template.sendBody("seda:start", "Message 1");
        template.sendBody("seda:start", "Message 2");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").delay(1000).maximumRedeliveries(3).logStackTrace(false));

                from("seda:start")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                if ("Message 1".equals(body) && counter++ < 2) {
                                    throw new IllegalArgumentException("Damn");
                                }
                                exchange.getIn().setHeader("foo", "bar");
                            }
                        }).to("mock:result");
            }
        };
    }
}
