package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.ThrottlingExceptionRoutePolicy;
import org.junit.Before;
import org.junit.Test;

public class ThrottlingExceptionRoutePolicyOpenViaConfigTest extends ContextTestSupport {

    private String url = "seda:foo?concurrentConsumers=20";
    private MockEndpoint result;
    private int size = 5;

    private ThrottlingExceptionRoutePolicy policy;

    @Override
    @Before
    public void setUp() throws Exception {
        this.createPolicy();

        super.setUp();
        this.setUseRouteBuilder(true);
        result = getMockEndpoint("mock:result");
        context.getShutdownStrategy().setTimeout(1);
    }

    protected void createPolicy() {
        int threshold = 2;
        long failureWindow = 30;
        long halfOpenAfter = 1000;
        boolean keepOpen = false;
        policy = new ThrottlingExceptionRoutePolicy(threshold, failureWindow, halfOpenAfter, null, keepOpen);
    }

    @Test
    public void testThrottlingRoutePolicyStartWithAlwaysOpenOffThenToggle() throws Exception {

        // send first set of messages
        // should go through b/c circuit is closed
        for (int i = 0; i < size; i++) {
            template.sendBody(url, "MessageRound1 " + i);
            Thread.sleep(3);
        }
        result.expectedMessageCount(size);
        result.setResultWaitTime(2000);
        assertMockEndpointsSatisfied();

        // set keepOpen to true
        policy.setKeepOpen(true);

        // trigger opening circuit
        // by sending another message
        template.sendBody(url, "MessageTrigger");

        // give time for circuit to open
        Thread.sleep(1000);

        // send next set of messages
        // should NOT go through b/c circuit is open
        for (int i = 0; i < size; i++) {
            template.sendBody(url, "MessageRound2 " + i);
            Thread.sleep(3);
        }

        // gives time for policy half open check to run every second
        // and should not close b/c keepOpen is true
        Thread.sleep(2000);

        result.expectedMessageCount(size + 1);
        result.setResultWaitTime(2000);
        assertMockEndpointsSatisfied();

        // set keepOpen to false
        policy.setKeepOpen(false);

        // gives time for policy half open check to run every second
        // and it should close b/c keepOpen is false
        result.expectedMessageCount(size * 2 + 1);
        result.setResultWaitTime(2000);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                int threshold = 2;
                long failureWindow = 30;
                long halfOpenAfter = 1000;
                policy = new ThrottlingExceptionRoutePolicy(threshold, failureWindow, halfOpenAfter, null);

                from(url)
                    .routePolicy(policy)
                    .log("${body}")
                    .to("log:foo?groupSize=10")
                    .to("mock:result");
            }
        };
    }

}