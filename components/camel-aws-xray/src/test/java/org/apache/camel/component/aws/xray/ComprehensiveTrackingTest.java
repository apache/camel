package org.apache.camel.component.aws.xray;

import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.xray.bean.SomeBean;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

public class ComprehensiveTrackingTest extends CamelAwsXRayTestSupport {

    private InvokeChecker invokeChecker = new InvokeChecker();

    public ComprehensiveTrackingTest() {
        super(
                TestDataBuilder.createTrace().inRandomOrder()
                        .withSegment(TestDataBuilder.createSegment("start")
                                .withSubsegment(TestDataBuilder.createSubsegment("direct:a")
                                        .withSubsegment(TestDataBuilder.createSubsegment("a")
                                                .withSubsegment(TestDataBuilder.createSubsegment("seda:b"))
                                                .withSubsegment(TestDataBuilder.createSubsegment("seda:c"))
                                                // note that the subsegment name matches the routeId
                                                .withSubsegment(TestDataBuilder.createSubsegment("test"))
                                                // no tracing of the invoke checker bean as it wasn't annotated with
                                                // @XRayTrace
                                        )
                                )
                        )
                        .withSegment(TestDataBuilder.createSegment("b"))
                        .withSegment(TestDataBuilder.createSegment("c")
                                // disabled by the LogSegmentDecorator (-> .to("log:...");
                                //.withSubsegment(TestDataBuilder.createSubsegment("log:test"))
                        )
                        .withSegment(TestDataBuilder.createSegment("d"))
                        // note no test-segment here!
        );
    }

    @Test
    public void testRoute() throws Exception {
        template.requestBody("direct:start", "Hello");

        verify();

        assertThat(invokeChecker.gotInvoked(), is(equalTo(true)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                        .wireTap("seda:d")
                        .to("direct:a");

                from("direct:a").routeId("a")
                        .log("routing at ${routeId}")
                        .to("seda:b")
                        .delay(2000)
                        .bean(SomeBean.class)
                        .to("seda:c")
                        .log("End of routing");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"));

                from("seda:c").routeId("c")
                        .to("log:test")
                        .delay(simple("${random(0,100)}"));

                from("seda:d").routeId("d")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(10,50)}"));

                from("seda:test").routeId("test")
                        .log("Async invoked route ${routeId} with body: ${body}")
                        .bean(invokeChecker)
                        .delay(simple("${random(10,50)}"));
            }
        };
    }

    public static class InvokeChecker {

        private boolean invoked = false;

        @Handler
        public void invoke() {
            this.invoked = true;
        }

        boolean gotInvoked() {
            return this.invoked;
        }
    }
}
