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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import org.apache.camel.CamelContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents {@link SSLContext} configuration options used in instantiating an {@code SSLContext} instance.
 */
public class SSLContextParameters extends BaseSSLContextParameters {

    protected static final String DEFAULT_SECURE_SOCKET_PROTOCOL = "TLSv1.3";

    /**
     * The post-quantum hybrid named groups to detect for auto-configuration. Any one of them is enough to switch the
     * auto-configuration on: a JVM or a crypto policy restricted to the NIST curves offers the SecP* hybrids without
     * the X25519 one.
     */
    private static final List<String> PQC_NAMED_GROUPS
            = List.of("X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024");

    /**
     * The preferred named group ordering when PQC groups are available. This ordering ensures post-quantum key exchange
     * is preferred while maintaining classical fallbacks for compatibility.
     */
    private static final List<String> PQC_PREFERRED_NAMED_GROUPS
            = List.of("X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024", "x25519", "secp256r1", "secp384r1");

    private static final Logger LOG = LoggerFactory.getLogger(SSLContextParameters.class);

    /**
     * Logged once per JVM, so that a deployment without any post-quantum group says so instead of staying silent.
     */
    private static volatile boolean pqcNamedGroupsUnavailableLogged;

    /**
     * Guards the window in which the auto-configured post-quantum ordering is published on this instance. A single
     * {@code SSLContextParameters} is shared by every component of a {@link CamelContext}, so concurrent
     * {@link #createSSLContext(CamelContext)} calls would otherwise race on it.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * The optional key manager configuration for creating the {@link KeyManager}s used in constructing an
     * {@link SSLContext}.
     */
    private @Nullable KeyManagersParameters keyManagers;

    /**
     * The optional trust manager configuration for creating the {@link TrustManager}s used in constructing an
     * {@link SSLContext}.
     */
    private @Nullable TrustManagersParameters trustManagers;

    /**
     * The optional secure random configuration options to use for constructing the {@link SecureRandom} used in the
     * creation of an {@link SSLContext}.
     */
    private @Nullable SecureRandomParameters secureRandom;

    /**
     * The optional configuration options to be applied purely to the client side settings of the {@link SSLContext}.
     * Settings specified here override any duplicate settings provided at the overall level by this class. These
     * parameters apply to {@link SSLSocketFactory}s and {@link SSLEngine}s produced by the {@code SSLContext} produced
     * from this class as well as to the {@code SSLContext} itself.
     */
    private @Nullable SSLContextClientParameters clientParameters;

    /**
     * The optional configuration options to be applied purely to the server side settings of the {@link SSLContext}.
     * Settings specified here override any duplicate settings provided at the overall level by this class. These
     * parameters apply to {@link SSLServerSocketFactory}s and {@link SSLEngine}s produced by the {@code SSLContext}
     * produced from this class as well as to the {@code SSLContext} itself.
     */
    private @Nullable SSLContextServerParameters serverParameters;

    /**
     * The optional provider identifier for the JSSE implementation to use when constructing an {@link SSLContext}.
     */
    private @Nullable String provider;

    /**
     * The optional protocol for the secure sockets created by the {@link SSLContext} represented by this instance's
     * configuration.
     *
     * See https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html
     */
    private @Nullable String secureSocketProtocol;

    /**
     * An optional certificate alias to use. This is useful when the keystore has multiple certificates.
     */
    private @Nullable String certAlias;

    public @Nullable KeyManagersParameters getKeyManagers() {
        return keyManagers;
    }

    /**
     * Sets the optional key manager configuration for creating the {@link KeyManager}s used in constructing an
     * {@link SSLContext}.
     *
     * @param keyManagers the options or {@code null} to provide no {@code KeyManager}s
     */
    public void setKeyManagers(@Nullable KeyManagersParameters keyManagers) {
        this.keyManagers = keyManagers;
    }

    public @Nullable TrustManagersParameters getTrustManagers() {
        return trustManagers;
    }

    /**
     * Sets the optional trust manager configuration for creating the {@link TrustManager}s used in constructing an
     * {@link SSLContext}.
     *
     * @param trustManagers the options or {@code null} to provide no {@code TrustManager}s
     */
    public void setTrustManagers(@Nullable TrustManagersParameters trustManagers) {
        this.trustManagers = trustManagers;
    }

    public @Nullable SecureRandomParameters getSecureRandom() {
        return secureRandom;
    }

    /**
     * Sets the optional secure random configuration options to use for constructing the {@link SecureRandom} used in
     * the creation of an {@link SSLContext}.
     *
     * @param secureRandom the options or {@code null} to use the default
     */
    public void setSecureRandom(@Nullable SecureRandomParameters secureRandom) {
        this.secureRandom = secureRandom;
    }

    public @Nullable SSLContextClientParameters getClientParameters() {
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
    public void setClientParameters(@Nullable SSLContextClientParameters clientParameters) {
        this.clientParameters = clientParameters;
    }

    public @Nullable SSLContextServerParameters getServerParameters() {
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
    public void setServerParameters(@Nullable SSLContextServerParameters serverParameters) {
        this.serverParameters = serverParameters;
    }

    public @Nullable String getProvider() {
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
    public void setProvider(@Nullable String provider) {
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
     * See https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html
     *
     * @param secureSocketProtocol the name of the protocol or {@code null} to use the default (TLS)
     */
    public void setSecureSocketProtocol(@Nullable String secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol;
    }

    public @Nullable String getCertAlias() {
        return certAlias;
    }

    /**
     * An optional certificate alias to use. This is useful when the keystore has multiple certificates.
     *
     * @param certAlias an optional certificate alias to use
     */
    public void setCertAlias(@Nullable String certAlias) {
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
        @Nullable
        TrustManager[] trustManagers = this.trustManagers == null ? null : this.trustManagers.createTrustManagers();
        SecureRandom secureRandom = this.secureRandom == null ? null : this.secureRandom.createSecureRandom();

        String protocol = this.parsePropertyValue(this.getSecureSocketProtocol());
        if (protocol == null) {
            protocol = DEFAULT_SECURE_SOCKET_PROTOCOL;
        }
        SSLContext context;
        if (this.getProvider() == null) {
            context = SSLContext.getInstance(protocol);
        } else {
            String provider = this.parsePropertyValue(this.getProvider());
            context = SSLContext.getInstance(protocol, provider != null ? provider : this.getProvider());
        }

        if (this.getCertAlias() != null && keyManagers != null) {
            for (int idx = 0; idx < keyManagers.length; idx++) {
                if (keyManagers[idx] instanceof X509KeyManager x509KeyManager) {
                    try {
                        String certAlias = this.parsePropertyValue(this.getCertAlias());
                        if (certAlias == null) {
                            certAlias = this.getCertAlias();
                        }
                        keyManagers[idx] = new AliasedX509ExtendedKeyManager(
                                certAlias,
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

        // The auto-configuration publishes the computed ordering on this instance for as long as it takes to build the
        // configurers, which read it, and takes it back afterwards. That window has to be exclusive: this instance is
        // shared by every component of the context, so a concurrent createSSLContext() would otherwise skip the
        // auto-configuration because it saw the ordering of the other thread, or build its configurers after that
        // thread has reset it - leaving the post-quantum preference applied or not depending on timing.
        lock.lock();
        boolean autoConfiguredPqc = false;
        try {
            // Auto-configure PQC named groups when available and no user configuration is present
            if (this.getNamedGroups() == null && this.getNamedGroupsFilter() == null) {
                autoConfiguredPqc = applyPqcNamedGroupDefaults(context);
            }

            this.configureSSLContext(context);

            // Decorate the context.
            context = new SSLContextDecorator(
                    new SSLContextSpiDecorator(
                            context,
                            this.getSSLEngineConfigurers(context),
                            this.getSSLSocketFactoryConfigurers(context),
                            this.getSSLServerSocketFactoryConfigurers(context)));
        } finally {
            // Reset auto-configured PQC named groups so they don't persist on this instance (also on failure)
            if (autoConfiguredPqc) {
                this.setNamedGroups(null);
            }
            lock.unlock();
        }

        return context;
    }

    /**
     * Auto-configures post-quantum named groups when the JVM supports them and the user hasn't explicitly configured
     * named groups or named groups filters.
     * <p/>
     * When a hybrid ML-KEM group is available (typically JDK 25+), this method sets the preferred named group ordering
     * to prioritize post-quantum key exchange, protecting against harvest-now-decrypt-later attacks.
     *
     * @param  context the initialized SSLContext to probe for available named groups
     * @return         {@code true} if PQC named groups were auto-configured, {@code false} otherwise
     */
    private boolean applyPqcNamedGroupDefaults(SSLContext context) {
        SSLEngine probeEngine = context.createSSLEngine();
        List<String> ordered = resolvePqcNamedGroups(getNamedGroupsFromParams(probeEngine.getSSLParameters()));

        if (ordered == null) {
            logPqcNamedGroupsUnavailable();
            return false;
        }

        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(ordered);
        this.setNamedGroups(ngp);

        LOG.info("Auto-configured PQC named groups: {}", ordered);
        return true;
    }

    /**
     * Orders the named groups available on this JVM so that the post-quantum hybrids come first, followed by the
     * classical fallbacks and then everything else, preserving the spelling the JVM uses.
     *
     * @param  availableGroups the named groups the JVM offers
     * @return                 the preferred ordering, or {@code null} when the JVM offers no post-quantum group - in
     *                         which case the ordering is left alone rather than rewritten
     */
    static @Nullable List<String> resolvePqcNamedGroups(String @Nullable [] availableGroups) {
        if (availableGroups == null || availableGroups.length == 0) {
            return null;
        }

        // Match case-insensitively, since the spelling of a group is up to the provider, but keep the name the
        // provider gave: that is the one it accepts back on SSLParameters.
        List<String> ordered = new ArrayList<>();
        boolean postQuantum = false;
        for (String preferred : PQC_PREFERRED_NAMED_GROUPS) {
            for (String available : availableGroups) {
                if (preferred.equalsIgnoreCase(available)) {
                    ordered.add(available);
                    postQuantum |= PQC_NAMED_GROUPS.contains(preferred);
                    break;
                }
            }
        }

        if (!postQuantum) {
            return null;
        }

        for (String group : availableGroups) {
            if (!ordered.contains(group)) {
                ordered.add(group);
            }
        }

        return ordered;
    }

    private static void logPqcNamedGroupsUnavailable() {
        if (!pqcNamedGroupsUnavailableLogged) {
            pqcNamedGroupsUnavailableLogged = true;
            LOG.info("No post-quantum TLS named group ({}) is available on this JVM,"
                     + " so the TLS key exchange stays classical. A JDK offering a hybrid ML-KEM group is required.",
                    String.join(", ", PQC_NAMED_GROUPS));
        }
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
