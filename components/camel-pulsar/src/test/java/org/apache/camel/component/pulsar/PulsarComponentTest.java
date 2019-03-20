package org.apache.camel.component.pulsar;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class PulsarComponentTest extends CamelTestSupport {

    @Test
    public void testProducer() throws Exception {
        PulsarComponent component = new PulsarComponent(context);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar:persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        endpoint.isSingleton();
    }
}
