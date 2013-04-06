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
package org.apache.camel.issues;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class SameRouteAndContextScopedErrorHandlerIssueTest extends ContextTestSupport {

    private final AtomicInteger counter = new AtomicInteger();

    public void testSame() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(2, counter.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0));

                onException(IllegalArgumentException.class).onRedelivery(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        log.info("OnRedelivery invoked");
                        counter.incrementAndGet();
                    }
                });

                from("direct:start")
                    .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                    .process(new Processor() {
                        private int counter;
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            if (counter++ < 2) {
                                throw new IllegalArgumentException("Damn");
                            }
                            exchange.getIn().setBody("Bye World");
                        }
                    })
                    .to("log:result")
                    .to("mock:result");
            }
        };
    }
}
