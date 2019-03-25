package org.apache.camel.component.pulsar;

import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PulsarComponentTest extends CamelTestSupport {

    @Mock
    private AutoConfiguration autoConfiguration;

    @Test
    public void testProducer() throws Exception {
        PulsarEndpointConfiguration configuration = new PulsarEndpointConfiguration();
        configuration.setPulsarBrokerUrl("pulsar://localhost:6650");
        PulsarComponent component = new PulsarComponent(context, configuration, null);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar://persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        endpoint.isSingleton();
    }

    @Test
    public void testProducerAutoconfigures() throws Exception {
        PulsarEndpointConfiguration configuration = new PulsarEndpointConfiguration();
        configuration.setPulsarBrokerUrl("pulsar://localhost:6650");
        PulsarComponent component = new PulsarComponent(context, configuration, autoConfiguration);

        PulsarEndpoint endpoint = (PulsarEndpoint) component.createEndpoint("pulsar://persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10&subscriptionName=batch-created-subscription&subscriptionType=Shared");

        endpoint.isSingleton();

        verify(autoConfiguration).ensureNameSpaceAndTenant(Matchers.anyString());
    }
}
