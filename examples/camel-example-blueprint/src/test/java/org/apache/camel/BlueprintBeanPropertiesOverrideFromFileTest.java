package org.apache.camel;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;

import org.junit.Ignore;
import org.junit.Test;

public class BlueprintBeanPropertiesOverrideFromFileTest extends CamelBlueprintTestSupport {
	
    @Override
    protected String getBlueprintDescriptor() {
        return "/OSGI-INF/blueprint/blueprint-camel-context.xml";
    }

    @Override
    protected String[] loadConfigAdminConfigurationFile() {
        // which .cfg file to use, and the name of the persistence-id
        return new String[]{"src/test/resources/etc/HelloBean.cfg", "HelloBean"};
    }

    @Test
    public void testReplacePropertiesFromFile() throws Exception {
        // the route is timer based, so every 2 seconds a message is sent
        MockEndpoint result = getMockEndpoint("mock://result");
        result.expectedMinimumMessageCount(1);
        result.expectedBodyReceived().body().contains("file property value");

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

}
