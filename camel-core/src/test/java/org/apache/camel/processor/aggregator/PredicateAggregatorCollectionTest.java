package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.PredicateAggregationCollection;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationCollection;

/**
 * Unit test for PredicateAggregatorCollection.
 */
public class PredicateAggregatorCollectionTest extends ContextTestSupport {

    public void testPredicateAggregateCollection() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we only expect two messages as they have reached the completed predicate
        // that we want 3 messages that has the same header id
        result.expectedMessageCount(2);
        result.expectedBodiesReceived("Message 1c", "Message 3c");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3b", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3c", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1d", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e2
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // create the aggregation collection we will use.
                // - we will correlate the recieved message based on the id header
                // - as we will just keep the latest message we use the latest strategy
                // - and finally we stop aggregate if we recieve 2 or more messages
                AggregationCollection ag = new PredicateAggregationCollection(header("id"),
                    new UseLatestAggregationStrategy(),
                    header(Exchange.AGGREGATED_COUNT).isEqualTo(3));

                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // we use the collection based aggregator we already have configued
                    .aggregator(ag)
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
