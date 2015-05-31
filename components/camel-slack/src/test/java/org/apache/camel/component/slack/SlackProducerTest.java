package org.apache.camel.component.slack;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class SlackProducerTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint.xml";
    }

    @Test
    public void testSlackMessage() throws Exception {
        getMockEndpoint(("mock:errors")).expectedMessageCount(0);
        template.sendBody("direct:test", "Hello from Camel!");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSlackError() throws Exception {
        getMockEndpoint(("mock:errors")).expectedMessageCount(1);
        template.sendBody("direct:error", "Error from Camel!");
        assertMockEndpointsSatisfied();
    }
}
