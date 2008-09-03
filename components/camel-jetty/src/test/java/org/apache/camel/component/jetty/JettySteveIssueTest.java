package org.apache.camel.component.jetty;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test based on Steve request for CAMEL-877.
 */
public class JettySteveIssueTest extends ContextTestSupport {

    private String serverUri = "http://localhost:5432/myservice";

    public void testSendX() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("<html><body>foo</body></html>");
        mock.expectedHeaderReceived("x", "foo");

        template.sendBody(serverUri + "?x=foo", "Hello World");

        assertMockEndpointsSatisifed();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:" + serverUri)
                    .setBody().simple("<html><body>${in.header.x}</body></html>")
                    .to("mock:result");
            }
        };
    }

}
