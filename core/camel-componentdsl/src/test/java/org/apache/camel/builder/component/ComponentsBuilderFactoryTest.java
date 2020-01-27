package org.apache.camel.builder.component;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.timer.TimerComponent;
import org.junit.Test;

public class ComponentsBuilderFactoryTest extends ContextTestSupport {

    @Test
    public void testIfCreateComponentCorrectly() {
        final TimerComponent timerComponent = (TimerComponent) ComponentsBuilderFactory.timer().build();
        assertNotNull(timerComponent);
    }

    @Test
    public void testNegativeDelay() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final TimerComponent timerComponent = (TimerComponent) ComponentsBuilderFactory.timer().build();
                context.addComponent("awesomeTimer", timerComponent);

                from("awesomeTimer:foo?delay=-1&repeatCount=10")
                        .to("mock:result");
            }
        };
    }
}