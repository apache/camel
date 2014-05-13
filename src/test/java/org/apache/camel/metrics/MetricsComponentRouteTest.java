package org.apache.camel.metrics;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class MetricsComponentRouteTest extends CamelTestSupport {

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void testMetrics() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody(new Object());
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        // .to("metrics")
                        // .to("metrics:")
                        .to("metrics:A")
                        .to("metrics:counter://B")
                        .to("metrics:counter:C?increment=19291")
                        .to("metrics:counter:C?decrement=19292")
                        .to("metrics:counter:C")
                        .to("metrics:meter:D")
                        .to("metrics:meter:D?mark=90001")
                        .to("mock:result");
            }
        };
    }
}
