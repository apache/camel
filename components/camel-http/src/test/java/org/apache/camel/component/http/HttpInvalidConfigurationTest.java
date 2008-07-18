package org.apache.camel.component.http;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import static org.apache.camel.component.http.HttpMethods.HTTP_METHOD;
import static org.apache.camel.component.http.HttpMethods.POST;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test of invalid configuraiton
 */
public class HttpInvalidConfigurationTest extends ContextTestSupport {

    protected void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should have thrown ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getMessage().endsWith("You have duplicated the http(s) protocol."));
        }
    }

    public void testInvalidHostConfiguratiob() {
        // dummy
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setHeader(HTTP_METHOD, POST).to("http://http://www.google.com");
            }
        };
    }

}
