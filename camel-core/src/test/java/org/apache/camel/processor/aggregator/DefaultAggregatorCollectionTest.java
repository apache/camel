package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for DefaultAggregatorCollection.
 */
public class DefaultAggregatorCollectionTest extends ContextTestSupport {

    public void testDefaultAggregateCollection() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 4 messages grouped by the latest message only
        result.expectedMessageCount(4);
        result.expectedBodiesReceived("Message 1d", "Message 2b", "Message 3c", "Message 4");

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
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id
                    // as we have not configured more on the aggregator it will default to aggregate the
                    // latest exchange only
                    .aggregator().header("id")
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}