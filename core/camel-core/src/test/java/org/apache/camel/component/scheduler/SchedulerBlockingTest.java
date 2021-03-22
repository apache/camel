package org.apache.camel.component.scheduler;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual test")
public class SchedulerBlockingTest extends ContextTestSupport {

    @Test
    public void testScheduler() throws Exception {
        Thread.sleep(60000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SchedulerComponent comp = getContext().getComponent("scheduler", SchedulerComponent.class);
                comp.setPoolSize(4);

                from("scheduler://trigger?delay=2000&repeatCount=3").routeId("scheduler")
                        .threads(10)
                        .log("1")
                        .inOut("seda:route1")
                        .log("1.1");

                from("seda:route1?concurrentConsumers=2").routeId("first route")
                        .log("2")
                        .delay(5000)
                        .log("2.1")
                        .inOut("seda:route2")
                        .log("2.2");

                from("seda:route2").routeId("second route")
                        .log("3")
                        .delay(3000)
                        .log("3.1");
            }
        };
    }
}
