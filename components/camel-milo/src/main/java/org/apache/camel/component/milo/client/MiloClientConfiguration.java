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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.xml.bind.annotation.XmlTransient;

import com.google.common.base.Supplier;
import org.apache.camel.component.milo.KeyStoreLoader;
import org.apache.camel.component.milo.KeyStoreLoader.Result;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

@UriParams
public class MiloClientConfiguration implements Cloneable {

    private static final String DEFAULT_APPLICATION_URI = "http://camel.apache.org/EclipseMilo/Client";

    private static final String DEFAULT_APPLICATION_NAME = "Apache Camel adapter for Eclipse Milo";

    private static final String DEFAULT_PRODUCT_URI = "http://camel.apache.org/EclipseMilo";

    private static final Double DEFAULT_REQUESTED_PUBLISHING_INTERVAL = 1_000.0;

    @XmlTransient // to not be included in component docs
    private String endpointUri;

    @UriParam
    private String discoveryEndpointUri;

    @UriParam
    private String discoveryEndpointSuffix;

    @UriParam
    private String clientId;

    @UriParam(label = "client", defaultValue = DEFAULT_APPLICATION_NAME)
    private String applicationName = DEFAULT_APPLICATION_NAME;

    @UriParam(label = "client", defaultValue = DEFAULT_APPLICATION_URI)
    private String applicationUri = DEFAULT_APPLICATION_URI;

    @UriParam(label = "client", defaultValue = DEFAULT_PRODUCT_URI)
    private String productUri = DEFAULT_PRODUCT_URI;

    @UriParam(label = "client")
    private Long requestTimeout;

    @UriParam(label = "client")
    private Long channelLifetime;

    @UriParam(label = "client")
    private String sessionName;

    @UriParam(label = "client")
    private Long sessionTimeout;

    @UriParam(label = "client")
    private Long maxPendingPublishRequests;

    @UriParam(label = "client")
    private Long maxResponseMessageSize;

    @UriParam(label = "client")
    private String keyStoreUrl;

    @UriParam(label = "client")
    private String keyStoreType = KeyStoreLoader.DEFAULT_KEY_STORE_TYPE;

    @UriParam(label = "client")
    private String keyAlias;

    @UriParam(label = "client", secret = true)
    private String keyStorePassword;

    @UriParam(label = "client", secret = true)
    private String keyPassword;

    @UriParam(label = "client", javaType = "java.lang.String")
    private Set<String> allowedSecurityPolicies = new HashSet<>();

    @UriParam(label = "client")
    private boolean overrideHost;

    @UriParam(label = "client", defaultValue = "1_000.0")
    private Double requestedPublishingInterval = DEFAULT_REQUESTED_PUBLISHING_INTERVAL;

    public MiloClientConfiguration() {
    }

    public MiloClientConfiguration(final MiloClientConfiguration other) {
        this.endpointUri = other.endpointUri;
        this.discoveryEndpointUri = other.discoveryEndpointUri;
        this.discoveryEndpointSuffix = other.discoveryEndpointSuffix;
        this.clientId = other.clientId;
        this.applicationName = other.applicationName;
        this.applicationUri = other.applicationUri;
        this.productUri = other.productUri;
        this.requestTimeout = other.requestTimeout;
        this.channelLifetime = other.channelLifetime;
        this.sessionName = other.sessionName;
        this.maxPendingPublishRequests = other.maxPendingPublishRequests;
        this.maxResponseMessageSize = other.maxResponseMessageSize;
        this.keyStoreUrl = other.keyStoreUrl;
        this.keyStoreType = other.keyStoreType;
        this.keyAlias = other.keyAlias;
        this.keyStorePassword = other.keyStorePassword;
        this.keyPassword = other.keyPassword;
        this.allowedSecurityPolicies = allowedSecurityPolicies != null ? new HashSet<>(other.allowedSecurityPolicies) : null;
        this.overrideHost = other.overrideHost;
        this.requestedPublishingInterval = other.requestedPublishingInterval;
    }

    /**
     * The OPC UA server endpoint
     */
    public void setEndpointUri(final String endpointUri) {
        this.endpointUri = endpointUri;
    }

    public String getEndpointUri() {
        return this.endpointUri;
    }

    /**
     * An alternative discovery URI
     */
    public void setDiscoveryEndpointUri(final String endpointDiscoveryUri) {
        this.discoveryEndpointUri = endpointDiscoveryUri;
    }

    public String getDiscoveryEndpointUri() {
        return this.discoveryEndpointUri;
    }

    /**
     * A suffix for endpoint URI when discovering
     */
    public void setDiscoveryEndpointSuffix(final String endpointDiscoverySuffix) {
        this.discoveryEndpointSuffix = endpointDiscoverySuffix;
    }

    public String getDiscoveryEndpointSuffix() {
        return this.discoveryEndpointSuffix;
    }

    /**
     * A virtual client id to force the creation of a new connection instance
     */
    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return this.clientId;
    }

    /**
     * The application name
     */
    public void setApplicationName(final String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationName() {
        return this.applicationName;
    }

    /**
     * The application URI
     */
    public void setApplicationUri(final String applicationUri) {
        this.applicationUri = applicationUri;
    }

    public String getApplicationUri() {
        return this.applicationUri;
    }

    /**
     * The product URI
     */
    public void setProductUri(final String productUri) {
        this.productUri = productUri;
    }

    public String getProductUri() {
        return this.productUri;
    }

    /**
     * Request timeout in milliseconds
     */
    public void setRequestTimeout(final Long reconnectTimeout) {
        this.requestTimeout = reconnectTimeout;
    }

    public Long getRequestTimeout() {
        return this.requestTimeout;
    }

    /**
     * Channel lifetime in milliseconds
     */
    public void setChannelLifetime(final Long channelLifetime) {
        this.channelLifetime = channelLifetime;
    }

    public Long getChannelLifetime() {
        return this.channelLifetime;
    }

    /**
     * Session name
     */
    public void setSessionName(final String sessionName) {
        this.sessionName = sessionName;
    }

    public String getSessionName() {
        return this.sessionName;
    }

    /**
     * Session timeout in milliseconds
     */
    public void setSessionTimeout(final Long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public Long getSessionTimeout() {
        return this.sessionTimeout;
    }

    /**
     * The maximum number of pending publish requests
     */
    public void setMaxPendingPublishRequests(final Long maxPendingPublishRequests) {
        this.maxPendingPublishRequests = maxPendingPublishRequests;
    }

    public Long getMaxPendingPublishRequests() {
        return this.maxPendingPublishRequests;
    }

    /**
     * The maximum number of bytes a response message may have
     */
    public void setMaxResponseMessageSize(final Long maxResponseMessageSize) {
        this.maxResponseMessageSize = maxResponseMessageSize;
    }

    public Long getMaxResponseMessageSize() {
        return this.maxResponseMessageSize;
    }

    /**
     * The URL where the key should be loaded from
     */
    public void setKeyStoreUrl(String keyStoreUrl) {
        this.keyStoreUrl = keyStoreUrl;
    }

    public String getKeyStoreUrl() {
        return this.keyStoreUrl;
    }

    /**
     * The key store type
     */
    public void setKeyStoreType(final String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreType() {
        return this.keyStoreType;
    }

    /**
     * The name of the key in the keystore file
     */
    public void setKeyAlias(final String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getKeyAlias() {
        return this.keyAlias;
    }

    /**
     * The keystore password
     */
    public void setKeyStorePassword(final String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStorePassword() {
        return this.keyStorePassword;
    }

    /**
     * The key password
     */
    public void setKeyPassword(final String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getKeyPassword() {
        return this.keyPassword;
    }

    /**
     * A set of allowed security policy URIs. Default is to accept all and use
     * the highest.
     */
    public void setAllowedSecurityPolicies(final Set<String> allowedSecurityPolicies) {
        this.allowedSecurityPolicies = allowedSecurityPolicies;
    }

    public void setAllowedSecurityPolicies(final String allowedSecurityPolicies) {

        // check if we are reset or set

        if (allowedSecurityPolicies == null) {
            // resetting to null
            this.allowedSecurityPolicies = null;
            return;
        }

        // split and convert

        this.allowedSecurityPolicies = new HashSet<>();
        final String[] policies = allowedSecurityPolicies.split(",");
        for (final String policy : policies) {

            String adding = null;
            try {
                adding = SecurityPolicy.fromUri(policy).getUri();
            } catch (Exception e) {
            }
            if (adding == null) {
                try {
                    adding = SecurityPolicy.valueOf(policy).getUri();
                } catch (Exception e) {
                }
            }

            if (adding == null) {
                throw new RuntimeException("Unknown security policy: " + policy);
            }

            this.allowedSecurityPolicies.add(adding);
        }

    }

    public Set<String> getAllowedSecurityPolicies() {
        return this.allowedSecurityPolicies;
    }

    /**
     * Override the server reported endpoint host with the host from the
     * endpoint URI.
     */
    public void setOverrideHost(boolean overrideHost) {
        this.overrideHost = overrideHost;
    }

    public boolean isOverrideHost() {
        return overrideHost;
    }

    /**
     * The requested publishing interval in milliseconds
     */
    public void setRequestedPublishingInterval(Double requestedPublishingInterval) {
        this.requestedPublishingInterval = requestedPublishingInterval;
    }

    public Double getRequestedPublishingInterval() {
        return requestedPublishingInterval;
    }

    @Override
    public MiloClientConfiguration clone() {
        return new MiloClientConfiguration(this);
    }

    public String toCacheId() {
        if (this.clientId != null && !this.clientId.isEmpty()) {
            return this.endpointUri + "|" + this.clientId;
        } else {
            return this.endpointUri;
        }
    }

    public OpcUaClientConfigBuilder newBuilder() {
        return mapToClientConfiguration(this);
    }

    private static OpcUaClientConfigBuilder mapToClientConfiguration(final MiloClientConfiguration configuration) {
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

        if (configuration.getKeyStoreUrl() != null) {
            setKey(configuration, builder);
        }

        return builder;
    }

    private static void setKey(final MiloClientConfiguration configuration, final OpcUaClientConfigBuilder builder) {
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

    private static void whenHasText(final Supplier<String> valueSupplier, final Consumer<String> valueConsumer) {
        final String value = valueSupplier.get();
        if (value != null && !value.isEmpty()) {
            valueConsumer.accept(value);
        }
    }

}
