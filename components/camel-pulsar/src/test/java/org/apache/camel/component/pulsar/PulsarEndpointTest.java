package org.apache.camel.component.pulsar;

import static org.mockito.Mockito.mock;

import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.Test;

public class PulsarEndpointTest {

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarClientIsNull_throwIllegalArgumentExceptionOnCreation() throws PulsarClientException {
        PulsarEndpointConfiguration configuration = mock(PulsarEndpointConfiguration.class);

        PulsarEndpoint.create(configuration, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarEndpointConfigurationIsNull_throwIllegalArgumentExceptionOnCreation() throws PulsarClientException {
        PulsarClient pulsarClient = mock(PulsarClient.class);

        PulsarEndpoint.create(null, null);
    }
}