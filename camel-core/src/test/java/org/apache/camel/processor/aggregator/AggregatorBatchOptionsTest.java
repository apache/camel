package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for the batch size options on aggregator.
 */
public class AggregatorBatchOptionsTest extends ContextTestSupport {

    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testAggregateOutBatchSize() throws Exception {
        context.addRoutes(new RouteBuilder() {
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
                    // batch size in is the limit of number of exchanges recieved, so when we have received 100
                    // exchanges then whatever we have in the collection will be sent
                    .batchSize(100)
                    // limit the out batch size to 3 so when we have aggregated 3 exchanges
                    // and we reach this limit then the exchanges is send
                    .outBatchSize(3)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        });
        startCamelContext();

        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 3 messages grouped by the latest message only
        result.expectedMinimumMessageCount(3);
        result.expectedBodiesReceived("Message 1c", "Message 2b", "Message 3a");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        // when we send message 4 then we will reach the collection batch size limit and the
        // exchanges above is the ones we have aggregated in the first batch
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");
        template.sendBodyAndHeader("direct:start", "Message 3b", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 3c", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1d", "id", "1");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e2
    }

    public void testAggregateBatchSize() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e3
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id
                    // as we have not configured more on the aggregator it will default to aggregate the
                    // latest exchange only
                    .aggregator().header("id")
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    // batch size in is the limit of number of exchanges recieved, so when we have received 100
                    // exchanges then whatever we have in the collection will be sent
                    .batchSize(5)
                    .to("mock:result");
                // END SNIPPET: e3
            }
        });
        startCamelContext();

        // START SNIPPET: e4
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 3 messages grouped by the latest message only
        result.expectedMinimumMessageCount(2);
        result.expectedBodiesReceived("Message 1c", "Message 2b");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");
        // when we sent the next message we have reached the in batch size limit and the current
        // aggregated exchanges will be sent
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");
        template.sendBodyAndHeader("direct:start", "Message 3b", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 3c", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1d", "id", "1");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e4
    }

    public void testAggregateBatchTimeout() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e5
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id
                    // as we have not configured more on the aggregator it will default to aggregate the
                    // latest exchange only
                    .aggregator().header("id")
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e5
            }
        });
        startCamelContext();

        // START SNIPPET: e6
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 3 messages grouped by the latest message only
        result.expectedMinimumMessageCount(3);
        result.expectedBodiesReceived("Message 1c", "Message 2b", "Message 3a");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        Thread.sleep(600L);
        // these messages are not aggregated as the timeout should have accoured
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");
        template.sendBodyAndHeader("direct:start", "Message 3b", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 3c", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1d", "id", "1");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e6
    }

}