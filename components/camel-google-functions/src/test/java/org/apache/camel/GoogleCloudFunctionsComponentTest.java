package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class GoogleCloudFunctionsComponentTest extends CamelTestSupport {

    @Test
    public void testGoogleCloudFunctions() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("google-functions://foo")
                        .to("google-functions://bar")
                        .to("mock:result");
            }
        };
    }
}
