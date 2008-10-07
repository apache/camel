package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * Unit test for using our own aggregation strategy.
 */
public class CustomAggregationStrategyTest extends ContextTestSupport {

    public void testCustomAggregationStrategy() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect to find the two winners with the highest bid
        result.expectedMessageCount(2);
        result.expectedBodiesReceived("200", "150");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "100", "id", "1");
        template.sendBodyAndHeader("direct:start", "150", "id", "2");
        template.sendBodyAndHeader("direct:start", "130", "id", "2");
        template.sendBodyAndHeader("direct:start", "200", "id", "1");
        template.sendBodyAndHeader("direct:start", "190", "id", "1");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e2
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id and use our own strategy how to aggregate
                    .aggregator(new MyAggregationStrategy()).header("id")
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e3
    private static class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            int oldPrice = oldExchange.getIn().getBody(Integer.class);
            int newPrice = newExchange.getIn().getBody(Integer.class);
            // return the "winner" that has the highest price
            return newPrice > oldPrice ? newExchange : oldExchange;
        }
    }
    // END SNIPPET: e3
}