package org.apache.camel.processor.aggregator;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;

public class AggregateExpressionMaxTimeoutTest extends ContextTestSupport {
    public void testMaximumTimeoutExpression() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("A+B+C");

        template.sendBodyAndHeader("direct:expression", "A", "max", 2000);
        template.sendBodyAndHeader("direct:expression", "B", "max", 2000);
        template.sendBodyAndHeader("direct:expression", "C", "max", 2000);

        result.assertIsSatisfied(5000);
    }

    public void testMaximumTimeoutExpressionWithDefaultValue() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("D+E+F", "A+B+C");

        template.sendBodyAndHeader("direct:expression+default", "A", "group", 1);
        template.sendBodyAndHeader("direct:expression+default", "B", "group", 1);
        template.sendBodyAndHeader("direct:expression+default", "C", "group", 1);

        // This group should arrive first since it has a smaller max timeout
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("group", 2);
        headers.put("max", 2000);
        template.sendBodyAndHeaders("direct:expression+default", "D", headers);
        template.sendBodyAndHeaders("direct:expression+default", "E", headers);
        template.sendBodyAndHeaders("direct:expression+default", "F", headers);

        result.assertIsSatisfied(8000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:expression")
                        .aggregate(constant(true), new BodyInAggregatingStrategy())
                        .completionMaxTimeout(header("max"))
                        .to("mock:result");

                from("direct:expression+default")
                        .aggregate(header("group"), new BodyInAggregatingStrategy())
                        .completionMaxTimeout(header("max"))
                        .completionMaxTimeout(5000)
                        .to("mock:result");
            }
        };
    }
}
