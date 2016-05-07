package org.apache.camel;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;

import org.junit.Ignore;
import org.junit.Test;

@Ignore( value = "Ignore for now - working on Pax Exam test")
public class BlueprintBeanPropertiesOverrideFromFileTest extends CamelBlueprintTestSupport {
	
    @Override
    protected String getBlueprintDescriptor() {
        return "/OSGI-INF/blueprint/blueprint-bean.xml";
    }

    @Override
    protected String[] loadConfigAdminConfigurationFile() {
        // which .cfg file to use, and the name of the persistence-id
        return new String[]{"src/test/resources/etc/HelloBean.cfg", "HelloBean"};
    }

    @Test
    public void testRoute() throws Exception {
        // the route is timer based, so every 5th second a message is send
        // we should then expect at least one message
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);

        // assert expectations
        assertMockEndpointsSatisfied();
    }

}
