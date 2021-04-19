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
package org.apache.camel.component.vertx.websocket;

import java.security.KeyStore;
import java.util.function.Function;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import io.vertx.core.Vertx;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

public final class VertxWebsocketHelper {

    private VertxWebsocketHelper() {
        // Utility class
    }

    /**
     * Configures key store and trust store options for the Vert.x client and server
     */
    public static void setupSSLOptions(SSLContextParameters sslContextParameters, TCPSSLOptions options) throws Exception {
        options.setSsl(true);
        options.setKeyCertOptions(new KeyManagerFactoryOptions(createKeyManagerFactory(sslContextParameters)));
        options.setTrustOptions(new TrustManagerFactoryOptions(createTrustManagerFactory(sslContextParameters)));
    }

    private static class KeyManagerFactoryOptions implements KeyCertOptions {
        private final KeyManagerFactory keyManagerFactory;

        public KeyManagerFactoryOptions(KeyManagerFactory keyManagerFactory) {
            this.keyManagerFactory = keyManagerFactory;
        }

        private KeyManagerFactoryOptions(KeyManagerFactoryOptions other) {
            this.keyManagerFactory = other.keyManagerFactory;
        }

        @Override
        public KeyCertOptions copy() {
            return new KeyManagerFactoryOptions(this);
        }

        @Override
        public KeyManagerFactory getKeyManagerFactory(Vertx vertx) {
            return keyManagerFactory;
        }

        @Override
        public Function<String, X509KeyManager> keyManagerMapper(Vertx vertx) {
            return keyManagerFactory.getKeyManagers()[0] instanceof X509KeyManager
                    ? serverName -> (X509KeyManager) keyManagerFactory.getKeyManagers()[0] : null;
        }
    }

    private static class TrustManagerFactoryOptions implements TrustOptions {
        private final TrustManagerFactory trustManagerFactory;

        public TrustManagerFactoryOptions(TrustManagerFactory trustManagerFactory) {
            this.trustManagerFactory = trustManagerFactory;
        }

        private TrustManagerFactoryOptions(TrustManagerFactoryOptions other) {
            trustManagerFactory = other.trustManagerFactory;
        }

        @Override
        public TrustOptions copy() {
            return new TrustManagerFactoryOptions(this);
        }

        @Override
        public TrustManagerFactory getTrustManagerFactory(Vertx vertx) {
            return trustManagerFactory;
        }

        @Override
        public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) {
            return serverName -> trustManagerFactory.getTrustManagers();
        }
    }

    /**
     * Extracts the port number from the endpoint URI path or returns the Vert.x default HTTP server port (0) if one was
     * not provided
     */
    public static int extractPortNumber(String remaining) {
        int index1 = remaining.indexOf(':');
        int index2 = remaining.indexOf('/');
        if ((index1 != -1) && (index2 != -1)) {
            String result = remaining.substring(index1 + 1, index2);
            if (result.isEmpty()) {
                throw new IllegalArgumentException("Unable to resolve port from URI: " + remaining);
            }

            try {
                return Integer.parseInt(result);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse port: " + result);
            }
        } else {
            return VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PORT;
        }
    }

    /**
     * Extracts the host name from the endpoint URI path or returns the Vert.x default HTTP server host (0.0.0.0) if one
     * was not provided
     */
    public static String extractHostName(String remaining) {
        int index = remaining.indexOf(':');
        if (index != -1) {
            return remaining.substring(0, index);
        } else {
            return VertxWebsocketConstants.DEFAULT_VERTX_SERVER_HOST;
        }
    }

    /**
     * Extracts the WebSocket path from the endpoint URI path or returns the Vert.x default HTTP server path (/) if one
     * was not provided
     */
    public static String extractPath(String remaining) {
        int index = remaining.indexOf('/');
        if (index != -1) {
            return remaining.substring(index);
        } else {
            return VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PATH + remaining;
        }
    }

    /**
     * Creates a VertxWebsocketHostKey from a given VertxWebsocketConfiguration
     */
    public static VertxWebsocketHostKey createHostKey(VertxWebsocketConfiguration configuration) {
        return new VertxWebsocketHostKey(configuration.getHost(), configuration.getPort());
    }

    /**
     * Creates a KeyManagerFactory from a given SSLContextParameters
     */
    private static KeyManagerFactory createKeyManagerFactory(SSLContextParameters sslContextParameters) throws Exception {
        final KeyManagersParameters keyManagers = sslContextParameters.getKeyManagers();
        if (keyManagers == null) {
            return null;
        }

        String kmfAlgorithm = keyManagers.getAlgorithm();
        if (kmfAlgorithm == null) {
            kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        }

        KeyManagerFactory kmf;
        if (keyManagers.getProvider() == null) {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
        } else {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm, keyManagers.getProvider());
        }

        char[] kmfPassword = null;
        if (keyManagers.getKeyPassword() != null) {
            kmfPassword = keyManagers.getKeyPassword().toCharArray();
        }

        KeyStore ks = keyManagers.getKeyStore() == null ? null : keyManagers.getKeyStore().createKeyStore();

        kmf.init(ks, kmfPassword);
        return kmf;
    }

    /**
     * Creates a TrustManagerFactory from a given SSLContextParameters
     */
    private static TrustManagerFactory createTrustManagerFactory(SSLContextParameters sslContextParameters) throws Exception {
        final TrustManagersParameters trustManagers = sslContextParameters.getTrustManagers();
        if (trustManagers == null) {
            return null;
        }

        TrustManagerFactory tmf = null;

        if (trustManagers.getKeyStore() != null) {
            String tmfAlgorithm = trustManagers.getAlgorithm();
            if (tmfAlgorithm == null) {
                tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            }

            if (trustManagers.getProvider() == null) {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            } else {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm, trustManagers.getProvider());
            }

            KeyStore ks = trustManagers.getKeyStore() == null ? null : trustManagers.getKeyStore().createKeyStore();
            tmf.init(ks);
        }
        return tmf;
    }
}
