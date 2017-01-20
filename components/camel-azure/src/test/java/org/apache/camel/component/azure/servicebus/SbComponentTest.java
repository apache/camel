package org.apache.camel.component.azure.servicebus;

import java.util.HashMap;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SbComponentTest  extends CamelTestSupport {
    @Test
    public void simpleSbComponentTest() {
        SbComponent component = new SbComponent(context);
        assertTrue(context.getRegistry().lookupByName("MyServiceBusContract") != null);

        component.createEndpoint("azure-sb://queue", "queue", new HashMap<String, Object>() {{ put("ServiceBusContract", "#MyServiceBusContract"); }});
    }

    @Test
    public void remainingParseTest() {
        String remaining;
        SbConfiguration configuration;

        remaining = "abc:def@x.y.z/topic";
        configuration = SbComponent.parseRemaining(remaining);

        assertEquals("abc", configuration.getSasKeyName());
        assertEquals("def", configuration.getSasKey());
        assertEquals("x", configuration.getNamespace());
        assertEquals(".y.z", configuration.getServiceBusRootUri());
        assertEquals(SbConstants.EntityType.TOPIC, configuration.getEntities());

        remaining = "topic";
        configuration = SbComponent.parseRemaining(remaining);
        assertEquals(SbConstants.EntityType.TOPIC, configuration.getEntities());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        ServiceBusContractMock myServiceBusContractMock = new ServiceBusContractMock();
        registry.bind("MyServiceBusContract", myServiceBusContractMock);
        return registry;
    }
}
