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

import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.milo.KeyStoreLoader;
import org.apache.camel.component.milo.KeyStoreLoader.Result;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.shaded.com.google.common.base.Supplier;

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

    @UriParam(label = "client", defaultValue = "10000")
    private long reconnectTimeout = 10_000;

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

    @UriParam(label = "client", security = "secret")
    private String keyStorePassword;

    @UriParam(label = "client", security = "secret")
    private String keyPassword;

    @UriParam(label = "client", javaType = "java.lang.String")
    private Set<String> allowedSecurityPolicies = new HashSet<>();

    @UriParam(label = "client")
    private boolean overrideHost;

    @UriParam(label = "client")
    private boolean overridePort;

    @UriParam(label = "client", defaultValue = "1_000.0")
    private Double requestedPublishingInterval = DEFAULT_REQUESTED_PUBLISHING_INTERVAL;

    @UriParam(label = "security")
    private String username;

    @UriParam(label = "security", security = "secret")
    private String password;

    @UriParam(label = "security")
    private String trustListDirectory;

    @UriParam(label = "security")
    private CertificateValidator certificateValidator;

    @UriParam(label = "security", security = "insecure:ssl")
    private boolean trustAllCertificates;

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
        this.reconnectTimeout = other.reconnectTimeout;
        this.channelLifetime = other.channelLifetime;
        this.sessionName = other.sessionName;
        this.maxPendingPublishRequests = other.maxPendingPublishRequests;
        this.maxResponseMessageSize = other.maxResponseMessageSize;
        this.keyStoreUrl = other.keyStoreUrl;
        this.keyStoreType = other.keyStoreType;
        this.keyAlias = other.keyAlias;
        this.keyStorePassword = other.keyStorePassword;
        this.keyPassword = other.keyPassword;
        this.allowedSecurityPolicies = new HashSet<>(other.allowedSecurityPolicies);
        this.overrideHost = other.overrideHost;
        this.overridePort = other.overridePort;
        this.requestedPublishingInterval = other.requestedPublishingInterval;
        this.username = other.username;
        this.password = other.password;
        this.trustListDirectory = other.trustListDirectory;
        this.certificateValidator = other.certificateValidator;
        this.trustAllCertificates = other.trustAllCertificates;
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
    public void setRequestTimeout(final Long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Long getRequestTimeout() {
        return this.requestTimeout;
    }

    /**
     * The delay in milliseconds before the client tries again to connect to the server after an attempt failed. It is
     * not used while the OPC UA client recovers a lost session on its own.
     */
    public void setReconnectTimeout(final long reconnectTimeout) {
        if (reconnectTimeout <= 0) {
            throw new IllegalArgumentException("reconnectTimeout must be > 0, got: " + reconnectTimeout);
        }
        this.reconnectTimeout = reconnectTimeout;
    }

    public long getReconnectTimeout() {
        return this.reconnectTimeout;
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
     * A set of allowed security policy URIs. Default is to accept all and use the highest.
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
                throw new RuntimeCamelException("Unknown security policy: " + policy);
            }

            this.allowedSecurityPolicies.add(adding);
        }

    }

    public Set<String> getAllowedSecurityPolicies() {
        return this.allowedSecurityPolicies;
    }

    /**
     * Override the server reported endpoint host with the host from the endpoint URI.
     */
    public void setOverrideHost(boolean overrideHost) {
        this.overrideHost = overrideHost;
    }

    public boolean isOverrideHost() {
        return overrideHost;
    }

    /**
     * Override the server reported endpoint port with the port from the endpoint URI.
     */
    public void setOverridePort(boolean overridePort) {
        this.overridePort = overridePort;
    }

    public boolean isOverridePort() {
        return overridePort;
    }

    /**
     * The requested publishing interval in milliseconds
     */
    public void setRequestedPublishingInterval(Double requestedPublishingInterval) {
        this.requestedPublishingInterval = requestedPublishingInterval;
    }

    /**
     * The username for authentication. Use this instead of embedding credentials in the endpoint URI when the username
     * contains special characters (such as {@code ?}, {@code /}, {@code @}, {@code &}).
     */
    public void setUsername(final String username) {
        this.username = username;
    }

    public String getUsername() {
        return this.username;
    }

    /**
     * The password for authentication. Use this instead of embedding credentials in the endpoint URI when the password
     * contains special characters (such as {@code ?}, {@code /}, {@code @}, {@code &}).
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    public String getPassword() {
        return this.password;
    }

    /**
     * The directory holding the trust list used to validate the certificate of the OPC UA server, in the layout used by
     * Eclipse Milo: trusted certificates in trusted/certs, CRLs of trusted certificates in trusted/crl, certificates of
     * issuing CAs in issuer/certs and their CRLs in issuer/crl. The directories are created if they do not exist.
     * Besides the chain of trust, the validity period, the revocation by a CRL of the trust list and the application
     * URI of the server certificate are checked. A certificate issued by a CA is refused when the trust list holds no
     * CRL of that CA; an empty CRL is enough. A server certificate that does not chain up to a trusted certificate is
     * written to rejected/certs, from where it can be moved to trusted/certs once it has been checked.
     */
    public void setTrustListDirectory(final String trustListDirectory) {
        this.trustListDirectory = trustListDirectory;
    }

    public String getTrustListDirectory() {
        return this.trustListDirectory;
    }

    /**
     * A custom validator for the certificate of the OPC UA server. When set, it is used instead of the trust list
     * configured with trustListDirectory. Endpoints share a connection only when they use the same validator instance.
     */
    public void setCertificateValidator(final CertificateValidator certificateValidator) {
        this.certificateValidator = certificateValidator;
    }

    public CertificateValidator getCertificateValidator() {
        return this.certificateValidator;
    }

    /**
     * Whether to accept any certificate presented by the OPC UA server. This is insecure: the connection is encrypted,
     * but the client does not authenticate the server it talks to. It only applies when neither certificateValidator
     * nor trustListDirectory is configured.
     */
    public void setTrustAllCertificates(final boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isTrustAllCertificates() {
        return this.trustAllCertificates;
    }

    public Double getRequestedPublishingInterval() {
        return requestedPublishingInterval;
    }

    @Override
    public MiloClientConfiguration clone() {
        try {
            MiloClientConfiguration copy = (MiloClientConfiguration) super.clone();
            copy.allowedSecurityPolicies
                    = this.allowedSecurityPolicies != null ? new HashSet<>(this.allowedSecurityPolicies) : null;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String toCacheId() {
        final String cacheId;
        if (this.clientId != null && !this.clientId.isEmpty()) {
            cacheId = this.endpointUri + "|" + this.clientId + "|" + this.requestedPublishingInterval;
        } else {
            cacheId = this.endpointUri + "|" + this.requestedPublishingInterval;
        }
        // endpoints that validate the server certificate differently must not share a connection
        return cacheId + serverCertificateTrustCacheId();
    }

    private String serverCertificateTrustCacheId() {
        if (this.certificateValidator != null) {
            return "|certificateValidator=" + this.certificateValidator.getClass().getName() + "@"
                   + Integer.toHexString(System.identityHashCode(this.certificateValidator));
        }
        if (this.trustListDirectory != null && !this.trustListDirectory.isEmpty()) {
            return "|trustListDirectory=" + this.trustListDirectory;
        }
        if (this.trustAllCertificates) {
            return "|trustAllCertificates";
        }
        return "";
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
        // FIXME - validate moving of ChannelLifetime to SubscriptionManager
        //        if (configuration.getChannelLifetime() != null) {
        //            builder.setChannelLifetime(Unsigned.uint(configuration.getChannelLifetime()));
        //        }

        whenHasText(configuration::getSessionName, value -> builder.setSessionName(() -> value));
        if (configuration.getSessionTimeout() != null) {
            builder.setSessionTimeout(UInteger.valueOf(configuration.getSessionTimeout()));
        }

        if (configuration.getMaxPendingPublishRequests() != null) {
            builder.setMaxPendingPublishRequests(UInteger.valueOf(configuration.getMaxPendingPublishRequests()));
        }

        if (configuration.getMaxResponseMessageSize() != null) {
            builder.setMaxResponseMessageSize(UInteger.valueOf(configuration.getMaxResponseMessageSize()));
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
        builder.setCertificateChain(result.getCertificateChain());
        builder.setKeyPair(result.getKeyPair());
    }

    private static void whenHasText(final Supplier<String> valueSupplier, final Consumer<String> valueConsumer) {
        final String value = valueSupplier.get();
        if (value != null && !value.isEmpty()) {
            valueConsumer.accept(value);
        }
    }

}
