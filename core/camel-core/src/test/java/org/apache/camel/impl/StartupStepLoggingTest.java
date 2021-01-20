package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StartupStepLoggingTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(false);
        context.adapt(ExtendedCamelContext.class).getStartupStepRecorder().setEnabled(true);
        // you can restrict the sub steps to a max depth level
        // context.adapt(ExtendedCamelContext.class).getStartupStepRecorder().setMaxDepth(1);
        context.setLoadTypeConverters(true);
        return context;
    }

    @Test
    public void testLog() throws Exception {
        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("log:bar").to("mock:result");
            }
        };
    }

}
