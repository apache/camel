package org.apache.camel;

import java.util.Dictionary;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;

import org.junit.Ignore;
import org.junit.Test;

public class BlueprintBeanPropertiesOverrideFromTestTest extends CamelBlueprintTestSupport {
	
    @Override
    protected String getBlueprintDescriptor() {
        return "/OSGI-INF/blueprint/blueprint-camel-context.xml";
    }

    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {
        // override / add extra properties
        props.put("greeting", "Hi from Camel - test property value");

        // return the persistence-id to use
        return "HelloBean";
    }

    @Test
    public void testReplacePropertiesFromTest() throws Exception {
        // the route is timer based, so every 2 seconds a message is sent
        MockEndpoint result = getMockEndpoint("mock://result");
        result.expectedMinimumMessageCount(1);
        result.expectedBodyReceived().body().contains("test property value");

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

}
