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
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * The inheritErrorHandler option of the failover load balancer decides whether Camel error handler does its
 * redeliveries on a failing endpoint before the load balancer fails over, or the load balancer fails over immediately
 * (CAMEL-24749).
 */
public class FailOverLoadBalanceInheritErrorHandlerTest extends ContextTestSupport {

    @Test
    public void testNotInheritErrorHandlerFailsOverImmediately() throws Exception {
        // fails over on the first error so the bad endpoint is only called once
        getMockEndpoint("mock:bad").whenAnyExchangeReceived(e -> {
            throw new IllegalStateException("Forced");
        });
        getMockEndpoint("mock:bad").expectedMessageCount(1);
        getMockEndpoint("mock:good").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:noInherit", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInheritErrorHandlerRedeliversFirst() throws Exception {
        // the error handler does its 2 redeliveries on the bad endpoint before the load balancer fails over
        getMockEndpoint("mock:bad").whenAnyExchangeReceived(e -> {
            throw new IllegalStateException("Forced");
        });
        getMockEndpoint("mock:bad").expectedMessageCount(3);
        getMockEndpoint("mock:good").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:inherit", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:noInherit")
                        .loadBalance().failover(-1, false, true)
                        .to("mock:bad").to("mock:good")
                        .end()
                        .to("mock:result");

                from("direct:inherit")
                        .loadBalance().failover(-1, true, true)
                        .to("mock:bad").to("mock:good")
                        .end()
                        .to("mock:result");
            }
        };
    }

}
