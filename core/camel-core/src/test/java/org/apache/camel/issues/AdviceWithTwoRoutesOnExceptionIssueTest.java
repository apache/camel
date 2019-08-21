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
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

/**
 *
 */
public class AdviceWithTwoRoutesOnExceptionIssueTest extends ContextTestSupport {

    @Test
    public void testAdviceWith() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinition("a"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:a").skipSendToOriginalEndpoint().to("mock:error");
            }
        });

        RouteReifier.adviceWith(context.getRouteDefinition("b"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:b").skipSendToOriginalEndpoint().to("mock:error");
            }
        });

        getMockEndpoint("mock:error").whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                String body = exchange.getIn().getBody(String.class);
                throw new IllegalArgumentException("Forced " + body);
            }
        });

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        // whenAnyExchange is invoked after the mock receive the exchange
        getMockEndpoint("mock:error").expectedBodiesReceived("A", "B");
        // the onException should handle and send the message to this mock
        getMockEndpoint("mock:handled").expectedBodiesReceived("Handling Forced A", "Handling Forced B");

        Object outA = template.requestBody("direct:a", "A");
        assertEquals("Handling Forced A", outA);

        Object outB = template.requestBody("direct:b", "B");
        assertEquals("Handling Forced B", outB);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).setBody(simple("Handling ${exception.message}")).to("mock:handled");

                from("direct:a").routeId("a").to("mock:a");

                from("direct:b").routeId("b").to("mock:b");
            }
        };
    }
}
