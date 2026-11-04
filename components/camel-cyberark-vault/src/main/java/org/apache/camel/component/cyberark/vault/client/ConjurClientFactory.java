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
package org.apache.camel.component.cyberark.vault.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.camel.component.cyberark.vault.client.impl.ConjurClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating CyberArk Conjur clients
 */
public final class ConjurClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ConjurClientFactory.class);

    private ConjurClientFactory() {
    }

    /**
     * Create a Conjur client using username and password authentication
     *
     * @param  url      the Conjur server URL
     * @param  account  the Conjur account name
     * @param  username the username for authentication
     * @param  password the password for authentication
     * @return          a configured ConjurClient instance
     */
    public static ConjurClient createWithCredentials(String url, String account, String username, String password) {
        return new ConjurClientImpl(url, account, username, password, null, null);
    }

    /**
     * Create a Conjur client using username and password authentication
     *
     * @param  url        the Conjur server URL
     * @param  account    the Conjur account name
     * @param  username   the username for authentication
     * @param  password   the password for authentication
     * @param  sslContext the SSL context for HTTPS connections, or null to use the default SSL context of the JVM
     * @return            a configured ConjurClient instance
     */
    public static ConjurClient createWithCredentials(
            String url, String account, String username, String password, SSLContext sslContext) {
        return new ConjurClientImpl(url, account, username, password, null, null, sslContext);
    }

    /**
     * Create a Conjur client using API key authentication
     *
     * @param  url      the Conjur server URL
     * @param  account  the Conjur account name
     * @param  username the username (login) for the API key
     * @param  apiKey   the API key for authentication
     * @return          a configured ConjurClient instance
     */
    public static ConjurClient createWithApiKey(String url, String account, String username, String apiKey) {
        return new ConjurClientImpl(url, account, username, null, apiKey, null);
    }

    /**
     * Create a Conjur client using API key authentication
     *
     * @param  url        the Conjur server URL
     * @param  account    the Conjur account name
     * @param  username   the username (login) for the API key
     * @param  apiKey     the API key for authentication
     * @param  sslContext the SSL context for HTTPS connections, or null to use the default SSL context of the JVM
     * @return            a configured ConjurClient instance
     */
    public static ConjurClient createWithApiKey(
            String url, String account, String username, String apiKey, SSLContext sslContext) {
        return new ConjurClientImpl(url, account, username, null, apiKey, null, sslContext);
    }

    /**
     * Create a Conjur client using a pre-authenticated token
     *
     * @param  url       the Conjur server URL
     * @param  account   the Conjur account name
     * @param  authToken the pre-authenticated access token
     * @return           a configured ConjurClient instance
     */
    public static ConjurClient createWithToken(String url, String account, String authToken) {
        return new ConjurClientImpl(url, account, null, null, null, authToken);
    }

    /**
     * Create a Conjur client using a pre-authenticated token
     *
     * @param  url        the Conjur server URL
     * @param  account    the Conjur account name
     * @param  authToken  the pre-authenticated access token
     * @param  sslContext the SSL context for HTTPS connections, or null to use the default SSL context of the JVM
     * @return            a configured ConjurClient instance
     */
    public static ConjurClient createWithToken(String url, String account, String authToken, SSLContext sslContext) {
        return new ConjurClientImpl(url, account, null, null, null, authToken, sslContext);
    }

    /**
     * Create the SSL context for the HTTPS connections to Conjur, from the verifySsl and certificatePath options.
     *
     * @param  verifySsl                whether to verify the certificate and the host name of the server. When false,
     *                                  any server is trusted and the certificate path is not used.
     * @param  certificatePath          the path of a file with the certificates (PEM or DER) to trust instead of the
     *                                  default trust store of the JVM, for example the certificate of a private CA, or
     *                                  null
     * @return                          the SSL context, or null to use the default SSL context of the JVM
     * @throws IOException              if the certificate file cannot be read
     * @throws GeneralSecurityException if the file has no certificate, or the SSL context cannot be created
     */
    public static SSLContext createSslContext(boolean verifySsl, String certificatePath)
            throws IOException, GeneralSecurityException {
        if (!verifySsl) {
            LOG.warn("SSL verification is disabled (verifySsl=false): the certificate and the host name of the"
                     + " CyberArk Conjur server are not checked");
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, new TrustManager[] { new InsecureTrustManager() }, null);
            return sslContext;
        }
        if (certificatePath == null || certificatePath.isBlank()) {
            return null;
        }

        Collection<? extends Certificate> certificates;
        try (InputStream in = Files.newInputStream(Path.of(certificatePath))) {
            certificates = CertificateFactory.getInstance("X.509").generateCertificates(in);
        }
        if (certificates.isEmpty()) {
            throw new CertificateException("No certificate in the file of the certificatePath option: " + certificatePath);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        int index = 0;
        for (Certificate certificate : certificates) {
            trustStore.setCertificateEntry("conjur-" + index++, certificate);
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    /**
     * Trusts any server without checking its certificate or its host name. Only used when verifySsl is false.
     */
    private static final class InsecureTrustManager extends X509ExtendedTrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // verifySsl is false
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // verifySsl is false
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            // verifySsl is false
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            // verifySsl is false
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            // verifySsl is false
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            // verifySsl is false
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
