package org.apache.camel.component.azure.servicebus;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SbComponentTest {
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
}
