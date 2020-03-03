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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("milo-client")
public class MiloClientComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientComponent.class);

    private final Map<String, MiloClientConnection> cache = new HashMap<>();
    private final Multimap<String, MiloClientEndpoint> connectionMap = HashMultimap.create();

    @Metadata
    private MiloClientConfiguration configuration = new MiloClientConfiguration();

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        final MiloClientConfiguration configuration = new MiloClientConfiguration(this.configuration);
        configuration.setEndpointUri(remaining);

        Endpoint endpoint = doCreateEndpoint(uri, configuration, parameters);
        return endpoint;
    }

    private synchronized MiloClientEndpoint doCreateEndpoint(final String uri, final MiloClientConfiguration configuration, final Map<String, Object> parameters) throws Exception {
        final MiloClientEndpoint endpoint = new MiloClientEndpoint(uri, this, configuration.getEndpointUri());
        endpoint.setConfiguration(configuration);
        setProperties(endpoint, parameters);

        final String cacheId = configuration.toCacheId();
        MiloClientConnection connection = this.cache.get(cacheId);
        if (connection == null) {
            LOG.info("Cache miss - creating new connection instance: {}", cacheId);
            connection = new MiloClientConnection(configuration);
            this.cache.put(cacheId, connection);
        }

        // register connection with endpoint
        this.connectionMap.put(cacheId, endpoint);
        endpoint.setConnection(connection);
        return endpoint;
    }

    public MiloClientConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * All default options for client configurations
     */
    public void setConfiguration(final MiloClientConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Default application name
     */
    public void setApplicationName(final String applicationName) {
        this.configuration.setApplicationName(applicationName);
    }

    /**
     * Default application URI
     */
    public void setApplicationUri(final String applicationUri) {
        this.configuration.setApplicationUri(applicationUri);
    }

    /**
     * Default product URI
     */
    public void setProductUri(final String productUri) {
        this.configuration.setProductUri(productUri);
    }

    /**
     * Default reconnect timeout
     */
    public void setReconnectTimeout(final Long reconnectTimeout) {
        this.configuration.setRequestTimeout(reconnectTimeout);
    }

    public synchronized void disposed(final MiloClientEndpoint endpoint) {

        final MiloClientConnection connection = endpoint.getConnection();

        // unregister usage of connection

        this.connectionMap.remove(connection.getConnectionId(), endpoint);

        // test if this was the last endpoint using this connection

        if (!this.connectionMap.containsKey(connection.getConnectionId())) {

            // this was the last endpoint using the connection ...

            // ... remove from the cache

            this.cache.remove(connection.getConnectionId());

            // ... and close

            try {
                connection.close();
            } catch (final Exception e) {
                LOG.warn("Failed to close connection", e);
            }
        }
    }
}
