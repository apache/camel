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
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.junit.Test;

public class AggregateControllerTest extends ContextTestSupport {

    private AggregateController controller;

    public AggregateController getAggregateController() {
        if (controller == null) {
            controller = new DefaultAggregateController();
        }
        return controller;
    }

    @Test
    public void testForceCompletionOfAll() throws Exception {
        getMockEndpoint("mock:aggregated").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "test1", "id", "1");
        template.sendBodyAndHeader("direct:start", "test2", "id", "2");
        template.sendBodyAndHeader("direct:start", "test3", "id", "1");
        template.sendBodyAndHeader("direct:start", "test4", "id", "2");

        assertMockEndpointsSatisfied();

        getMockEndpoint("mock:aggregated").expectedMessageCount(2);
        getMockEndpoint("mock:aggregated").expectedBodiesReceivedInAnyOrder("test1test3", "test2test4");
        getMockEndpoint("mock:aggregated").expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "force");

        int groups = getAggregateController().forceCompletionOfAllGroups();
        assertEquals(2, groups);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testForceCompletionOfGroup() throws Exception {
        getMockEndpoint("mock:aggregated").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "test1", "id", "1");
        template.sendBodyAndHeader("direct:start", "test2", "id", "2");
        template.sendBodyAndHeader("direct:start", "test3", "id", "1");
        template.sendBodyAndHeader("direct:start", "test4", "id", "2");

        assertMockEndpointsSatisfied();

        getMockEndpoint("mock:aggregated").expectedMessageCount(1);
        getMockEndpoint("mock:aggregated").expectedBodiesReceivedInAnyOrder("test1test3");
        getMockEndpoint("mock:aggregated").expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "force");

        int groups = getAggregateController().forceCompletionOfGroup("1");
        assertEquals(1, groups);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testForceDiscardingOfGroup() throws Exception {
        getMockEndpoint("mock:aggregated").expectedMessageCount(1);
        getMockEndpoint("mock:aggregated").expectedHeaderReceived("id", "1");
        // the first 5 messages are discarded
        getMockEndpoint("mock:aggregated").message(0).body().startsWith("test6");

        template.sendBodyAndHeader("direct:start", "test1", "id", "1");
        template.sendBodyAndHeader("direct:start", "test2", "id", "1");
        template.sendBodyAndHeader("direct:start", "test3", "id", "1");
        template.sendBodyAndHeader("direct:start", "test4", "id", "1");
        template.sendBodyAndHeader("direct:start", "test5", "id", "1");

        int groups = getAggregateController().forceDiscardingOfGroup("1");
        assertEquals(1, groups);

        template.sendBodyAndHeader("direct:start", "test6", "id", "1");
        template.sendBodyAndHeader("direct:start", "test7", "id", "1");
        template.sendBodyAndHeader("direct:start", "test8", "id", "1");
        template.sendBodyAndHeader("direct:start", "test9", "id", "1");
        template.sendBodyAndHeader("direct:start", "test10", "id", "1");
        template.sendBodyAndHeader("direct:start", "test11", "id", "1");
        template.sendBodyAndHeader("direct:start", "test12", "id", "1");
        template.sendBodyAndHeader("direct:start", "test13", "id", "1");
        template.sendBodyAndHeader("direct:start", "test14", "id", "1");
        template.sendBodyAndHeader("direct:start", "test15", "id", "1");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testForceDiscardingOfAll() throws Exception {
        getMockEndpoint("mock:aggregated").expectedMessageCount(1);
        getMockEndpoint("mock:aggregated").expectedHeaderReceived("id", "1");
        // the first 5 messages are discarded
        getMockEndpoint("mock:aggregated").message(0).body().startsWith("test6");

        template.sendBodyAndHeader("direct:start", "test0", "id", "2");
        template.sendBodyAndHeader("direct:start", "test1", "id", "1");
        template.sendBodyAndHeader("direct:start", "test2", "id", "1");
        template.sendBodyAndHeader("direct:start", "test3", "id", "1");
        template.sendBodyAndHeader("direct:start", "test4", "id", "1");
        template.sendBodyAndHeader("direct:start", "test5", "id", "1");

        int groups = getAggregateController().forceDiscardingOfAllGroups();
        assertEquals(2, groups);

        template.sendBodyAndHeader("direct:start", "test6", "id", "1");
        template.sendBodyAndHeader("direct:start", "test7", "id", "1");
        template.sendBodyAndHeader("direct:start", "test8", "id", "1");
        template.sendBodyAndHeader("direct:start", "test9", "id", "1");
        template.sendBodyAndHeader("direct:start", "test10", "id", "1");
        template.sendBodyAndHeader("direct:start", "test11", "id", "1");
        template.sendBodyAndHeader("direct:start", "test12", "id", "1");
        template.sendBodyAndHeader("direct:start", "test13", "id", "1");
        template.sendBodyAndHeader("direct:start", "test14", "id", "1");
        template.sendBodyAndHeader("direct:start", "test15", "id", "1");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new MyAggregationStrategy()).aggregateController(getAggregateController()).completionSize(10).to("log:aggregated",
                                                                                                                                                              "mock:aggregated");
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }
}
