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

package org.apache.camel.test.infra.azure.cosmosdb.clients;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.apache.camel.test.infra.azure.cosmosdb.common.AzureCosmosDbProperties;

public final class AzureCosmosDbClientUtils {
    private static final String TRUST_STORE = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";
    private static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    private static final String TRUST_STORE_PROVIDER = "javax.net.ssl.trustStoreProvider";

    private AzureCosmosDbClientUtils() {

    }

    /**
     * Builds a client of the account the tests run against: the local Floci AZ container, unless the
     * {@link AzureCosmosDbProperties#INSTANCE_TYPE} system property is {@code remote}. Like the clients the
     * azure-cosmosdb component builds by default, it returns the items it writes.
     */
    public static CosmosAsyncClient createAsyncClient() {
        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(System.getProperty(AzureCosmosDbProperties.ENDPOINT))
                .key(System.getProperty(AzureCosmosDbProperties.ACCESS_KEY))
                .contentResponseOnWriteEnabled(true);

        String instanceType = System.getProperty(AzureCosmosDbProperties.INSTANCE_TYPE);
        if (instanceType == null || instanceType.equals("local-azure-container")) {
            // Floci AZ emulates the gateway API only, and the client keeps to the endpoint whatever locations the
            // emulator advertises
            builder.gatewayMode()
                    .endpointDiscoveryEnabled(false);

            return buildTrusting(builder, System.getProperty(AzureCosmosDbProperties.CERTIFICATE_URL));
        }

        return builder.buildAsyncClient();
    }

    /*
     * The Cosmos DB SDK has no option for the certificates a client trusts: a client trusts the default trust store of
     * the JVM, which it loads when it is built. The default trust store holds the certificate of the emulator, next to the
     * certificates the JVM trusts, only while the client is built: the client trusts the emulator, and the trust of the
     * JVM is the same once the client is built.
     */
    private static synchronized CosmosAsyncClient buildTrusting(CosmosClientBuilder builder, String certificateUrl) {
        Map<String, String> previous = new HashMap<>();
        for (String name : new String[] { TRUST_STORE, TRUST_STORE_TYPE, TRUST_STORE_PASSWORD, TRUST_STORE_PROVIDER }) {
            previous.put(name, System.getProperty(name));
        }

        Path trustStoreFile = null;
        try {
            String password = UUID.randomUUID().toString();
            trustStoreFile = Files.createTempFile("floci-az-cosmos-trust", ".p12");
            try (OutputStream out = Files.newOutputStream(trustStoreFile)) {
                trustStore(readCertificate(certificateUrl)).store(out, password.toCharArray());
            }

            System.setProperty(TRUST_STORE, trustStoreFile.toString());
            System.setProperty(TRUST_STORE_TYPE, "PKCS12");
            System.setProperty(TRUST_STORE_PASSWORD, password);
            System.clearProperty(TRUST_STORE_PROVIDER);

            return builder.buildAsyncClient();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot trust the certificate of Floci AZ at " + certificateUrl, e);
        } finally {
            previous.forEach((name, value) -> {
                if (value == null) {
                    System.clearProperty(name);
                } else {
                    System.setProperty(name, value);
                }
            });
            deleteQuietly(trustStoreFile);
        }
    }

    private static X509Certificate readCertificate(String certificateUrl) throws IOException, GeneralSecurityException {
        try (InputStream in = URI.create(certificateUrl).toURL().openStream()) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    // the certificates the JVM trusts, and the one of the emulator
    private static KeyStore trustStore(X509Certificate certificate) throws IOException, GeneralSecurityException {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);

        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        int index = 0;
        for (TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                for (X509Certificate trusted : x509TrustManager.getAcceptedIssuers()) {
                    trustStore.setCertificateEntry("jvm-" + index++, trusted);
                }
            }
        }
        trustStore.setCertificateEntry("floci-az", certificate);

        return trustStore;
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                file.toFile().deleteOnExit();
            }
        }
    }
}
