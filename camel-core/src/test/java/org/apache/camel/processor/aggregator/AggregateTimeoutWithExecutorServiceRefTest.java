package org.apache.camel.processor.aggregator;

import java.util.concurrent.ScheduledExecutorService;

import javax.naming.Context;

import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.ThreadPoolProfile;

public class AggregateTimeoutWithExecutorServiceRefTest extends AggregateTimeoutWithExecutorServiceTest {
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // create and register thread pool profile
                ThreadPoolProfile profile = new ThreadPoolProfile("MyThreadPool");
                profile.setPoolSize(8);
                profile.setMaxPoolSize(8);
                profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
                context.getExecutorServiceManager().registerThreadPoolProfile(profile);
                
                for (int i = 0; i < NUM_AGGREGATORS; ++i) {
                    from("direct:start" + i)
                    // aggregate timeout after 3th seconds
                    .aggregate(header("id"), new UseLatestAggregationStrategy()).completionTimeout(3000).timeoutCheckerExecutorServiceRef("MyThreadPool")
                    .to("mock:result" + i);
                }
            }
        };
    }
    
}
