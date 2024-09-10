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
package org.apache.camel.support.jsse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents {@link SSLContext} configuration options used in instantiating an {@code SSLContext} instance.
 */
public class SSLContextParameters extends BaseSSLContextParameters {

    protected static final String DEFAULT_SECURE_SOCKET_PROTOCOL = "TLSv1.3";

    private static final Logger LOG = LoggerFactory.getLogger(SSLContextParameters.class);

    /**
     * The optional key manager configuration for creating the {@link KeyManager}s used in constructing an
     * {@link SSLContext}.
     */
    private KeyManagersParameters keyManagers;

    /**
     * The optional trust manager configuration for creating the {@link TrustManager}s used in constructing an
     * {@link SSLContext}.
     */
    private TrustManagersParameters trustManagers;

    /**
     * The optional secure random configuration options to use for constructing the {@link SecureRandom} used in the
     * creation of an {@link SSLContext}.
     */
    private SecureRandomParameters secureRandom;

    /**
     * The optional configuration options to be applied purely to the client side settings of the {@link SSLContext}.
     * Settings specified here override any duplicate settings provided at the overall level by this class. These
     * parameters apply to {@link SSLSocketFactory}s and {@link SSLEngine}s produced by the {@code SSLContext} produced
     * from this class as well as to the {@code SSLContext} itself.
     */
    private SSLContextClientParameters clientParameters;

    /**
     * The optional configuration options to be applied purely to the server side settings of the {@link SSLContext}.
     * Settings specified here override any duplicate settings provided at the overall level by this class. These
     * parameters apply to {@link SSLServerSocketFactory}s and {@link SSLEngine}s produced by the {@code SSLContext}
     * produced from this class as well as to the {@code SSLContext} itself.
     */
    private SSLContextServerParameters serverParameters;

    /**
     * The optional provider identifier for the JSSE implementation to use when constructing an {@link SSLContext}.
     */
    private String provider;

    /**
     * The optional protocol for the secure sockets created by the {@link SSLContext} represented by this instance's
     * configuration.
     *
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    private String secureSocketProtocol;

    /**
     * An optional certificate alias to use. This is useful when the keystore has multiple certificates.
     */
    private String certAlias;

    public KeyManagersParameters getKeyManagers() {
        return keyManagers;
    }

    /**
     * Sets the optional key manager configuration for creating the {@link KeyManager}s used in constructing an
     * {@link SSLContext}.
     *
     * @param keyManagers the options or {@code null} to provide no {@code KeyManager}s
     */
    public void setKeyManagers(KeyManagersParameters keyManagers) {
        this.keyManagers = keyManagers;
    }

    public TrustManagersParameters getTrustManagers() {
        return trustManagers;
    }

    /**
     * Sets the optional trust manager configuration for creating the {@link TrustManager}s used in constructing an
     * {@link SSLContext}.
     *
     * @param trustManagers the options or {@code null} to provide no {@code TrustManager}s
     */
    public void setTrustManagers(TrustManagersParameters trustManagers) {
        this.trustManagers = trustManagers;
    }

    public SecureRandomParameters getSecureRandom() {
        return secureRandom;
    }

    /**
     * Sets the optional secure random configuration options to use for constructing the {@link SecureRandom} used in
     * the creation of an {@link SSLContext}.
     *
     * @param secureRandom the options or {@code null} to use the default
     */
    public void setSecureRandom(SecureRandomParameters secureRandom) {
        this.secureRandom = secureRandom;
    }

    public SSLContextClientParameters getClientParameters() {
        return clientParameters;
    }

    /**
     * The optional configuration options to be applied purely to the client side settings of the {@link SSLContext}.
     * Settings specified here override any duplicate settings provided at the overall level by this class. These
     * parameters apply to {@link SSLSocketFactory}s and {@link SSLEngine}s produced by the {@code SSLContext} produced
     * from this class as well as to the {@code SSLContext} itself.
     *
     * @param clientParameters the optional additional client-side parameters
     */
    public void setClientParameters(SSLContextClientParameters clientParameters) {
        this.clientParameters = clientParameters;
    }

    public SSLContextServerParameters getServerParameters() {
        return serverParameters;
    }

    /**
     * The optional configuration options to be applied purely to the server side settings of the {@link SSLContext}.
     * Settings specified here override any duplicate settings provided at the overall level by this class. These
     * parameters apply to {@link SSLServerSocketFactory}s and {@link SSLEngine}s produced by the {@code SSLContext}
     * produced from this class as well as to the {@code SSLContext} itself.
     *
     * @param serverParameters the optional additional client-side parameters
     */
    public void setServerParameters(SSLContextServerParameters serverParameters) {
        this.serverParameters = serverParameters;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Sets the optional provider identifier to use when constructing an {@link SSLContext}.
     *
     * @param provider the identifier (from the list of available providers returned by {@link Security#getProviders()})
     *                 or {@code null} to use the highest priority provider implementing the secure socket protocol
     *
     * @see            Security#getProviders(java.util.Map)
     * @see            #setSecureSocketProtocol(String)
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSecureSocketProtocol() {
        if (this.secureSocketProtocol == null) {
            return DEFAULT_SECURE_SOCKET_PROTOCOL;
        }
        return this.secureSocketProtocol;
    }

    /**
     * Sets the optional protocol for the secure sockets created by the {@link SSLContext} represented by this
     * instance's configuration. Defaults to TLS.
     *
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     *
     * @param secureSocketProtocol the name of the protocol or {@code null} to use the default (TLS)
     */
    public void setSecureSocketProtocol(String secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol;
    }

    public String getCertAlias() {
        return certAlias;
    }

    /**
     * An optional certificate alias to use. This is useful when the keystore has multiple certificates.
     *
     * @param certAlias an optional certificate alias to use
     */
    public void setCertAlias(String certAlias) {
        this.certAlias = certAlias;
    }

    ////////////////////////////////////////////

    /**
     * Creates an {@link SSLContext} based on the related configuration options of this instance. Namely,
     * {@link #keyManagers}, {@link #trustManagers}, and {@link #secureRandom}, but also respecting the chosen provider
     * and secure socket protocol as well.
     *
     * @param  camelContext             The camel context
     *
     * @return                          a newly configured instance
     *
     * @throws GeneralSecurityException if there is a problem in this instances configuration or that of its nested
     *                                  configuration options
     * @throws IOException              if there is an error reading a key/trust store
     */
    public SSLContext createSSLContext(CamelContext camelContext) throws GeneralSecurityException, IOException {
        if (camelContext != null) {
            // setup CamelContext before creating SSLContext
            setCamelContext(camelContext);
            if (keyManagers != null) {
                keyManagers.setCamelContext(camelContext);
                if (keyManagers.getKeyStore() != null) {
                    keyManagers.getKeyStore().setCamelContext(camelContext);
                }
            }
            if (trustManagers != null) {
                trustManagers.setCamelContext(camelContext);
                if (trustManagers.getKeyStore() != null) {
                    trustManagers.getKeyStore().setCamelContext(camelContext);
                }
            }
            if (secureRandom != null) {
                secureRandom.setCamelContext(camelContext);
            }
            if (clientParameters != null) {
                clientParameters.setCamelContext(camelContext);
            }
            if (serverParameters != null) {
                serverParameters.setCamelContext(camelContext);
            }
        }

        LOG.trace("Creating SSLContext from SSLContextParameters [{}].", this);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Available Security providers: {}.", Arrays.toString(Security.getProviders()));
        }

        KeyManager[] keyManagers = this.keyManagers == null ? null : this.keyManagers.createKeyManagers();
        TrustManager[] trustManagers = this.trustManagers == null ? null : this.trustManagers.createTrustManagers();
        SecureRandom secureRandom = this.secureRandom == null ? null : this.secureRandom.createSecureRandom();

        SSLContext context;
        if (this.getProvider() == null) {
            context = SSLContext.getInstance(this.parsePropertyValue(this.getSecureSocketProtocol()));
        } else {
            context = SSLContext.getInstance(this.parsePropertyValue(this.getSecureSocketProtocol()),
                    this.parsePropertyValue(this.getProvider()));
        }

        if (this.getCertAlias() != null && keyManagers != null) {
            for (int idx = 0; idx < keyManagers.length; idx++) {
                if (keyManagers[idx] instanceof X509KeyManager x509KeyManager) {
                    try {
                        keyManagers[idx] = new AliasedX509ExtendedKeyManager(
                                this.parsePropertyValue(this.getCertAlias()),
                                x509KeyManager);
                    } catch (Exception e) {
                        throw new GeneralSecurityException(e);
                    }
                }
            }
        }

        LOG.debug(
                "SSLContext [{}], initialized from [{}], is using provider [{}], protocol [{}], key managers {}, trust managers {}, and secure random [{}].",
                context, this, context.getProvider(), context.getProtocol(), keyManagers, trustManagers,
                secureRandom);

        context.init(keyManagers, trustManagers, secureRandom);

        this.configureSSLContext(context);

        // Decorate the context.
        context = new SSLContextDecorator(
                new SSLContextSpiDecorator(
                        context,
                        this.getSSLEngineConfigurers(context),
                        this.getSSLSocketFactoryConfigurers(context),
                        this.getSSLServerSocketFactoryConfigurers(context)));

        return context;
    }

    @Override
    protected void configureSSLContext(SSLContext context) throws GeneralSecurityException {
        LOG.trace("Configuring client and server side SSLContext parameters on SSLContext [{}]...", context);
        super.configureSSLContext(context);

        if (this.getClientParameters() != null) {
            LOG.trace("Overriding client-side SSLContext parameters on SSLContext [{}] with configured client parameters.",
                    context);
            this.getClientParameters().configureSSLContext(context);
        }

        if (this.getServerParameters() != null) {
            LOG.trace("Overriding server-side SSLContext parameters on SSLContext [{}] with configured server parameters.",
                    context);
            this.getServerParameters().configureSSLContext(context);
        }

        LOG.trace("Configured client and server side SSLContext parameters on SSLContext [{}].", context);
    }

    @Override
    protected List<Configurer<SSLEngine>> getSSLEngineConfigurers(SSLContext context) {
        LOG.trace("Collecting client and server side SSLEngine configurers on SSLContext [{}]...", context);
        List<Configurer<SSLEngine>> configurers = super.getSSLEngineConfigurers(context);

        if (this.getClientParameters() != null) {
            LOG.trace("Augmenting SSLEngine configurers with configurers from client parameters on SSLContext [{}].",
                    context);
            configurers.addAll(this.getClientParameters().getSSLEngineConfigurers(context));
        }

        if (this.getServerParameters() != null) {
            LOG.trace("Augmenting SSLEngine configurers with configurers from server parameters on SSLContext [{}].",
                    context);
            configurers.addAll(this.getServerParameters().getSSLEngineConfigurers(context));
        }

        LOG.trace("Collected client and server side SSLEngine configurers on SSLContext [{}].", context);

        return configurers;
    }

    @Override
    protected List<Configurer<SSLSocketFactory>> getSSLSocketFactoryConfigurers(SSLContext context) {
        LOG.trace("Collecting SSLSocketFactory configurers on SSLContext [{}]...", context);
        List<Configurer<SSLSocketFactory>> configurers = super.getSSLSocketFactoryConfigurers(context);

        if (this.getClientParameters() != null) {
            LOG.trace("Augmenting SSLSocketFactory configurers with configurers from client parameters on SSLContext [{}].",
                    context);
            configurers.addAll(this.getClientParameters().getSSLSocketFactoryConfigurers(context));
        }

        LOG.trace("Collected SSLSocketFactory configurers on SSLContext [{}].", context);

        return configurers;
    }

    @Override
    protected List<Configurer<SSLServerSocketFactory>> getSSLServerSocketFactoryConfigurers(SSLContext context) {
        LOG.trace("Collecting SSLServerSocketFactory configurers for SSLContext [{}]...", context);
        List<Configurer<SSLServerSocketFactory>> configurers = super.getSSLServerSocketFactoryConfigurers(context);

        if (this.getServerParameters() != null) {
            LOG.trace(
                    "Augmenting SSLServerSocketFactory configurers with configurers from server parameters for SSLContext [{}].",
                    context);
            configurers.addAll(this.getServerParameters().getSSLServerSocketFactoryConfigurers(context));
        }

        LOG.trace("Collected client and server side SSLServerSocketFactory configurers for SSLContext [{}].", context);

        return configurers;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SSLContextParameters[keyManagers=");
        builder.append(keyManagers);
        builder.append(", trustManagers=");
        builder.append(trustManagers);
        builder.append(", secureRandom=");
        builder.append(secureRandom);
        builder.append(", clientParameters=");
        builder.append(clientParameters);
        builder.append(", serverParameters=");
        builder.append(serverParameters);
        builder.append(", provider=");
        builder.append(provider);
        builder.append(", secureSocketProtocol=");
        builder.append(secureSocketProtocol);
        builder.append(", certAlias=");
        builder.append(certAlias);
        builder.append(", getCipherSuites()=");
        builder.append(getCipherSuites());
        builder.append(", getCipherSuitesFilter()=");
        builder.append(getCipherSuitesFilter());
        builder.append(", getSecureSocketProtocols()=");
        builder.append(getSecureSocketProtocols());
        builder.append(", getSecureSocketProtocolsFilter()=");
        builder.append(getSecureSocketProtocolsFilter());
        builder.append(", getSessionTimeout()=");
        builder.append(getSessionTimeout());
        builder.append("]");
        return builder.toString();
    }

}
