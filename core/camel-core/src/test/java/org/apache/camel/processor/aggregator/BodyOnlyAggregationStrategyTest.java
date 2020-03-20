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
package org.apache.camel.processor.aggregator;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test for CAMEL-14684
 */
public class BodyOnlyAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void exceptionRouteTest() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:failingRoute").expectedMessageCount(1);
        getMockEndpoint("mock:nextRoute").expectedMessageCount(0);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                DeadLetterChannelBuilder deadLetterChannelBuilder = deadLetterChannel("direct:error");
                deadLetterChannelBuilder.setUseOriginalMessage(true);

                from("direct:failingRoute")
                        .errorHandler(deadLetterChannelBuilder)
                        .to("mock:failingRoute")
                        .throwException(new RuntimeException("Boem!"));

                from("direct:error")
                        .to("mock:error");

                from("direct:nextRoute")
                        .to("mock:nextRoute");

                from("direct:start")
                        .enrich("direct:failingRoute", new BodyOnlyAggregationStrategy())
                        .to("direct:nextRoute");
            }
        };
    }

    /**
     * This aggregation strategy will only take the body from the called route and returns the old exchange.
     * Only the property CamelErrorHandlerHandled is taken from the route to make sure the route stops on an exception.
     */
    public static class BodyOnlyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            oldExchange.getIn().setBody(newExchange.getIn().getBody());

            oldExchange.adapt(ExtendedExchange.class).setErrorHandlerHandled(
                    newExchange.adapt(ExtendedExchange.class).getErrorHandlerHandled()
            );
            // deprecated usage:
            // if (newExchange.getProperties().get(Exchange.ERRORHANDLER_HANDLED) != null) {
            //    oldExchange.getProperties().put(Exchange.ERRORHANDLER_HANDLED, newExchange.getProperties().get(Exchange.ERRORHANDLER_HANDLED));
            //}

            return oldExchange;
        }
    }
}
