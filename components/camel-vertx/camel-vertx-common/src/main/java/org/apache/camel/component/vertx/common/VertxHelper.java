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
package org.apache.camel.component.vertx.common;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.JsseParametersHelper;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VertxHelper {

    private static final Logger LOG = LoggerFactory.getLogger(VertxHelper.class);

    private VertxHelper() {
        // Utility class
    }

    /**
     * Configures key store and trust store options, the client authentication (of a server), the cipher suites, the
     * protocols and the named groups (key exchange groups) for the given TCPSSLOptions from the configuration specified
     * on SSLContextParameters. The named groups include the post-quantum preference that
     * {@link SSLContextParameters#createSSLContext(CamelContext)} applies when none are configured. The signature
     * schemes cannot be applied, as Vert.x has no such setting, and a WARN says so when they are configured.
     *
     * @param camelContext         the CamelContext
     * @param sslContextParameters the SSL configuration to use for the KeyManagerFactory & TrustManagerFactory
     * @param tcpsslOptions        the TCPSSLOptions instance to configure
     */
    public static void setupSSLOptions(
            CamelContext camelContext, SSLContextParameters sslContextParameters, TCPSSLOptions tcpsslOptions)
            throws Exception {

        if (camelContext == null) {
            throw new IllegalArgumentException("camelContext cannot be null");
        }

        if (sslContextParameters == null) {
            throw new IllegalArgumentException("sslContextParameters cannot be null");
        }

        if (tcpsslOptions == null) {
            throw new IllegalArgumentException("tcpsslOptions cannot be null");
        }

        tcpsslOptions.setSsl(true);

        KeyManager keyManager = sslContextParameters.getKeyManagers() != null
                ? sslContextParameters.getKeyManagers().getKeyManager() : null;
        if (keyManager instanceof X509KeyManager x509KeyManager) {
            tcpsslOptions.setKeyCertOptions(KeyCertOptions.wrap(x509KeyManager));
        } else if (keyManager != null) {
            throw new IllegalArgumentException(
                    "Vert.x requires an X509KeyManager, but the configured key manager is a "
                                               + keyManager.getClass().getName());
        } else {
            KeyManagerFactory keyManagerFactory = createKeyManagerFactory(camelContext, sslContextParameters);
            if (keyManagerFactory != null) {
                tcpsslOptions.setKeyCertOptions(new KeyManagerFactoryOptions(keyManagerFactory));
            }
        }

        TrustManager trustManager = sslContextParameters.getTrustManagers() != null
                ? sslContextParameters.getTrustManagers().getTrustManager() : null;
        if (trustManager != null) {
            tcpsslOptions.setTrustOptions(TrustOptions.wrap(trustManager));
        } else {
            TrustManagerFactory trustManagerFactory = createTrustManagerFactory(camelContext, sslContextParameters);
            if (trustManagerFactory != null) {
                tcpsslOptions.setTrustOptions(new TrustManagerFactoryOptions(trustManagerFactory));
            }
        }

        // client authentication of a server
        if (tcpsslOptions instanceof NetServerOptions serverOptions
                && sslContextParameters.getServerParameters() != null
                && sslContextParameters.getServerParameters().getClientAuthentication() != null) {
            String value = camelContext.resolvePropertyPlaceholders(
                    sslContextParameters.getServerParameters().getClientAuthentication());
            ClientAuthentication clientAuthentication = ClientAuthentication.valueOf(value.toUpperCase(Locale.ENGLISH));
            serverOptions.setClientAuth(switch (clientAuthentication) {
                case REQUIRE -> ClientAuth.REQUIRED;
                case WANT -> ClientAuth.REQUEST;
                case NONE -> ClientAuth.NONE;
            });
        }

        // the cipher suites, protocols and named groups (when configured) as computed by the SSLContextParameters
        // (such as from the include and exclude filters)
        boolean cipherSuites = sslContextParameters.getCipherSuites() != null
                || sslContextParameters.getCipherSuitesFilter() != null;
        boolean protocols = sslContextParameters.getSecureSocketProtocols() != null
                || sslContextParameters.getSecureSocketProtocolsFilter() != null;
        boolean namedGroups = sslContextParameters.getNamedGroups() != null
                || sslContextParameters.getNamedGroupsFilter() != null;
        // the post-quantum preference that the SSLContext applies when no named groups are configured, which a Vert.x
        // engine of the JDK can take as well
        boolean postQuantum = !namedGroups && isJdkEngine(tcpsslOptions.getSslEngineOptions())
                && JsseParametersHelper.postQuantumNamedGroups(sslContextParameters) != null;
        if (cipherSuites || protocols || namedGroups || postQuantum) {
            SSLEngine engine = sslContextParameters.createSSLContext(camelContext).createSSLEngine();
            if (cipherSuites) {
                for (String suite : engine.getEnabledCipherSuites()) {
                    tcpsslOptions.addEnabledCipherSuite(suite);
                }
            }
            if (protocols) {
                tcpsslOptions.setEnabledSecureTransportProtocols(new LinkedHashSet<>(List.of(engine.getEnabledProtocols())));
            }
            String[] groups = engine.getSSLParameters().getNamedGroups();
            if ((namedGroups || postQuantum) && groups != null) {
                // Vert.x applies its key exchange groups on each SSLEngine it creates, server and client side
                sslOptions(tcpsslOptions).setKeyExchangeGroups(new ArrayList<>(List.of(groups)));
            }
        }

        // Vert.x has no setting for the signature schemes
        if (sslContextParameters.getSignatureSchemes() != null || sslContextParameters.getSignatureSchemesFilter() != null) {
            LOG.warn("Vert.x cannot apply the signature schemes of sslContextParameters, as its TLS options have no such"
                     + " setting: the signature schemes of the JVM are used instead");
        }
    }

    /**
     * The SSL options of the given TCP SSL options, which Vert.x creates on the first SSL setting.
     */
    private static SSLOptions sslOptions(TCPSSLOptions tcpsslOptions) {
        SSLOptions answer = tcpsslOptions.getSslOptions();
        if (answer == null) {
            // TCPSSLOptions.getOrCreateSSLOptions() is protected, but each SSL setter calls it, so set the value the
            // options already have (VertxHelperNamedGroupsTest fails if a Vert.x upgrade changes this)
            tcpsslOptions.setSslHandshakeTimeout(tcpsslOptions.getSslHandshakeTimeout());
            answer = tcpsslOptions.getSslOptions();
        }
        return answer;
    }

    /**
     * Whether Vert.x uses the SSLEngine of the JDK, which knows the named groups of the JVM, rather than OpenSSL.
     */
    private static boolean isJdkEngine(SSLEngineOptions engineOptions) {
        return engineOptions == null || engineOptions instanceof JdkSSLEngineOptions;
    }

    private static KeyManagerFactory createKeyManagerFactory(
            CamelContext camelContext, SSLContextParameters sslContextParameters)
            throws Exception {
        final KeyManagersParameters keyManagers = sslContextParameters.getKeyManagers();
        if (keyManagers == null) {
            return null;
        }
        keyManagers.setCamelContext(camelContext);
        if (keyManagers.getKeyStore() != null) {
            keyManagers.getKeyStore().setCamelContext(camelContext);
        }

        String kmfAlgorithm = camelContext.resolvePropertyPlaceholders(keyManagers.getAlgorithm());
        if (kmfAlgorithm == null) {
            kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        }

        KeyManagerFactory kmf;
        if (keyManagers.getProvider() == null) {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
        } else {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm,
                    camelContext.resolvePropertyPlaceholders(keyManagers.getProvider()));
        }

        char[] kmfPassword = null;
        if (keyManagers.getKeyPassword() != null) {
            kmfPassword = camelContext.resolvePropertyPlaceholders(keyManagers.getKeyPassword()).toCharArray();
        }

        KeyStore ks = keyManagers.getKeyStore() == null ? null : keyManagers.getKeyStore().createKeyStore();

        kmf.init(ks, kmfPassword);
        return kmf;
    }

    private static TrustManagerFactory createTrustManagerFactory(
            CamelContext camelContext, SSLContextParameters sslContextParameters)
            throws Exception {
        final TrustManagersParameters trustManagers = sslContextParameters.getTrustManagers();
        if (trustManagers == null) {
            return null;
        }
        trustManagers.setCamelContext(camelContext);
        if (trustManagers.getKeyStore() != null) {
            trustManagers.getKeyStore().setCamelContext(camelContext);
        }

        TrustManagerFactory tmf = null;

        if (trustManagers.getKeyStore() != null) {
            String tmfAlgorithm = camelContext.resolvePropertyPlaceholders(trustManagers.getAlgorithm());
            if (tmfAlgorithm == null) {
                tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            }

            if (trustManagers.getProvider() == null) {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            } else {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm,
                        camelContext.resolvePropertyPlaceholders(trustManagers.getProvider()));
            }

            KeyStore ks = trustManagers.getKeyStore() == null ? null : trustManagers.getKeyStore().createKeyStore();
            tmf.init(ks);
        }
        return tmf;
    }
}
