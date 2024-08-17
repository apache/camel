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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Global configuration for SSL.
 */
@Configurer(bootstrap = true)
public class SSLConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private boolean enabled;
    @Metadata(label = "advanced")
    private String provider;
    @Metadata(label = "advanced", defaultValue = "TLSv1.3")
    private String secureSocketProtocol;
    @Metadata(label = "advanced")
    private String certAlias;
    @Metadata(label = "advanced", defaultValue = "86400")
    private int sessionTimeout;
    @Metadata(label = "advanced")
    private String cipherSuites;
    @Metadata(label = "advanced")
    private String cipherSuitesInclude;
    @Metadata(label = "advanced")
    private String cipherSuitesExclude;
    @Metadata
    private String keyStore;
    @Metadata(label = "advanced")
    private String keyStoreType;
    @Metadata(label = "advanced")
    private String keyStoreProvider;
    @Metadata
    private String keystorePassword;
    @Metadata
    private String trustStore;
    @Metadata
    private String trustStorePassword;
    @Metadata(label = "advanced")
    private String keyManagerAlgorithm;
    @Metadata(label = "advanced")
    private String keyManagerProvider;
    @Metadata(label = "advanced")
    private String secureRandomAlgorithm;
    @Metadata(label = "advanced")
    private String secureRandomProvider;
    @Metadata(defaultValue = "NONE", enums = "NONE,WANT,REQUIRE")
    private String clientAuthentication = "NONE";

    public SSLConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables SSL in your Camel application.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * To use a specific provider for creating SSLContext.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSecureSocketProtocol() {
        return secureSocketProtocol;
    }

    /**
     * The optional protocol for the secure sockets created by the SSLContext.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public void setSecureSocketProtocol(String secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol;
    }

    public String getCertAlias() {
        return certAlias;
    }

    /**
     * An optional certificate alias to use. This is useful when the keystore has multiple certificates.
     */
    public void setCertAlias(String certAlias) {
        this.certAlias = certAlias;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Timeout in seconds to use for SSLContext. The default is 24 hours.
     */
    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public String getCipherSuites() {
        return cipherSuites;
    }

    /**
     * List of TLS/SSL cipher suite algorithm names. Multiple names can be separated by comma.
     */
    public void setCipherSuites(String cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    public String getCipherSuitesInclude() {
        return cipherSuitesInclude;
    }

    /**
     * Filters TLS/SSL cipher suites algorithms names.
     * <p>
     * This filter is used for including algorithms that matches the naming pattern. Multiple names can be separated by
     * comma.
     * <p>
     * Notice that if the cipherSuites option has been configured then the include/exclude filters are not in use.
     */
    public void setCipherSuitesInclude(String cipherSuitesInclude) {
        this.cipherSuitesInclude = cipherSuitesInclude;
    }

    public String getCipherSuitesExclude() {
        return cipherSuitesExclude;
    }

    /**
     * Filters TLS/SSL cipher suites algorithms names.
     * <p>
     * This filter is used for excluding algorithms that matches the naming pattern. Multiple names can be separated by
     * comma.
     * <p>
     * Notice that if the cipherSuites option has been configured then the include/exclude filters are not in use.
     */
    public void setCipherSuitesExclude(String cipherSuitesExclude) {
        this.cipherSuitesExclude = cipherSuitesExclude;
    }

    public String getKeyStore() {
        return keyStore;
    }

    /**
     * The file path, class path resource, or URL of the resource used to load the key store.
     *
     * An existing java.security.KeyStore can also be referred using #bean:name syntax.
     */
    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    /**
     * The type of the key store to load.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    /**
     * To use a specific provider for creating KeyStore.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public void setKeyStoreProvider(String keyStoreProvider) {
        this.keyStoreProvider = keyStoreProvider;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * Sets the SSL Keystore password.
     */
    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getTrustStore() {
        return trustStore;
    }

    /**
     * The file path, class path resource, or URL of the resource used to load the trust store.
     *
     * An existing java.security.KeyStore can also be referred using #bean:name syntax.
     */
    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    /**
     * Sets the SSL Truststore password.
     */
    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }

    /**
     * Algorithm name used for creating the KeyManagerFactory.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }

    public String getKeyManagerProvider() {
        return keyManagerProvider;
    }

    /**
     * To use a specific provider for creating KeyManagerFactory.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public void setKeyManagerProvider(String keyManagerProvider) {
        this.keyManagerProvider = keyManagerProvider;
    }

    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }

    /**
     * Algorithm name used for creating the SecureRandom.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }

    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }

    /**
     * To use a specific provider for creating SecureRandom.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }

    public String getClientAuthentication() {
        return clientAuthentication;
    }

    /**
     * Sets the configuration for server-side client-authentication requirements
     */
    public void setClientAuthentication(String clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
    }

    /**
     * Enables SSL in your Camel application.
     */
    public SSLConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * To use a specific provider for creating SSLContext.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public SSLConfigurationProperties withProvider(String provider) {
        this.provider = provider;
        return this;
    }

    /**
     * The optional protocol for the secure sockets created by the SSLContext.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public SSLConfigurationProperties withSecureSocketProtocol(String secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol;
        return this;
    }

    /**
     * An optional certificate alias to use. This is useful when the keystore has multiple certificates.
     */
    public SSLConfigurationProperties withCertAlias(String certAlias) {
        this.certAlias = certAlias;
        return this;
    }

    /**
     * Timeout in seconds to use for SSLContext. The default is 24 hours.
     */
    public SSLConfigurationProperties withSessionTimeoutCertAlias(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * List of TLS/SSL cipher suite algorithm names. Multiple names can be separated by comma.
     */
    public SSLConfigurationProperties withCipherSuites(String cipherSuites) {
        this.cipherSuites = cipherSuites;
        return this;
    }

    /**
     * Filters TLS/SSL cipher suites algorithms names.
     * <p>
     * This filter is used for including algorithms that matches the naming pattern. Multiple names can be separated by
     * comma.
     * <p>
     * Notice that if the cipherSuites option has been configured then the include/exclude filters are not in use.
     */
    public SSLConfigurationProperties withCipherSuitesInclude(String cipherSuitesInclude) {
        this.cipherSuitesInclude = cipherSuitesInclude;
        return this;
    }

    /**
     * Filters TLS/SSL cipher suites algorithms names.
     * <p>
     * This filter is used for excluding algorithms that matches the naming pattern. Multiple names can be separated by
     * comma.
     * <p>
     * Notice that if the cipherSuites option has been configured then the include/exclude filters are not in use.
     */
    public SSLConfigurationProperties withCipherSuitesExclude(String cipherSuitesExclude) {
        this.cipherSuitesExclude = cipherSuitesExclude;
        return this;
    }

    /**
     * The file path, class path resource, or URL of the resource used to load the key store.
     *
     * An existing java.security.KeyStore can also be referred using #bean:name syntax.
     */
    public SSLConfigurationProperties withKeyStore(String keyStore) {
        this.keyStore = keyStore;
        return this;
    }

    /**
     * The type of the key store to load.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public SSLConfigurationProperties withKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
        return this;
    }

    /**
     * To use a specific provider for creating KeyStore.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public SSLConfigurationProperties withKeyStoreProvider(String keyStoreProvider) {
        this.keyStoreProvider = keyStoreProvider;
        return this;
    }

    /**
     * Sets the SSL Keystore password.
     */
    public SSLConfigurationProperties withKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
        return this;
    }

    /**
     * The file path, class path resource, or URL of the resource used to load the trust store.
     *
     * An existing java.security.KeyStore can also be referred using #bean:name syntax.
     */
    public SSLConfigurationProperties withTrustStore(String trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    /**
     * Sets the SSL Truststore password.
     */
    public SSLConfigurationProperties withTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    /**
     * Algorithm name used for creating the KeyManagerFactory.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public SSLConfigurationProperties withKeyManagerAlgorithm(String keyManagerAlgorithm) {
        this.keyManagerAlgorithm = keyManagerAlgorithm;
        return this;
    }

    /**
     * To use a specific provider for creating KeyManagerFactory.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public SSLConfigurationProperties withKeyManagerProvider(String keyManagerProvider) {
        this.keyManagerProvider = keyManagerProvider;
        return this;
    }

    /**
     * Algorithm name used for creating the SecureRandom.
     * <p>
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public SSLConfigurationProperties withSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
        return this;
    }

    /**
     * To use a specific provider for creating SecureRandom.
     * <p>
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public SSLConfigurationProperties withSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
        return this;
    }

    /**
     * Sets the configuration for server-side client-authentication requirements
     */
    public SSLConfigurationProperties withClientAuthentication(String clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
        return this;
    }

}
