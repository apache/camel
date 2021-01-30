package org.apache.camel.component.milo;

import org.apache.camel.component.milo.client.MiloClientCachingConnectionManager;
import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MonitorFilterConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MiloClientCachingConnectionManagerTest {

    private MiloClientCachingConnectionManager instance;

    @BeforeEach
    void setup() {
        instance = new MiloClientCachingConnectionManager();
    }

    @Test
    void testCreateConnection_reuseConnection() {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();

        MiloClientConnection connection1 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        MiloClientConnection connection2 = instance.createConnection(configuration, new MonitorFilterConfiguration());

        Assertions.assertNotNull(connection1);
        Assertions.assertNotNull(connection2);
        Assertions.assertEquals(connection1, connection2);
    }

    @Test
    void releaseConnection_notLastConsumer() throws Exception {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        MiloClientConnection connection1 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        instance.createConnection(configuration, new MonitorFilterConfiguration());

        instance.releaseConnection(connection1);

        MiloClientConnection connection3 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        Assertions.assertEquals(connection1, connection3);
    }

    @Test
    void releaseConnection_lastConsumer() throws Exception {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        MiloClientConnection connection1 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        MiloClientConnection connection2 = instance.createConnection(configuration, new MonitorFilterConfiguration());

        instance.releaseConnection(connection1);
        instance.releaseConnection(connection2);

        MiloClientConnection connection3 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        Assertions.assertFalse(connection1 == connection3);
    }
}
