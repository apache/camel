package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;

public class AggregateMaxTimeoutTest extends ContextTestSupport {
    public void testMaximumTimeout() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("A+B+C");

        template.sendBody("direct:max", "A");
        template.sendBody("direct:max", "B");
        template.sendBody("direct:max", "C");

        result.assertIsSatisfied(5000);
    }

    public void testMaximumTimeoutAlongWithTimeout() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("A+B+C");

        template.sendBody("direct:max+classic", "A");
        template.sendBody("direct:max+classic", "B");
        Thread.sleep(3000);
        template.sendBody("direct:max+classic", "C"); // This call extends the classic timeout

        result.assertIsSatisfied(3000); // We want the completion before the 4000 millis of the classic timeout
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:max")
                        .aggregate(constant(true), new BodyInAggregatingStrategy())
                        .completionMaxTimeout(2000)
                        .to("mock:result");

                from("direct:max+classic")
                        .aggregate(constant(true), new BodyInAggregatingStrategy())
                        .completionTimeout(4000)
                        .completionMaxTimeout(5000)
                        .to("mock:result");
            }
        };
    }
}
