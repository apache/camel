package org.apache.camel;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;

import org.junit.Ignore;
import org.junit.Test;

public class BlueprintBeanTest extends CamelBlueprintTestSupport {
	
    @Override
    protected String getBlueprintDescriptor() {
        return "/OSGI-INF/blueprint/blueprint-camel-context.xml";
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Test
    public void testRoute() throws Exception {
        // the route is timer based, so every 2 seconds a message is sent
        MockEndpoint result = getMockEndpoint("mock://result");
        result.expectedMinimumMessageCount(1);
        result.expectedBodyReceived().body().contains("Default property value");

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

}
