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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class OnExceptionContinuedIssueTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOnExceptionWrappedMatch() throws Exception {
        final DefaultErrorHandlerBuilder defaultErrorHandlerBuilder = new DeadLetterChannelBuilder("direct:dead");
        defaultErrorHandlerBuilder.redeliveryDelay(0); // run fast
        defaultErrorHandlerBuilder.maximumRedeliveries(2);

        context.adapt(ExtendedCamelContext.class).setErrorHandlerFactory(defaultErrorHandlerBuilder);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(false);

                onException(OrderFailedException.class).maximumRedeliveries(0).continued(true);

                from("direct:dead").to("log:dead", "mock:dead");

                from("direct:order").to("mock:one").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        log.info("First Processor Invoked");
                        throw new OrderFailedException("First Processor Failure");
                    }
                }).to("mock:two").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        log.info("Second Processor Invoked");
                    }
                }).to("mock:three").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        log.info("Third Processor Invoked");
                        throw new RuntimeException("Some Runtime Exception");
                    }
                }).to("mock:four").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        log.info("Fourth Processor Invoked");
                    }
                });
            }
        });
        context.start();

        // we should only get 1 to the DLC when we hit the 3rd route that
        // throws the runtime exception
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:one").expectedMessageCount(1);
        getMockEndpoint("mock:two").expectedMessageCount(1);
        getMockEndpoint("mock:three").expectedMessageCount(1);
        getMockEndpoint("mock:four").expectedMessageCount(0);

        template.requestBody("direct:order", "Camel in Action");

        assertMockEndpointsSatisfied();
    }

    public class OrderFailedException extends Exception {

        public OrderFailedException(String s) {
            super(s);
        }
    }

}
