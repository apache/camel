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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class AggregateDiscardOnFailureTest extends ContextTestSupport {

    @Test
    public void testAggregateDiscardOnAggregationFailureFirst() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Kaboom", "id", 123);

        mock.assertIsSatisfied();

        // send in a new group's with same correlation key but should not fail
        mock.reset();
        mock.expectedBodiesReceived("ABC", "DEF");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);

        template.sendBodyAndHeader("direct:start", "D", "id", 456);
        template.sendBodyAndHeader("direct:start", "E", "id", 456);

        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "F", "id", 456);

        mock.assertIsSatisfied();
    }

    @Test
    public void testAggregateDiscardOnAggregationFailureMiddle() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "Kaboom", "id", 123);

        mock.assertIsSatisfied();

        // send in a new group's with same correlation key but should not fail
        mock.reset();
        mock.expectedBodiesReceived("ABC", "DEF");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);

        template.sendBodyAndHeader("direct:start", "D", "id", 456);
        template.sendBodyAndHeader("direct:start", "E", "id", 456);

        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "F", "id", 456);

        mock.assertIsSatisfied();
    }

    @Test
    public void testAggregateDiscardOnAggregationFailureLast() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "Kaboom", "id", 123);

        mock.assertIsSatisfied();

        // send in a new group's with same correlation key but should not fail
        mock.reset();
        mock.expectedBodiesReceived("ABC", "DEF");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);

        template.sendBodyAndHeader("direct:start", "D", "id", 456);
        template.sendBodyAndHeader("direct:start", "E", "id", 456);

        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "F", "id", 456);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start").aggregate(header("id"), new MyAggregationStrategy()).completionSize(3).completionTimeout(2000)
                    // and if an exception happens in aggregate then discard the
                    // message
                    .discardOnAggregationFailure().to("mock:aggregated");
                // END SNIPPET: e1
            }
        };
    }

    private class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if ("Kaboom".equals(newExchange.getMessage().getBody())) {
                throw new IllegalArgumentException("Forced");
            }

            if (oldExchange == null) {
                return newExchange;
            }

            Object body = oldExchange.getMessage().getBody(String.class) + newExchange.getMessage().getBody(String.class);
            oldExchange.getMessage().setBody(body);
            return oldExchange;
        }
    }
}
