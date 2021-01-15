package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class MultipleComponentInstancesTest extends ContextTestSupport {

    @Test
    public void testMultipleInstances() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock2:aaa").expectedMessageCount(1);
        getMockEndpoint("mock3:bbb").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:bye", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("log2", context.getComponent("log"));
        context.addComponent("mock2", context.getComponent("mock"));
        context.addComponent("mock3", context.getComponent("mock"));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("log:bar").to("mock:result");
                from("direct:bye").to("log2:bye").to("mock2:aaa").to("mock3:bbb");
            }
        };
    }
}
