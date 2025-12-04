/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.component.milo.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A caching {@link MiloClientConnectionManager} which cache and reuses the same {@link MiloClientConnection} for
 * clients with the same cache id ({@link MiloClientConfiguration#toCacheId()}.
 */
public class MiloClientCachingConnectionManager implements MiloClientConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientCachingConnectionManager.class);

    private final Map<String, ManagedConnection> cache = new HashMap<>();

    private static class ManagedConnection {
        private final MiloClientConnection connection;
        private int consumers;

        ManagedConnection(MiloClientConnection connection) {
            this.connection = connection;
        }

        void increment() {
            consumers++;
        }

        void decrement() {
            consumers--;
        }
    }

    @Override
    public synchronized MiloClientConnection createConnection(
            MiloClientConfiguration configuration, MonitorFilterConfiguration monitorFilterConfiguration) {
        final String identifier = configuration.toCacheId();
        final ManagedConnection managedConnection =
                cache.computeIfAbsent(identifier, k -> managedConnection(configuration, monitorFilterConfiguration));
        managedConnection.increment();
        return managedConnection.connection;
    }

    @Override
    public synchronized void releaseConnection(MiloClientConnection connection) {
        final Optional<Entry<String, ManagedConnection>> existingConnection = this.cache.entrySet().stream()
                .filter(entry -> entry.getValue().connection.equals(connection))
                .findFirst();
        existingConnection.ifPresent(entry -> {
            entry.getValue().decrement();
            if (entry.getValue().consumers <= 0) {
                try {
                    LOG.debug("Closing connection {}", entry.getKey());
                    entry.getValue().connection.close();
                } catch (Exception e) {
                    LOG.debug("Error while closing connection with id {}. This exception is ignored.", entry.getKey());
                } finally {
                    cache.remove(entry.getKey());
                }
            }
        });
    }

    private ManagedConnection managedConnection(
            MiloClientConfiguration configuration, MonitorFilterConfiguration monitorFilterConfiguration) {
        return new ManagedConnection(miloClientConnection(configuration, monitorFilterConfiguration));
    }

    private MiloClientConnection miloClientConnection(
            MiloClientConfiguration configuration, MonitorFilterConfiguration monitorFilterConfiguration) {
        return new MiloClientConnection(configuration, monitorFilterConfiguration);
    }
}
