package org.apache.camel.component.pulsar;

import static org.mockito.Mockito.mock;

import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.Test;

public class PulsarEndpointTest {

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarClientIsNull_throwIllegalArgumentExceptionOnCreation() {
        PulsarEndpointConfiguration configuration = mock(PulsarEndpointConfiguration.class);

        PulsarEndpoint.create(configuration, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarEndpointConfigurationIsNull_throwIllegalArgumentExceptionOnCreation() {
        PulsarClient pulsarClient = mock(PulsarClient.class);

        PulsarEndpoint.create(null, pulsarClient);
    }
}