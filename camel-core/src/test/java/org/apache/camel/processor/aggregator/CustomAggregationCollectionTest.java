package org.apache.camel.processor.aggregator;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.AbstractCollection;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationCollection;

/**
 * Unit test for using our own aggregation collection.
 */
public class CustomAggregationCollectionTest extends ContextTestSupport {

    public void testCustomAggregationCollection() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 5 messages since our custom aggregation collection just gets it all
        // but returns them in reverse order
        result.expectedMessageCount(5);
        result.expectedBodiesReceived("190", "200", "130", "150", "100");

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
                    // use our own collection for aggregation
                    .aggregator(new MyReverseAggregationCollection())
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e3
    private static class MyReverseAggregationCollection extends AbstractCollection<Exchange> implements AggregationCollection {

        private List<Exchange> collection = new ArrayList<Exchange>();
        private Expression<Exchange> correlation;
        private AggregationStrategy strategy;

        public Expression<Exchange> getCorrelationExpression() {
            return correlation;
        }

        public void setCorrelationExpression(Expression<Exchange> correlationExpression) {
            this.correlation = correlationExpression;
        }

        public AggregationStrategy getAggregationStrategy() {
            return strategy;
        }

        public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
            this.strategy = aggregationStrategy;
        }

        public boolean add(Exchange exchange) {
            return collection.add(exchange);
        }

        public Iterator<Exchange> iterator() {
            // demonstrate the we can do something with this collection, so we reverse it
            Collections.reverse(collection);

            return collection.iterator();
        }

        public int size() {
            return collection.size();
        }

        public void clear() {
            collection.clear();
        }

        public void onAggregation(Object correlationKey, Exchange newExchange) {
            add(newExchange);
        }
    }
    // END SNIPPET: e3
}