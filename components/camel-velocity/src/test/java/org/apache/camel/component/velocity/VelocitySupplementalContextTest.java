package org.apache.camel.component.velocity;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class VelocitySupplementalContextTest extends CamelTestSupport {

    @Produce(uri = "direct:input")
    protected ProducerTemplate inputEndpoint;

    @EndpointInject(uri = "mock:results")
    protected MockEndpoint outputEndpoint;

    @Test
    public void testCamelRoute() throws Exception {
        outputEndpoint.expectedMessageCount(1);
        outputEndpoint.expectedHeaderReceived("body", "new_body");
        outputEndpoint.expectedHeaderReceived("in.body", "old_body");
        outputEndpoint.expectedBodiesReceived("bar");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(VelocityConstants.VELOCITY_TEMPLATE,
                "#set( $headers.body = ${body} )\n" +
                "#set( $headers['in.body'] = $in.body )\n" + "bar");
        inputEndpoint.sendBodyAndHeaders("old_body", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        final Map<String, Object> supplementalContext = new HashMap<>();
        supplementalContext.put("body", "new_body");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:input")
                .setHeader(VelocityConstants.VELOCITY_SUPPLEMENTAL_CONTEXT).constant(supplementalContext)
                .to("velocity:template-in-header")
                .to("mock:results");
            }
        };
    }

}
