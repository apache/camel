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
package org.apache.camel.component.google.bigquery;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.Collections;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Strings;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleBigQueryConnectionFactory {

    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryConnectionFactory.class);

    private String serviceAccount;
    private String serviceAccountKey;
    private String credentialsFileLocation;
    private String serviceURL;

    private Bigquery client;

    public GoogleBigQueryConnectionFactory() {
    }

    public synchronized Bigquery getDefaultClient() throws Exception {
        if (this.client == null) {
            this.client = buildClient();
        }
        return this.client;
    }

    public Bigquery getMultiThreadClient(int parallelThreads) throws Exception {

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setDefaultMaxPerRoute(parallelThreads);
        cm.setMaxTotal(parallelThreads);
        CloseableHttpClient httpClient = HttpClients.createMinimal(cm);

        return buildClient(new ApacheHttpTransport(httpClient));
    }

    private Bigquery buildClient() throws Exception {
        return buildClient(GoogleNetHttpTransport.newTrustedTransport());
    }

    private Bigquery buildClient(HttpTransport httpTransport) throws Exception {

        GoogleCredential credential = null;

        if (!Strings.isNullOrEmpty(serviceAccount) && !Strings.isNullOrEmpty(serviceAccountKey)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Service Account and Key have been set explicitly. Initialising BigQuery using Service Account " + serviceAccount);
            }
            credential = createFromAccountKeyPair(httpTransport);
        }

        if (credential == null && !Strings.isNullOrEmpty(credentialsFileLocation)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Key File Name has been set explicitly. Initialising BigQuery using Key File " + credentialsFileLocation);
            }
            credential = createFromFile();
        }

        if (credential == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("No explicit Service Account or Key File Name have been provided. Initialising BigQuery using defaults ");
            }
            credential = createDefault();
        }

        Bigquery.Builder builder = new Bigquery.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("camel-google-bigquery");

        // Local emulator, SOCKS proxy, etc.
        if (serviceURL != null) {
            builder.setRootUrl(serviceURL);
        }

        return builder.build();
    }

    private GoogleCredential createFromFile() throws Exception {
        try (InputStream is = new FileInputStream(credentialsFileLocation)) {
            GoogleCredential credential = GoogleCredential.fromStream(is);

            if (credential.createScopedRequired()) {
                credential = credential.createScoped(BigqueryScopes.all());
            }

            return credential;
        }
    }

    private GoogleCredential createDefault() throws Exception {
        GoogleCredential credential = GoogleCredential.getApplicationDefault();

        Collection<String> scopes = Collections.singletonList(BigqueryScopes.BIGQUERY);

        if (credential.createScopedRequired()) {
            credential = credential.createScoped(scopes);
        }

        return credential;
    }

    private GoogleCredential createFromAccountKeyPair(HttpTransport httpTransport) {
        try {
            return new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(JSON_FACTORY)
                    .setServiceAccountId(serviceAccount)
                    .setServiceAccountScopes(BigqueryScopes.all())
                    .setServiceAccountPrivateKey(getPrivateKeyFromString(serviceAccountKey))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PrivateKey getPrivateKeyFromString(String serviceKeyPem) {
        PrivateKey privateKey;
        try {
            String privKeyPEM = serviceKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                                             .replace("-----END PRIVATE KEY-----", "")
                                             .replace("\r", "")
                                             .replace("\n", "");

            byte[] encoded = Base64.decodeBase64(privKeyPEM);

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            privateKey = KeyFactory.getInstance("RSA")
                                   .generatePrivate(keySpec);
        } catch (Exception e) {
            String error = "Constructing Private Key from PEM string failed: " + e.getMessage();
            logger.error(error, e);
            throw new RuntimeException(e);
        }
        return privateKey;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    public GoogleBigQueryConnectionFactory setServiceAccount(String serviceAccount) {
        this.serviceAccount = serviceAccount;
        resetClient();
        return this;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public GoogleBigQueryConnectionFactory setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
        resetClient();
        return this;
    }

    public String getCredentialsFileLocation() {
        return credentialsFileLocation;
    }

    public GoogleBigQueryConnectionFactory setCredentialsFileLocation(String credentialsFileLocation) {
        this.credentialsFileLocation = credentialsFileLocation;
        resetClient();
        return this;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public GoogleBigQueryConnectionFactory setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
        resetClient();
        return this;
    }

    private synchronized void resetClient() {
        this.client = null;
    }
}
