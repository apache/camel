package org.apache.camel.component.google.functions.unit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class GoogleCloudFunctionsComponentTest extends CamelTestSupport {

    @Test
    public void testGoogleCloudFunctions() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        //mock.expectedMinimumMessageCount(1);

        //assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:test")
                        .to("google-functions://function1")
                        .to("mock:result");
            }
        };
    }
}
