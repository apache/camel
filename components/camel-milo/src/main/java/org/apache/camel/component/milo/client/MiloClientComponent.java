/**
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.base.Supplier;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.apache.camel.Endpoint;
import org.apache.camel.component.milo.KeyStoreLoader;
import org.apache.camel.component.milo.KeyStoreLoader.Result;
import org.apache.camel.impl.DefaultComponent;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiloClientComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientComponent.class);

    private final Map<String, MiloClientConnection> cache = new HashMap<>();
    private final Multimap<String, MiloClientEndpoint> connectionMap = HashMultimap.create();

    private MiloClientConfiguration defaultConfiguration = new MiloClientConfiguration();

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {

        final MiloClientConfiguration configuration = new MiloClientConfiguration(this.defaultConfiguration);
        configuration.setEndpointUri(remaining);
        setProperties(configuration, parameters);

        return createEndpoint(uri, configuration, parameters);
    }

    private synchronized MiloClientEndpoint createEndpoint(final String uri, final MiloClientConfiguration configuration, final Map<String, Object> parameters) throws Exception {

        MiloClientConnection connection = this.cache.get(configuration.toCacheId());

        if (connection == null) {
            LOG.info("Cache miss - creating new connection instance: {}", configuration.toCacheId());

            connection = new MiloClientConnection(configuration, mapToClientConfiguration(configuration));
            this.cache.put(configuration.toCacheId(), connection);
        }

        final MiloClientEndpoint endpoint = new MiloClientEndpoint(uri, this, connection, configuration.getEndpointUri());

        setProperties(endpoint, parameters);

        // register connection with endpoint

        this.connectionMap.put(configuration.toCacheId(), endpoint);

        return endpoint;
    }

    private OpcUaClientConfigBuilder mapToClientConfiguration(final MiloClientConfiguration configuration) {
        final OpcUaClientConfigBuilder builder = new OpcUaClientConfigBuilder();

        whenHasText(configuration::getApplicationName, value -> builder.setApplicationName(LocalizedText.english(value)));
        whenHasText(configuration::getApplicationUri, builder::setApplicationUri);
        whenHasText(configuration::getProductUri, builder::setProductUri);

        if (configuration.getRequestTimeout() != null) {
            builder.setRequestTimeout(Unsigned.uint(configuration.getRequestTimeout()));
        }
        if (configuration.getChannelLifetime() != null) {
            builder.setChannelLifetime(Unsigned.uint(configuration.getChannelLifetime()));
        }

        whenHasText(configuration::getSessionName, value -> builder.setSessionName(() -> value));
        if (configuration.getSessionTimeout() != null) {
            builder.setSessionTimeout(UInteger.valueOf(configuration.getSessionTimeout()));
        }

        if (configuration.getMaxPendingPublishRequests() != null) {
            builder.setMaxPendingPublishRequests(UInteger.valueOf(configuration.getMaxPendingPublishRequests()));
        }

        if (configuration.getMaxResponseMessageSize() != null) {
            builder.setMaxResponseMessageSize(UInteger.valueOf(configuration.getMaxPendingPublishRequests()));
        }

        if (configuration.getSecureChannelReauthenticationEnabled() != null) {
            builder.setSecureChannelReauthenticationEnabled(configuration.getSecureChannelReauthenticationEnabled());
        }

        if (configuration.getKeyStoreUrl() != null) {
            setKey(configuration, builder);
        }

        return builder;
    }

    private void setKey(final MiloClientConfiguration configuration, final OpcUaClientConfigBuilder builder) {
        final KeyStoreLoader loader = new KeyStoreLoader();

        final Result result;
        try {
            // key store properties
            loader.setType(configuration.getKeyStoreType());
            loader.setUrl(configuration.getKeyStoreUrl());
            loader.setKeyStorePassword(configuration.getKeyStorePassword());

            // key properties
            loader.setKeyAlias(configuration.getKeyAlias());
            loader.setKeyPassword(configuration.getKeyPassword());

            result = loader.load();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to load key", e);
        }

        if (result == null) {
            throw new IllegalStateException("Key not found in keystore");
        }

        builder.setCertificate(result.getCertificate());
        builder.setKeyPair(result.getKeyPair());
    }

    private void whenHasText(final Supplier<String> valueSupplier, final Consumer<String> valueConsumer) {
        final String value = valueSupplier.get();
        if (value != null && !value.isEmpty()) {
            valueConsumer.accept(value);
        }
    }

    /**
     * All default options for client
     */
    public void setDefaultConfiguration(final MiloClientConfiguration defaultConfiguration) {
        this.defaultConfiguration = defaultConfiguration;
    }

    /**
     * Default application name
     */
    public void setApplicationName(final String applicationName) {
        this.defaultConfiguration.setApplicationName(applicationName);
    }

    /**
     * Default application URI
     */
    public void setApplicationUri(final String applicationUri) {
        this.defaultConfiguration.setApplicationUri(applicationUri);
    }

    /**
     * Default product URI
     */
    public void setProductUri(final String productUri) {
        this.defaultConfiguration.setProductUri(productUri);
    }

    /**
     * Default reconnect timeout
     */
    public void setReconnectTimeout(final Long reconnectTimeout) {
        this.defaultConfiguration.setRequestTimeout(reconnectTimeout);
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
