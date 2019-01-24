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
import org.apache.camel.util.StopWatch;
import org.junit.Test;

/**
 * Tests that the redelivery error handler will break out if CamelContext is shutting down.
 */
public class RedeliveryErrorHandlerBreakoutDuringShutdownTest extends ContextTestSupport {

    @Test
    public void testRedelivery() throws Exception {

        getMockEndpoint("mock:before").expectedMessageCount(1);
        getMockEndpoint("mock:after").expectedMessageCount(0);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        // use a stop watch to time how long it takes to force the shutdown
        StopWatch watch = new StopWatch();

        // force quicker shutdown
        context.getShutdownStrategy().setTimeout(1);
        context.stop();

        // should take less than 5 seconds
        assertTrue("Should take less than 5 seconds, was {}", watch.taken() < 5000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // just keep on redelivering
                errorHandler(defaultErrorHandler().maximumRedeliveries(-1).redeliveryDelay(1000));

                from("seda:start")
                    .to("mock:before")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            throw new IllegalArgumentException("Forced");
                        }
                    })
                    .to("mock:after");
            }
        };
    }
}
