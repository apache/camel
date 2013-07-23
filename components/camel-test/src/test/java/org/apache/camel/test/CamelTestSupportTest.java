package org.apache.camel.test;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class CamelTestSupportTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        replaceRouteFromWith("routeId", "direct:start");
        super.setUp();
    }

    @Test
    public void replacesFromEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:something")
                        .id("routeId")
                        .to("mock:result");
            }
        };
    }
}
