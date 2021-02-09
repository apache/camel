package org.apache.camel.component.milo.client;

public interface MiloClientConnectionManager {
    MiloClientConnection createConnection(
            MiloClientConfiguration configuration, MonitorFilterConfiguration monitorFilterConfiguration);

    void releaseConnection(MiloClientConnection connection);
}
