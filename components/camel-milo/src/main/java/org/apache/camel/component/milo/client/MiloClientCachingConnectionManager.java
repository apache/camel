package org.apache.camel.component.milo.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiloClientCachingConnectionManager implements MiloClientConnectionManager {
    private static final Logger LOG = LoggerFactory.getLogger(MiloClientCachingConnectionManager.class);

    private final Map<String, ManagedConnection> cache = new HashMap<>();

    private static class ManagedConnection {
        private final MiloClientConnection connection;
        private int amountConsumers = 0;

        ManagedConnection(MiloClientConnection connection) {
            this.connection = connection;
        }

        void increment() {
            amountConsumers++;
        }

        void decrement() {
            amountConsumers--;
        }
    }

    @Override
    public synchronized MiloClientConnection createConnection(
            MiloClientConfiguration configuration,
            MonitorFilterConfiguration monitorFilterConfiguration) {
        final String identifier = configuration.toCacheId();
        final ManagedConnection managedConnection
                = cache.computeIfAbsent(identifier, (k) -> managedConnection(configuration, monitorFilterConfiguration));
        managedConnection.increment();
        return managedConnection.connection;
    }

    @Override
    public synchronized void releaseConnection(MiloClientConnection connection) {
        final Optional<Entry<String, ManagedConnection>> existingConnection = this.cache.entrySet().stream()
                .filter(entry -> entry.getValue().connection.equals(connection)).findFirst();
        existingConnection.ifPresent((entry) -> {
            entry.getValue().decrement();
            if (entry.getValue().amountConsumers <= 0) {
                try {
                    LOG.debug("Closing connection {}", entry.getKey());
                    entry.getValue().connection.close();
                } catch (Exception e) {
                    LOG.warn("Error while closing connection with id {}", entry.getKey());
                } finally {
                    cache.remove(entry.getKey());
                }
            }
        });
    }

    private ManagedConnection managedConnection(
            MiloClientConfiguration configuration,
            MonitorFilterConfiguration monitorFilterConfiguration) {
        return new ManagedConnection(miloClientConnection(configuration, monitorFilterConfiguration));
    }

    private MiloClientConnection miloClientConnection(
            MiloClientConfiguration configuration,
            MonitorFilterConfiguration monitorFilterConfiguration) {
        return new MiloClientConnection(configuration, monitorFilterConfiguration);
    }
}
