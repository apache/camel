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

import javax.net.ssl.SSLContext;
import java.security.Security;

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
    @Metadata
    private String keyStore;
    @Metadata
    private String keystorePassword;
    @Metadata
    private String trustStore;
    @Metadata
    private String trustStorePassword;
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
     *
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
     *
     * See Appendix A in the https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html for
     * information about standard protocol names.
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

    public String getKeyStore() {
        return keyStore;
    }

    /**
     * Sets the SSL Keystore resource.
     */
    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
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
     * Sets the SSL Truststore resource.
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
     *
     * The list of available providers returned by java.security.Security.getProviders() or null to use the highest
     * priority provider implementing the secure socket protocol.
     */
    public SSLConfigurationProperties withProvider(String provider) {
        this.provider = provider;
        return this;
    }

    /**
     * The optional protocol for the secure sockets created by the SSLContext.
     *
     * See Appendix A in the https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html for
     * information about standard protocol names.
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
     * Sets the SSL Keystore.
     */
    public SSLConfigurationProperties withKeyStore(String keyStore) {
        this.keyStore = keyStore;
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
     * Sets the SSL Truststore.
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
     * Sets the configuration for server-side client-authentication requirements
     */
    public SSLConfigurationProperties withClientAuthentication(String clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
        return this;
    }

}
