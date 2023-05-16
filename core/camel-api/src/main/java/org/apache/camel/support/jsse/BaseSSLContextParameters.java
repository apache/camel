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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.camel.support.jsse.FilterParameters.Patterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents configuration options that can be applied in the client-side or server-side context depending on what they
 * are applied to.
 */
public abstract class BaseSSLContextParameters extends JsseParameters {

    protected static final List<String> DEFAULT_CIPHER_SUITES_FILTER_INCLUDE
            = List.of(".*");

    protected static final List<String> DEFAULT_CIPHER_SUITES_FILTER_EXCLUDE
            = List.of(".*_NULL_.*", ".*_anon_.*", ".*_EXPORT_.*", ".*_DES_.*", ".*MD5", ".*RC4.*");

    protected static final List<String> DEFAULT_SECURE_SOCKET_PROTOCOLS_FILTER_INCLUDE
            = List.of(".*");

    protected static final List<String> DEFAULT_SECURE_SOCKET_PROTOCOLS_FILTER_EXCLUDE
            = List.of("SSL.*");

    private static final Logger LOG = LoggerFactory.getLogger(BaseSSLContextParameters.class);

    private static final String LS = System.lineSeparator();

    private static final String SSL_ENGINE_CIPHER_SUITE_LOG_MSG = createCipherSuiteLogMessage("SSLEngine");

    private static final String SSL_SOCKET_CIPHER_SUITE_LOG_MSG = createCipherSuiteLogMessage("SSLSocket");

    private static final String SSL_SERVER_SOCKET_CIPHER_SUITE_LOG_MSG = createCipherSuiteLogMessage("SSLServerSocket");

    private static final String SSL_ENGINE_PROTOCOL_LOG_MSG = createProtocolLogMessage("SSLEngine");

    private static final String SSL_SOCKET_PROTOCOL_LOG_MSG = createProtocolLogMessage("SSLSocket");

    private static final String SSL_SERVER_SOCKET_PROTOCOL_LOG_MSG = createProtocolLogMessage("SSLServerSocket");

    /**
     * The optional explicitly configured cipher suites for this configuration.
     */
    private CipherSuitesParameters cipherSuites;

    /**
     * The optional cipher suite filter configuration for this configuration.
     */
    private FilterParameters cipherSuitesFilter;

    /**
     * The optional explicitly configured secure socket protocol names for this configuration.
     */
    private SecureSocketProtocolsParameters secureSocketProtocols;

    /**
     * The option secure socket protocol name filter configuration for this configuration.
     */
    private FilterParameters secureSocketProtocolsFilter;

    /**
     * The optional {@link SSLSessionContext} timeout time for {@link javax.net.ssl.SSLSession}s in seconds.
     */
    private String sessionTimeout;

    protected List<SNIServerName> getSNIHostNames() {
        return Collections.emptyList();
    }

    /**
     * Returns the optional explicitly configured cipher suites for this configuration. These options are used in the
     * configuration of {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on the
     * context in which they are applied.
     * <p/>
     * These values override any filters supplied in {@link #setCipherSuitesFilter(FilterParameters)}
     */
    public CipherSuitesParameters getCipherSuites() {
        return cipherSuites;
    }

    /**
     * Sets the optional explicitly configured cipher suites for this configuration. These options are used in the
     * configuration of {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on the
     * context in which they are applied.
     * <p/>
     * These values override any filters supplied in {@link #setCipherSuitesFilter(FilterParameters)}
     *
     * @param cipherSuites the suite configuration
     */
    public void setCipherSuites(CipherSuitesParameters cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    /**
     * Returns the optional cipher suite filter for this configuration. These options are used in the configuration of
     * {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on the context in which
     * they are applied.
     * <p/>
     * These values are ignored if {@link #setCipherSuites(CipherSuitesParameters)} is called with a non {@code null}
     * argument.
     */
    public FilterParameters getCipherSuitesFilter() {
        return cipherSuitesFilter;
    }

    /**
     * Sets the optional cipher suite filter for this JSSE configuration. These options are used in the configuration of
     * {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on the context in which
     * they are applied.
     * <p/>
     * These values are ignored if {@link #setCipherSuites(CipherSuitesParameters)} is called with a non {@code null}
     * argument.
     *
     * @param cipherSuitesFilter the filter configuration
     */
    public void setCipherSuitesFilter(FilterParameters cipherSuitesFilter) {
        this.cipherSuitesFilter = cipherSuitesFilter;
    }

    /**
     * Returns the explicitly configured secure socket protocol names for this configuration. These options are used in
     * the configuration of {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on
     * the context in which they are applied.
     * <p/>
     * These values override any filters supplied in {@link #setSecureSocketProtocolsFilter(FilterParameters)}
     */
    public SecureSocketProtocolsParameters getSecureSocketProtocols() {
        return secureSocketProtocols;
    }

    /**
     * Sets the explicitly configured secure socket protocol names for this configuration. These options are used in the
     * configuration of {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on the
     * context in which they are applied.
     * <p/>
     * These values override any filters supplied in {@link #setSecureSocketProtocolsFilter(FilterParameters)}
     */
    public void setSecureSocketProtocols(SecureSocketProtocolsParameters secureSocketProtocols) {
        this.secureSocketProtocols = secureSocketProtocols;
    }

    /**
     * Returns the optional secure socket protocol filter for this configuration. These options are used in the
     * configuration of {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on the
     * context in which they are applied.
     * <p/>
     * These values are ignored if {@link #setSecureSocketProtocols(SecureSocketProtocolsParameters)} is called with a
     * non-{@code null} argument.
     */
    public FilterParameters getSecureSocketProtocolsFilter() {
        return secureSocketProtocolsFilter;
    }

    /**
     * Sets the optional secure socket protocol filter for this JSSE configuration. These options are used in the
     * configuration of {@link SSLEngine}, {@link SSLSocketFactory} and {@link SSLServerSocketFactory} depending on the
     * context in which they are applied.
     * <p/>
     * These values are ignored if {@link #setSecureSocketProtocols(SecureSocketProtocolsParameters)} is called with a
     * non-{@code null} argument.
     *
     * @param secureSocketProtocolsFilter the filter configuration
     */
    public void setSecureSocketProtocolsFilter(FilterParameters secureSocketProtocolsFilter) {
        this.secureSocketProtocolsFilter = secureSocketProtocolsFilter;
    }

    /**
     * Returns the optional {@link SSLSessionContext} timeout time for {@link javax.net.ssl.SSLSession}s in seconds.
     */
    public String getSessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Sets the optional {@link SSLSessionContext} timeout time for {@link javax.net.ssl.SSLSession}s in seconds.
     *
     * @param sessionTimeout the timeout value or {@code null} to use the default
     */
    public void setSessionTimeout(String sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    /**
     * Returns a flag indicating if default values should be applied in the event that no other property of the instance
     * configures a particular aspect of the entity produced by the instance. This flag is used to allow instances of
     * this class to produce a configurer that simply passes through the current configuration of a configured entity
     * when the instance of this class would otherwise only apply some default configuration.
     *
     * @see SSLContextClientParameters
     * @see SSLContextServerParameters
     */
    protected boolean getAllowPassthrough() {
        return false;
    }

    /**
     * Configures the actual {@link SSLContext} itself with direct setter calls. This method differs from configuration
     * options that are handled by a configurer instance in that the options are part of the context itself and are not
     * part of some factory or instance object returned by the context.
     *
     * @param  context                  the context to configure
     *
     * @throws GeneralSecurityException if there is an error configuring the context
     */
    protected void configureSSLContext(SSLContext context) throws GeneralSecurityException {
        LOG.trace("Configuring client and server side SSLContext parameters on SSLContext [{}]...", context);

        if (this.getSessionTimeout() != null) {
            LOG.debug("Configuring client and server side SSLContext session timeout on SSLContext [{}] to [{}]",
                    context, this.getSessionTimeout());
            this.configureSessionContext(context.getClientSessionContext(), this.getSessionTimeout());
            this.configureSessionContext(context.getServerSessionContext(), this.getSessionTimeout());
        }

        LOG.trace("Configured client and server side SSLContext parameters on SSLContext [{}].", context);
    }

    protected FilterParameters getDefaultCipherSuitesFilter() {
        FilterParameters filter = new FilterParameters();

        filter.getInclude().addAll(DEFAULT_CIPHER_SUITES_FILTER_INCLUDE);
        filter.getExclude().addAll(DEFAULT_CIPHER_SUITES_FILTER_EXCLUDE);

        return filter;
    }

    protected FilterParameters getDefaultSecureSocketProcotolFilter() {
        FilterParameters filter = new FilterParameters();

        filter.getInclude().addAll(DEFAULT_SECURE_SOCKET_PROTOCOLS_FILTER_INCLUDE);
        filter.getExclude().addAll(DEFAULT_SECURE_SOCKET_PROTOCOLS_FILTER_EXCLUDE);

        return filter;
    }

    /**
     * Returns the list of configurers to apply to an {@link SSLEngine} in order to fully configure it in compliance
     * with the provided configuration options. The configurers are to be applied in the order in which they appear in
     * the list.
     *
     * @param  context the context that serves as the factory for {@code SSLEngine} instances
     *
     * @return         the needed configurers
     */
    protected List<Configurer<SSLEngine>> getSSLEngineConfigurers(SSLContext context) {

        final List<String> enabledCipherSuites = this.getCipherSuites() == null
                ? null : this.parsePropertyValues(this.getCipherSuites().getCipherSuite());

        final Patterns enabledCipherSuitePatterns;
        final Patterns defaultEnabledCipherSuitePatterns = this.getDefaultCipherSuitesFilter().getPatterns();

        if (this.getCipherSuitesFilter() != null) {
            enabledCipherSuitePatterns = this.getCipherSuitesFilter().getPatterns();
        } else {
            enabledCipherSuitePatterns = null;
        }

        ///

        final List<String> enabledSecureSocketProtocols = this.getSecureSocketProtocols() == null
                ? null : this.parsePropertyValues(this.getSecureSocketProtocols().getSecureSocketProtocol());

        final Patterns enabledSecureSocketProtocolsPatterns;
        final Patterns defaultEnabledSecureSocketProtocolsPatterns = this.getDefaultSecureSocketProcotolFilter().getPatterns();

        if (this.getSecureSocketProtocolsFilter() != null) {
            enabledSecureSocketProtocolsPatterns = this.getSecureSocketProtocolsFilter().getPatterns();
        } else {
            enabledSecureSocketProtocolsPatterns = null;
        }

        //

        final boolean allowPassthrough = getAllowPassthrough();

        //////

        Configurer<SSLEngine> sslEngineConfigurer = new Configurer<SSLEngine>() {

            @Override
            public SSLEngine configure(SSLEngine engine) {

                Collection<String> filteredCipherSuites = BaseSSLContextParameters.this
                        .filter(enabledCipherSuites, Arrays.asList(engine.getSSLParameters().getCipherSuites()),
                                Arrays.asList(engine.getEnabledCipherSuites()),
                                enabledCipherSuitePatterns, defaultEnabledCipherSuitePatterns,
                                !allowPassthrough);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(SSL_ENGINE_CIPHER_SUITE_LOG_MSG,
                            engine,
                            enabledCipherSuites,
                            enabledCipherSuitePatterns,
                            engine.getSSLParameters().getCipherSuites(),
                            engine.getEnabledCipherSuites(),
                            defaultEnabledCipherSuitePatterns,
                            filteredCipherSuites);
                }

                engine.setEnabledCipherSuites(filteredCipherSuites.toArray(new String[0]));

                Collection<String> filteredSecureSocketProtocols = BaseSSLContextParameters.this
                        .filter(enabledSecureSocketProtocols, Arrays.asList(engine.getSSLParameters().getProtocols()),
                                Arrays.asList(engine.getEnabledProtocols()),
                                enabledSecureSocketProtocolsPatterns, defaultEnabledSecureSocketProtocolsPatterns,
                                !allowPassthrough);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(SSL_ENGINE_PROTOCOL_LOG_MSG,
                            engine,
                            enabledSecureSocketProtocols,
                            enabledSecureSocketProtocolsPatterns,
                            engine.getSSLParameters().getProtocols(),
                            engine.getEnabledProtocols(),
                            defaultEnabledSecureSocketProtocolsPatterns,
                            filteredSecureSocketProtocols);
                }

                engine.setEnabledProtocols(
                        filteredSecureSocketProtocols.toArray(new String[0]));

                return engine;
            }
        };

        List<Configurer<SSLEngine>> sslEngineConfigurers = new LinkedList<>();
        sslEngineConfigurers.add(sslEngineConfigurer);

        return sslEngineConfigurers;
    }

    /**
     * Returns the list of configurers to apply to an {@link SSLSocketFactory} in order to fully configure it in
     * compliance with the provided configuration options. The configurers are to be applied in the order in which they
     * appear in the list.
     * <p/>
     * It is preferred to use {@link #getSSLSocketFactorySSLSocketConfigurers(SSLContext)} instead of this method as
     * {@code SSLSocketFactory} does not contain any configuration options that are non-proprietary.
     *
     * @param  context the context that serves as the factory for {@code SSLSocketFactory} instances
     *
     * @return         the needed configurers
     *
     * @see            #getSSLSocketFactorySSLSocketConfigurers(SSLContext)
     */
    protected List<Configurer<SSLSocketFactory>> getSSLSocketFactoryConfigurers(SSLContext context) {

        final List<Configurer<SSLSocket>> sslSocketConfigurers = this.getSSLSocketFactorySSLSocketConfigurers(context);

        Configurer<SSLSocketFactory> sslSocketFactoryConfigurer = new Configurer<SSLSocketFactory>() {

            @Override
            public SSLSocketFactory configure(SSLSocketFactory factory) {
                return new SSLSocketFactoryDecorator(
                        factory,
                        sslSocketConfigurers);
            }
        };

        List<Configurer<SSLSocketFactory>> sslSocketFactoryConfigurers = new LinkedList<>();
        sslSocketFactoryConfigurers.add(sslSocketFactoryConfigurer);

        return sslSocketFactoryConfigurers;
    }

    /**
     * Returns the list of configurers to apply to an {@link SSLServerSocketFactory} in order to fully configure it in
     * compliance with the provided configuration options. The configurers are to be applied in the order in which they
     * appear in the list.
     * <p/>
     * It is preferred to use {@link #getSSLServerSocketFactorySSLServerSocketConfigurers(SSLContext)} instead of this
     * method as {@code SSLServerSocketFactory} does not contain any configuration options that are non-proprietary.
     *
     * @param  context the context that serves as the factory for {@code SSLServerSocketFactory} instances
     *
     * @return         the needed configurers
     *
     * @see            #getSSLServerSocketFactorySSLServerSocketConfigurers(SSLContext)
     */
    protected List<Configurer<SSLServerSocketFactory>> getSSLServerSocketFactoryConfigurers(SSLContext context) {

        final List<Configurer<SSLServerSocket>> sslServerSocketConfigurers
                = this.getSSLServerSocketFactorySSLServerSocketConfigurers(context);

        Configurer<SSLServerSocketFactory> sslServerSocketFactoryConfigurer = new Configurer<SSLServerSocketFactory>() {

            @Override
            public SSLServerSocketFactory configure(SSLServerSocketFactory factory) {
                return new SSLServerSocketFactoryDecorator(
                        factory,
                        sslServerSocketConfigurers);
            }
        };

        List<Configurer<SSLServerSocketFactory>> sslServerSocketFactoryConfigurers = new LinkedList<>();
        sslServerSocketFactoryConfigurers.add(sslServerSocketFactoryConfigurer);

        return sslServerSocketFactoryConfigurers;
    }

    /**
     * Returns the list of configurers to apply to an {@link SSLSocket} in order to fully configure it in compliance
     * with the provided configuration options. These configurers are intended for sockets produced by a
     * {@link SSLSocketFactory}, see {@link #getSSLServerSocketFactorySSLServerSocketConfigurers(SSLContext)} for
     * configurers related to sockets produced by a {@link SSLServerSocketFactory}. The configurers are to be applied in
     * the order in which they appear in the list.
     *
     * @param  context the context that serves as the factory for {@code SSLSocketFactory} instances
     *
     * @return         the needed configurers
     */
    protected List<Configurer<SSLSocket>> getSSLSocketFactorySSLSocketConfigurers(SSLContext context) {
        final List<String> enabledCipherSuites = this.getCipherSuites() == null
                ? null : this.parsePropertyValues(this.getCipherSuites().getCipherSuite());

        final Patterns enabledCipherSuitePatterns;
        final Patterns defaultEnabledCipherSuitePatterns = this.getDefaultCipherSuitesFilter().getPatterns();

        if (this.getCipherSuitesFilter() != null) {
            enabledCipherSuitePatterns = this.getCipherSuitesFilter().getPatterns();
        } else {
            enabledCipherSuitePatterns = null;
        }

        ///

        final List<String> enabledSecureSocketProtocols = this.getSecureSocketProtocols() == null
                ? null : this.parsePropertyValues(this.getSecureSocketProtocols().getSecureSocketProtocol());

        final Patterns enabledSecureSocketProtocolsPatterns;
        final Patterns defaultEnabledSecureSocketProtocolsPatterns = this.getDefaultSecureSocketProcotolFilter().getPatterns();

        if (this.getSecureSocketProtocolsFilter() != null) {
            enabledSecureSocketProtocolsPatterns = this.getSecureSocketProtocolsFilter().getPatterns();
        } else {
            enabledSecureSocketProtocolsPatterns = null;
        }

        //

        final boolean allowPassthrough = getAllowPassthrough();

        //////

        Configurer<SSLSocket> sslSocketConfigurer = new Configurer<SSLSocket>() {

            @Override
            public SSLSocket configure(SSLSocket socket) {

                if (!getSNIHostNames().isEmpty()) {
                    SSLParameters sslParameters = socket.getSSLParameters();
                    sslParameters.setServerNames(getSNIHostNames());
                    socket.setSSLParameters(sslParameters);
                }

                Collection<String> filteredCipherSuites = BaseSSLContextParameters.this
                        .filter(enabledCipherSuites, Arrays.asList(socket.getSSLParameters().getCipherSuites()),
                                Arrays.asList(socket.getEnabledCipherSuites()),
                                enabledCipherSuitePatterns, defaultEnabledCipherSuitePatterns,
                                !allowPassthrough);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(SSL_SOCKET_CIPHER_SUITE_LOG_MSG,
                            socket,
                            enabledCipherSuites,
                            enabledCipherSuitePatterns,
                            socket.getSSLParameters().getCipherSuites(),
                            socket.getEnabledCipherSuites(),
                            defaultEnabledCipherSuitePatterns,
                            filteredCipherSuites);
                }

                socket.setEnabledCipherSuites(filteredCipherSuites.toArray(new String[0]));

                Collection<String> filteredSecureSocketProtocols = BaseSSLContextParameters.this
                        .filter(enabledSecureSocketProtocols, Arrays.asList(socket.getSSLParameters().getProtocols()),
                                Arrays.asList(socket.getEnabledProtocols()),
                                enabledSecureSocketProtocolsPatterns, defaultEnabledSecureSocketProtocolsPatterns,
                                !allowPassthrough);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(SSL_SOCKET_PROTOCOL_LOG_MSG,
                            socket,
                            enabledSecureSocketProtocols,
                            enabledSecureSocketProtocolsPatterns,
                            socket.getSSLParameters().getProtocols(),
                            socket.getEnabledProtocols(),
                            defaultEnabledSecureSocketProtocolsPatterns,
                            filteredSecureSocketProtocols);
                }

                socket.setEnabledProtocols(
                        filteredSecureSocketProtocols.toArray(new String[0]));
                return socket;
            }
        };

        List<Configurer<SSLSocket>> sslSocketConfigurers = new LinkedList<>();
        sslSocketConfigurers.add(sslSocketConfigurer);

        return sslSocketConfigurers;
    }

    /**
     * Returns the list of configurers to apply to an {@link SSLServerSocket} in order to fully configure it in
     * compliance with the provided configuration options. These configurers are intended for sockets produced by a
     * {@link SSLServerSocketFactory}, see {@link #getSSLSocketFactorySSLSocketConfigurers(SSLContext)} for configurers
     * related to sockets produced by a {@link SSLSocketFactory}. The configurers are to be applied in the order in
     * which they appear in the list.
     *
     * @param  context the context that serves as the factory for {@code SSLServerSocketFactory} instances
     * @return         the needed configurers
     */
    protected List<Configurer<SSLServerSocket>> getSSLServerSocketFactorySSLServerSocketConfigurers(SSLContext context) {
        final List<String> enabledCipherSuites = this.getCipherSuites() == null
                ? null : this.parsePropertyValues(this.getCipherSuites().getCipherSuite());

        final Patterns enabledCipherSuitePatterns;
        final Patterns defaultEnabledCipherSuitePatterns = this.getDefaultCipherSuitesFilter().getPatterns();

        if (this.getCipherSuitesFilter() != null) {
            enabledCipherSuitePatterns = this.getCipherSuitesFilter().getPatterns();
        } else {
            enabledCipherSuitePatterns = null;
        }

        ///

        final List<String> enabledSecureSocketProtocols = this.getSecureSocketProtocols() == null
                ? null : this.parsePropertyValues(this.getSecureSocketProtocols().getSecureSocketProtocol());

        final Patterns enabledSecureSocketProtocolsPatterns;
        final Patterns defaultEnabledSecureSocketProtocolsPatterns = this.getDefaultSecureSocketProcotolFilter().getPatterns();

        if (this.getSecureSocketProtocolsFilter() != null) {
            enabledSecureSocketProtocolsPatterns = this.getSecureSocketProtocolsFilter().getPatterns();
        } else {
            enabledSecureSocketProtocolsPatterns = null;
        }

        //

        final boolean allowPassthrough = getAllowPassthrough();

        //////

        Configurer<SSLServerSocket> sslServerSocketConfigurer = new Configurer<SSLServerSocket>() {

            @Override
            public SSLServerSocket configure(SSLServerSocket socket) {

                Collection<String> filteredCipherSuites = BaseSSLContextParameters.this
                        .filter(enabledCipherSuites, Arrays.asList(socket.getSupportedCipherSuites()),
                                Arrays.asList(socket.getEnabledCipherSuites()),
                                enabledCipherSuitePatterns, defaultEnabledCipherSuitePatterns,
                                !allowPassthrough);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(SSL_SERVER_SOCKET_CIPHER_SUITE_LOG_MSG,
                            socket,
                            enabledCipherSuites,
                            enabledCipherSuitePatterns,
                            socket.getSupportedCipherSuites(),
                            socket.getEnabledCipherSuites(),
                            defaultEnabledCipherSuitePatterns,
                            filteredCipherSuites);
                }

                socket.setEnabledCipherSuites(filteredCipherSuites.toArray(new String[0]));

                Collection<String> filteredSecureSocketProtocols = BaseSSLContextParameters.this
                        .filter(enabledSecureSocketProtocols, Arrays.asList(socket.getSupportedProtocols()),
                                Arrays.asList(socket.getEnabledProtocols()),
                                enabledSecureSocketProtocolsPatterns, defaultEnabledSecureSocketProtocolsPatterns,
                                !allowPassthrough);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(SSL_SERVER_SOCKET_PROTOCOL_LOG_MSG,
                            socket,
                            enabledSecureSocketProtocols,
                            enabledSecureSocketProtocolsPatterns,
                            socket.getSupportedProtocols(),
                            socket.getEnabledProtocols(),
                            defaultEnabledSecureSocketProtocolsPatterns,
                            filteredSecureSocketProtocols);
                }

                socket.setEnabledProtocols(
                        filteredSecureSocketProtocols.toArray(new String[0]));
                return socket;
            }
        };

        List<Configurer<SSLServerSocket>> sslServerSocketConfigurers = new LinkedList<>();
        sslServerSocketConfigurers.add(sslServerSocketConfigurer);

        return sslServerSocketConfigurers;
    }

    /**
     * Configures a {@link SSLSessionContext}, client or server, with the supplied session timeout.
     *
     * @param  sessionContext           the context to configure
     * @param  sessionTimeout           the timeout time period
     * @throws GeneralSecurityException if {@code sessionContext} is {@code null}
     */
    protected void configureSessionContext(
            SSLSessionContext sessionContext, String sessionTimeout)
            throws GeneralSecurityException {

        int sessionTimeoutInt = Integer.parseInt(this.parsePropertyValue(sessionTimeout));

        if (sessionContext != null) {
            sessionContext.setSessionTimeout(sessionTimeoutInt);
        } else {
            throw new GeneralSecurityException(
                    "The SSLContext does not support SSLSessionContext, "
                                               + "but a session timeout is configured. Set sessionTimeout to null "
                                               + "to avoid this error.");
        }
    }

    /**
     * Filters the values in {@code availableValues} returning only the values that are explicitly listed in
     * {@code explicitValues} (returns them regardless of if they appear in {@code availableValues} or not) if
     * {@code explicitValues} is not {@code null} or according to the following rules:
     * <ol>
     * <li>Match the include patterns in {@code patterns} and don't match the exclude patterns in {@code patterns} if
     * patterns is not {@code null}.</li>
     * <li>Match the include patterns in {@code defaultPatterns} and don't match the exclude patterns in
     * {@code defaultPatterns} if patterns is {@code null} and {@code applyDefaults} is true.</li>
     * <li>Are provided in currentValues if if patterns is {@code null} and {@code applyDefaults} is false.</li>
     * </ol>
     *
     * @param  explicitValues  the optional explicit values to use
     * @param  availableValues the available values to filter from
     * @param  patterns        the optional patterns to use when {@code explicitValues} is not used
     * @param  defaultPatterns the required patterns to use when {@code explicitValues} and {@code patterns} are not
     *                         used
     * @param  applyDefaults   flag indicating whether or not to apply defaults in the event that no explicit values and
     *                         no patterns apply
     *
     * @return                 the filtered values
     *
     * @see                    #filter(Collection, Collection, List, List)
     */
    protected Collection<String> filter(
            Collection<String> explicitValues, Collection<String> availableValues,
            Collection<String> currentValues, Patterns patterns, Patterns defaultPatterns,
            boolean applyDefaults) {

        final List<Pattern> enabledIncludePatterns;
        final List<Pattern> enabledExcludePatterns;

        if (explicitValues == null && patterns == null && !applyDefaults) {
            return currentValues;
        }

        if (patterns != null) {
            enabledIncludePatterns = patterns.getIncludes();
            enabledExcludePatterns = patterns.getExcludes();
        } else {
            enabledIncludePatterns = defaultPatterns.getIncludes();
            enabledExcludePatterns = defaultPatterns.getExcludes();
        }

        return this.filter(
                explicitValues,
                availableValues,
                enabledIncludePatterns, enabledExcludePatterns);
    }

    /**
     * Filters the values in {@code availableValues} returning only the values that are explicitly listed in
     * {@code explicitValues} (returns them regardless of if they appear in {@code availableValues} or not) if
     * {@code explicitValues} is not {@code null} or as match the patterns in {@code includePatterns} and do not match
     * the patterns in {@code excludePatterns} if {@code explicitValues} is {@code null}.
     *
     * @param  explicitValues  the optional explicit values to use
     * @param  availableValues the available values to filter from if {@code explicitValues} is {@code null}
     * @param  includePatterns the patterns to use for inclusion filtering, required if {@code explicitValues} is
     *                         {@code null}
     * @param  excludePatterns the patterns to use for exclusion filtering, required if {@code explicitValues} is
     *                         {@code null}
     *
     * @return                 the filtered values
     */
    protected Collection<String> filter(
            Collection<String> explicitValues, Collection<String> availableValues,
            List<Pattern> includePatterns, List<Pattern> excludePatterns) {
        Collection<String> returnValues;

        // Explicit list has precedence over filters, even when the list is
        // empty.
        if (explicitValues != null) {
            returnValues = new ArrayList<>(explicitValues);
        } else {
            returnValues = new LinkedList<>();

            for (String value : availableValues) {
                if (this.matchesOneOf(value, includePatterns)
                        && !this.matchesOneOf(value, excludePatterns)) {
                    returnValues.add(value);
                }
            }
        }

        return returnValues;
    }

    /**
     * Returns true if and only if the value is matched by one or more of the supplied patterns.
     *
     * @param value    the value to match
     * @param patterns the patterns to try to match against
     */
    protected boolean matchesOneOf(String value, List<Pattern> patterns) {
        boolean matches = false;

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.matches()) {
                matches = true;
                break;
            }
        }

        return matches;
    }

    /**
     * Configures a {@code T} based on the related configuration options.
     */
    interface Configurer<T> {

        /**
         * Configures a {@code T} based on the related configuration options. The return value from this method may be
         * {@code object} or it may be a decorated instance there of. Consequently, any subsequent actions on
         * {@code object} must be performed using the returned value.
         *
         * @param  object the object to configure
         * @return        {@code object} or a decorated instance there of
         */
        T configure(T object);
    }

    /**
     * Makes a decorated {@link SSLContext} appear as a normal {@code SSLContext}.
     */
    protected static final class SSLContextDecorator extends SSLContext {

        public SSLContextDecorator(SSLContextSpiDecorator decorator) {
            super(decorator, decorator.getDelegate().getProvider(), decorator.getDelegate().getProtocol());
            LOG.debug("SSLContextDecorator [{}] decorating SSLContext [{}].", this, decorator.getDelegate());
        }

        @Override
        public String toString() {
            return String.format("SSLContext[hash=%h, provider=%s, protocol=%s, needClientAuth=%s, "
                                 + "wantClientAuth=%s%n\tdefaultProtocols=%s%n\tdefaultCipherSuites=%s%n\tsupportedProtocols=%s%n\tsupportedCipherSuites=%s%n]",
                    hashCode(), getProvider(), getProtocol(), getDefaultSSLParameters().getNeedClientAuth(),
                    getDefaultSSLParameters().getWantClientAuth(),
                    collectionAsCommaDelimitedString(getDefaultSSLParameters().getProtocols()),
                    collectionAsCommaDelimitedString(getDefaultSSLParameters().getCipherSuites()),
                    collectionAsCommaDelimitedString(getSupportedSSLParameters().getProtocols()),
                    collectionAsCommaDelimitedString(getSupportedSSLParameters().getCipherSuites()));
        }
    }

    /**
     * Class needed to provide decoration of an existing {@link SSLContext}. Since {@code SSLContext} is an abstract
     * class and requires an instance of {@link SSLContextSpi}, this class effectively wraps an {@code SSLContext} as if
     * it were an {@code SSLContextSpi}, allowing us to achieve decoration.
     */
    protected static final class SSLContextSpiDecorator extends SSLContextSpi {

        private final SSLContext context;

        private final List<Configurer<SSLEngine>> sslEngineConfigurers;

        private final List<Configurer<SSLSocketFactory>> sslSocketFactoryConfigurers;

        private final List<Configurer<SSLServerSocketFactory>> sslServerSocketFactoryConfigurers;

        public SSLContextSpiDecorator(SSLContext context,
                                      List<Configurer<SSLEngine>> sslEngineConfigurers,
                                      List<Configurer<SSLSocketFactory>> sslSocketFactoryConfigurers,
                                      List<Configurer<SSLServerSocketFactory>> sslServerSocketFactoryConfigurers) {
            this.context = context;
            this.sslEngineConfigurers = sslEngineConfigurers;
            this.sslSocketFactoryConfigurers = sslSocketFactoryConfigurers;
            this.sslServerSocketFactoryConfigurers = sslServerSocketFactoryConfigurers;
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            SSLEngine engine = this.context.createSSLEngine();
            LOG.debug("SSLEngine [{}] created from SSLContext [{}].", engine, context);
            this.configureSSLEngine(engine);
            return engine;
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String peerHost, int peerPort) {
            SSLEngine engine = this.context.createSSLEngine(peerHost, peerPort);
            LOG.debug("SSLEngine [{}] created from SSLContext [{}].", engine, context);
            return this.configureSSLEngine(engine);
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            return this.context.getClientSessionContext();
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            return this.context.getServerSessionContext();
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            SSLServerSocketFactory factory = this.context.getServerSocketFactory();
            LOG.debug("SSLServerSocketFactoryEngine [{}] created from SSLContext [{}].", factory, context);
            return this.configureSSLServerSocketFactory(factory);
        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            SSLSocketFactory factory = this.context.getSocketFactory();
            LOG.debug("SSLSocketFactory [{}] created from SSLContext [{}].", factory, context);
            return this.configureSSLSocketFactory(factory);
        }

        @Override
        protected void engineInit(
                KeyManager[] km,
                TrustManager[] tm,
                SecureRandom random)
                throws KeyManagementException {
            this.context.init(km, tm, random);
        }

        protected SSLContext getDelegate() {
            return this.context;
        }

        /**
         * Configures an {@link SSLEngine} based on the configurers in instance. The return value from this method may
         * be {@code engine} or it may be a decorated instance there of. Consequently, any subsequent actions on
         * {@code engine} must be performed using the returned value.
         *
         * @param  engine the engine to configure
         * @return        {@code engine} or a decorated instance there of
         */
        protected SSLEngine configureSSLEngine(SSLEngine engine) {
            SSLEngine workingEngine = engine;

            for (Configurer<SSLEngine> configurer : this.sslEngineConfigurers) {
                workingEngine = configurer.configure(workingEngine);
            }

            return workingEngine;
        }

        /**
         * Configures an {@link SSLSocketFactory} based on the configurers in this instance. The return value from this
         * method may be {@code factory} or it may be a decorated instance there of. Consequently, any subsequent
         * actions on {@code factory} must be performed using the returned value.
         *
         * @param  factory the factory to configure
         * @return         {@code factory} or a decorated instance there of
         */
        protected SSLSocketFactory configureSSLSocketFactory(SSLSocketFactory factory) {
            SSLSocketFactory workingFactory = factory;

            for (Configurer<SSLSocketFactory> configurer : this.sslSocketFactoryConfigurers) {
                workingFactory = configurer.configure(workingFactory);
            }

            return workingFactory;
        }

        /**
         * Configures an {@link SSLServerSocketFactory} based on the configurers in this instance. The return value from
         * this method may be {@code factory} or it may be a decorated instance there of. Consequently, any subsequent
         * actions on {@code factory} must be performed using the returned value.
         *
         * @param  factory the factory to configure
         * @return         {@code factory} or a decorated instance there of
         */
        protected SSLServerSocketFactory configureSSLServerSocketFactory(
                SSLServerSocketFactory factory) {
            SSLServerSocketFactory workingFactory = factory;

            for (Configurer<SSLServerSocketFactory> configurer : this.sslServerSocketFactoryConfigurers) {
                workingFactory = configurer.configure(workingFactory);
            }

            return workingFactory;
        }
    }

    /**
     * A decorator that enables the application of configuration options to be applied to created sockets even after
     * this factory has been created and turned over to client code.
     */
    protected static final class SSLServerSocketFactoryDecorator extends SSLServerSocketFactory {

        private final SSLServerSocketFactory sslServerSocketFactory;
        private final List<Configurer<SSLServerSocket>> sslServerSocketConfigurers;

        public SSLServerSocketFactoryDecorator(SSLServerSocketFactory sslServerSocketFactory,
                                               List<Configurer<SSLServerSocket>> sslServerSocketConfigurers) {
            this.sslServerSocketFactory = sslServerSocketFactory;
            this.sslServerSocketConfigurers = sslServerSocketConfigurers;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return this.sslServerSocketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return this.sslServerSocketFactory.getSupportedCipherSuites();
        }

        @Override
        public ServerSocket createServerSocket() throws IOException {
            return this.configureSocket(this.sslServerSocketFactory.createServerSocket());
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
            return this.configureSocket(this.sslServerSocketFactory.createServerSocket(port, backlog, ifAddress));
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
            return this.configureSocket(this.sslServerSocketFactory.createServerSocket(port, backlog));
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            return this.configureSocket(this.sslServerSocketFactory.createServerSocket(port));
        }

        public SSLServerSocketFactory getDelegate() {
            return this.sslServerSocketFactory;
        }

        private ServerSocket configureSocket(ServerSocket s) {
            SSLServerSocket workingSocket = (SSLServerSocket) s;

            LOG.debug("Created ServerSocket [{}] from SslServerSocketFactory [{}].", s, sslServerSocketFactory);

            for (Configurer<SSLServerSocket> configurer : this.sslServerSocketConfigurers) {
                workingSocket = configurer.configure(workingSocket);
            }

            return workingSocket;
        }
    }

    /**
     * A decorator that enables the application of configuration options to be applied to created sockets even after
     * this factory has been created and turned over to client code.
     */
    protected static final class SSLSocketFactoryDecorator extends SSLSocketFactory {

        private final SSLSocketFactory sslSocketFactory;
        private final List<Configurer<SSLSocket>> sslSocketConfigurers;

        public SSLSocketFactoryDecorator(SSLSocketFactory sslSocketFactory,
                                         List<Configurer<SSLSocket>> sslSocketConfigurers) {
            this.sslSocketFactory = sslSocketFactory;
            this.sslSocketConfigurers = sslSocketConfigurers;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return sslSocketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return sslSocketFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return configureSocket(sslSocketFactory.createSocket());
        }

        @Override
        public Socket createSocket(
                Socket s, String host,
                int port, boolean autoClose)
                throws IOException {
            return configureSocket(sslSocketFactory.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configureSocket(sslSocketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(
                String host, int port,
                InetAddress localHost, int localPort)
                throws IOException {
            return configureSocket(sslSocketFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configureSocket(sslSocketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(
                InetAddress address, int port,
                InetAddress localAddress, int localPort)
                throws IOException {
            return configureSocket(sslSocketFactory.createSocket(address, port, localAddress, localPort));
        }

        public SSLSocketFactory getDelegate() {
            return this.sslSocketFactory;
        }

        private Socket configureSocket(Socket s) {
            SSLSocket workingSocket = (SSLSocket) s;

            LOG.debug("Created Socket [{}] from SocketFactory [{}].", s, sslSocketFactory);

            for (Configurer<SSLSocket> configurer : this.sslSocketConfigurers) {
                workingSocket = configurer.configure(workingSocket);
            }

            return workingSocket;
        }
    }

    private static String collectionAsCommaDelimitedString(String[] col) {
        return col == null || col.length == 0 ? "" : String.join(",", col);
    }

    private static String createCipherSuiteLogMessage(String entityName) {
        return "Configuring " + entityName + " [{}] with " + LS
               + "\t explicitly set cipher suites [{}]," + LS
               + "\t cipher suite patterns [{}]," + LS
               + "\t available cipher suites [{}]," + LS
               + "\t currently enabled cipher suites [{}]," + LS
               + "\t and default cipher suite patterns [{}]." + LS
               + "\t Resulting enabled cipher suites are [{}].";
    }

    private static String createProtocolLogMessage(String entityName) {
        return "Configuring " + entityName + " [{}] with " + LS
               + "\t explicitly set protocols [{}]," + LS
               + "\t protocol patterns [{}]," + LS
               + "\t available protocols [{}]," + LS
               + "\t currently enabled protocols [{}]," + LS
               + "\t and default protocol patterns [{}]." + LS
               + "\t Resulting enabled protocols are [{}].";
    }
}
