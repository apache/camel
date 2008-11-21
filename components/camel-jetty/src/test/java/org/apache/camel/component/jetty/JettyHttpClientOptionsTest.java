package org.apache.camel.component.jetty;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for http client options.
 */
public class JettyHttpClientOptionsTest extends ContextTestSupport {

    public void testCustomHttpBinding() throws Exception {
        // assert jetty was configured with our timeout
        JettyHttpComponent jetty = context.getComponent("jetty", JettyHttpComponent.class);
        assertNotNull(jetty);
        assertEquals(5555, jetty.getHttpClient().getIdleTimeout());

        // send and receive
        Object out = template.requestBody("http://localhost:8080/myapp/myservice", "Hello World");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, out));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:8080/myapp/myservice?httpClient.idleTimeout=5555").transform().constant("Bye World");
            }
        };
    }

}