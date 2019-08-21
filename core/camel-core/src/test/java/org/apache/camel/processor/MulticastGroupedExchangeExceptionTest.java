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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;

public class MulticastGroupedExchangeExceptionTest extends ContextTestSupport {

    @Test
    public void testBothGood() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody("direct:start", "dummy");

        assertMockEndpointsSatisfied();

        Exchange received = result.getReceivedExchanges().get(0);
        assertThat("no exception", received.isFailed(), is(false));
    }

    @Test
    public void testBFail() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        MockEndpoint endpointB = getMockEndpoint("mock:endpointB");
        endpointB.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new IllegalArgumentException("Fake exception");
            }
        });

        template.sendBody("direct:start", "dummy");

        assertMockEndpointsSatisfied();

        Exchange received = result.getReceivedExchanges().get(0);
        assertThat("no exception", received.isFailed(), is(false));
    }

    @Test
    public void testAFail() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        MockEndpoint endpointA = getMockEndpoint("mock:endpointA");
        endpointA.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new IllegalArgumentException("Fake exception");
            }
        });

        template.sendBody("direct:start", "dummy");

        assertMockEndpointsSatisfied();

        Exchange received = result.getReceivedExchanges().get(0);
        assertThat("no exception", received.isFailed(), is(false));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").multicast(new GroupedExchangeAggregationStrategy()).to("mock:endpointA", "mock:endpointB").end().to("mock:result");

            }
        };
    }

}
