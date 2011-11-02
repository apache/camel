package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

public class AggregateTimeoutWithNoExecutorServiceTest extends ContextTestSupport {
    public void testThreadUsedForEveryAggregatorWhenDefaultExecutorServiceUsed() throws Exception {
        assertTrue("There should be a thread for every aggregator when using defaults", 
                AggregateTimeoutWithExecutorServiceTest.aggregateThreadsCount() >= AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS);
        
        // sanity check to make sure were testing routes that work
        for (int i = 0; i < AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS; ++i) {
            MockEndpoint result = getMockEndpoint("mock:result" + i);
            // by default the use latest aggregation strategy is used so we get message 4
            result.expectedBodiesReceived("Message 4");
        }
        for (int i = 0; i < AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS; ++i) {
            for (int j = 0; j < 5; j++) {
                template.sendBodyAndHeader("direct:start" + i, "Message " + j, "id", "1");
            }
        }
        assertMockEndpointsSatisfied();
    }   
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                for (int i = 0; i < AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS; ++i) {
                    from("direct:start" + i)
                    // aggregate timeout after 3th seconds
                    .aggregate(header("id"), new UseLatestAggregationStrategy()).completionTimeout(3000)
                    .to("mock:result" + i);
                }
            }
        };
    }    
}
