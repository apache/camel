package org.apache.camel.component.pulsar;

import org.apache.camel.component.pulsar.configuration.AdminConfiguration;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.junit.Test;
import org.mockito.Mock;

public class AutoConfigurationTest {

    @Mock
    private AdminConfiguration adminConfiguration;
    @Mock
    private PulsarAdmin pulsarAdmin;

    @Test
    public void noAdminClient() {
        //TODO
    }
}
