package org.apache.camel.component.pulsar;

import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.Test;

public class PulsarEndpointTest {

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarEndpointConfigurationIsNull_throwIllegalArgumentExceptionOnCreation() throws PulsarClientException {
        PulsarEndpoint.create("", "", null, null);
    }
}