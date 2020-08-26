package org.apache.camel.component.etcd3;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd3.processor.aggregate.Etcd3AggregationRepository;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * The ABC example for using the Aggregator EIP.
 * <p/>
 * This example have 4 messages send to the aggregator, by which one message is published which contains the aggregation
 * of message 1,2 and 4 as they use the same correlation key.
 * <p/>
 */
@Disabled("Requires manually testing")
public class AggregateEtcd3Test extends CamelTestSupport {

    // TODO: use docker test-containers for testing

    private String endpoint = System.getProperty("endpoint"); //http://ip:port

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
                        .aggregationRepository(new Etcd3AggregationRepository("aggregation", endpoint))
                        .completionSize(3)
                        .log("Sending out ${body}")
                        .to("mock:result");
            }
        };
    }
}
