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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * Unit test to verify that Splitter aggregator does not included filtered exchanges.
 *
 * @version $Revision$
 */
public class SplitShouldSkipFilteredExchanges extends ContextTestSupport {

    public void testSplitWithFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World,Bye World");

        MockEndpoint filtered = getMockEndpoint("mock:filtered");
        filtered.expectedBodiesReceived("Hello World", "Bye World");

        List<String> body = new ArrayList<String>();
        body.add("Hello World");
        body.add("Hi there");
        body.add("Bye World");
        body.add("How do you do?");

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("direct:split")
                    .to("mock:result");

                Predicate goodWord = body().contains("World");
                from("direct:split")
                    .split(body(List.class), new MyAggregationStrategy())
                        .filter(goodWord)
                        .to("mock:filtered");
            }
        };
    }

    private class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String newBody = newExchange.getIn().getBody(String.class);
            assertTrue("Should have been filtered: " + newBody, newBody.contains("World"));

            if (oldExchange == null) {
                return newExchange;
            }

            String body = oldExchange.getIn().getBody(String.class);
            body = body + "," + newBody;
            oldExchange.getIn().setBody(body);
            return oldExchange;
        }

    }
}
