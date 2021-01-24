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

    @Metadata
    private MiloClientConfiguration configuration = new MiloClientConfiguration();

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
            throws Exception {

        final MiloClientConfiguration configuration = new MiloClientConfiguration(this.configuration);
        configuration.setEndpointUri(remaining);

        final MiloClientEndpoint endpoint = new MiloClientEndpoint(uri, this, configuration.getEndpointUri());
        endpoint.setConfiguration(configuration);
        setProperties(endpoint, parameters);

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

    public synchronized MiloClientConnection createConnection(
            MiloClientConfiguration configurationParam, MonitorFilterConfiguration monitorFilterConfiguration) {
        final String cacheId = configurationParam.toCacheId();
        MiloClientConnection connection = this.cache.get(cacheId);
        if (connection == null) {
            LOG.debug("Cache miss - creating new connection instance: {}", cacheId);
            connection = new MiloClientConnection(configurationParam, monitorFilterConfiguration);
            this.cache.put(cacheId, connection);
        }
        return connection;
    }

    @Override
    protected synchronized void doStop() throws Exception {
        super.doStop();

        for (MiloClientConnection connection : this.cache.values()) {
            connection.close();
        }
        this.cache.clear();
    }

}
