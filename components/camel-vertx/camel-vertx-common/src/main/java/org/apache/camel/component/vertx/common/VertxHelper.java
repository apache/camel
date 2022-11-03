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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.vertx.core.net.TCPSSLOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

public final class VertxHelper {

    private VertxHelper() {
        // Utility class
    }

    /**
     * Configures key store and trust store options for the given TCPSSLOptions from the configuration specified on
     * SSLContextParameters
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

        KeyManagerFactory keyManagerFactory = createKeyManagerFactory(camelContext, sslContextParameters);
        tcpsslOptions.setKeyCertOptions(new KeyManagerFactoryOptions(keyManagerFactory));

        TrustManagerFactory trustManagerFactory = createTrustManagerFactory(camelContext, sslContextParameters);
        tcpsslOptions.setTrustOptions(new TrustManagerFactoryOptions(trustManagerFactory));
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
