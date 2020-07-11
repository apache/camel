package org.apache.camel.component.redis;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.component.redis.processor.aggregate.RedisAggregationRepository;
import org.junit.Test;

/**
 * The ABC example for using the Aggregator EIP.
 * <p/>
 * This example have 4 messages send to the aggregator, by which one
 * message is published which contains the aggregation of message 1,2 and 4
 * as they use the same correlation key.
 * <p/>
 * See the class {@link camelinaction.MyAggregationStrategy} for how the messages
 * are actually aggregated together.
 *
 * @see MyAggregationStrategy
 */
public class AggregateRedisTest extends CamelTestSupport {
	
	private String endpoint = System.getProperty("endpoint"); //ip:port
	
	@Test
    public void testABC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("ABC");
        template.sendBodyAndHeader("direct:start", "A", "myId", 1);
        template.sendBodyAndHeader("direct:start", "B", "myId", 1);
        template.sendBodyAndHeader("direct:start", "F", "myId", 2);
        template.sendBodyAndHeader("direct:start", "C", "myId", 1);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .log("Sending ${body} with correlation key ${header.myId}")
                    .aggregate(header("myId"), new MyAggregationStrategy())
					.aggregationRepository(new RedisAggregationRepository("aggregation", endpoint))
					.completionSize(3)
                        .log("Sending out ${body}")
                        .to("mock:result");
            }
        };
    }
}