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
package org.apache.camel.component.google.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Utility class providing centralized credential resolution for Google services.
 * <p>
 * This class handles the common patterns for obtaining Google credentials:
 * <ul>
 * <li>Service Account JSON key file (for GCP native clients)</li>
 * <li>OAuth 2.0 credentials with client ID/secret (for legacy Google API clients)</li>
 * <li>Application Default Credentials (ADC) as fallback</li>
 * </ul>
 */
public final class GoogleCredentialsHelper {

    private static final HttpTransport DEFAULT_HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory DEFAULT_JSON_FACTORY = new JacksonFactory();

    private GoogleCredentialsHelper() {
        // Utility class - prevent instantiation
    }

    // ==================== GCP Native Clients (google-cloud-* libraries) ====================

    /**
     * Gets credentials for GCP native clients (Storage, Firestore, BigQuery, etc.).
     * <p>
     * Resolution order:
     * <ol>
     * <li>Service Account key file if provided</li>
     * <li>Application Default Credentials (ADC) as fallback</li>
     * </ol>
     *
     * @param  context     the Camel context for resource resolution
     * @param  config      the component configuration
     * @param  scopes      OAuth scopes to apply (optional, used for service account)
     * @return             Google credentials for use with GCP native clients
     * @throws IOException if credentials cannot be loaded
     */
    public static Credentials getCredentials(
            CamelContext context,
            GoogleCommonConfiguration config,
            Collection<String> scopes)
            throws IOException {

        if (!config.isAuthenticate()) {
            return null;
        }

        String serviceAccountKey = config.getServiceAccountKey();
        if (ObjectHelper.isNotEmpty(serviceAccountKey)) {
            return loadServiceAccountCredentials(context, serviceAccountKey, scopes, config.getDelegate());
        }

        // Fall back to Application Default Credentials
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        if (scopes != null && !scopes.isEmpty()) {
            credentials = credentials.createScoped(scopes);
        }
        return credentials;
    }

    /**
     * Gets credentials for GCP native clients using configuration's scopes.
     *
     * @param  context     the Camel context for resource resolution
     * @param  config      the component configuration
     * @return             Google credentials for use with GCP native clients
     * @throws IOException if credentials cannot be loaded
     */
    public static Credentials getCredentials(
            CamelContext context,
            GoogleCommonConfiguration config)
            throws IOException {
        return getCredentials(context, config, config.getScopesAsList());
    }

    // ==================== Legacy Google API Clients (google-api-client library) ====================

    /**
     * Gets OAuth credential for legacy Google API clients (Sheets, Calendar, Drive, Mail, etc.).
     * <p>
     * Resolution order:
     * <ol>
     * <li>OAuth 2.0 credentials if clientId and clientSecret are provided</li>
     * <li>Service Account key file if provided</li>
     * </ol>
     *
     * @param  context          the Camel context for resource resolution
     * @param  config           the component configuration
     * @param  scopes           OAuth scopes to apply
     * @param  httpTransport    HTTP transport to use (pass null for default)
     * @param  jsonFactory      JSON factory to use (pass null for default)
     * @return                  Google credential for use with legacy API clients
     * @throws IOException      if credentials cannot be loaded
     * @throws RuntimeException if neither OAuth nor service account credentials are configured
     */
    public static Credential getOAuthCredential(
            CamelContext context,
            GoogleCommonConfiguration config,
            Collection<String> scopes,
            HttpTransport httpTransport,
            JsonFactory jsonFactory)
            throws IOException {

        HttpTransport transport = httpTransport != null ? httpTransport : DEFAULT_HTTP_TRANSPORT;
        JsonFactory factory = jsonFactory != null ? jsonFactory : DEFAULT_JSON_FACTORY;

        // Check for OAuth 2.0 credentials first
        if (hasOAuthCredentials(config)) {
            return createOAuthCredential(config, scopes, transport, factory);
        }

        // Check for Service Account credentials
        String serviceAccountKey = config.getServiceAccountKey();
        if (ObjectHelper.isNotEmpty(serviceAccountKey)) {
            return loadServiceAccountAsLegacyCredential(
                    context, serviceAccountKey, scopes, config.getDelegate(), transport, factory);
        }

        throw new IllegalArgumentException(
                "Either OAuth credentials (clientId + clientSecret) or serviceAccountKey must be provided");
    }

    /**
     * Gets OAuth credential for legacy Google API clients using default transport and factory.
     *
     * @param  context     the Camel context for resource resolution
     * @param  config      the component configuration
     * @param  scopes      OAuth scopes to apply
     * @return             Google credential for use with legacy API clients
     * @throws IOException if credentials cannot be loaded
     */
    public static Credential getOAuthCredential(
            CamelContext context,
            GoogleCommonConfiguration config,
            Collection<String> scopes)
            throws IOException {
        return getOAuthCredential(context, config, scopes, null, null);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Checks if OAuth 2.0 credentials are configured.
     */
    private static boolean hasOAuthCredentials(GoogleCommonConfiguration config) {
        return ObjectHelper.isNotEmpty(config.getClientId())
                && ObjectHelper.isNotEmpty(config.getClientSecret());
    }

    /**
     * Creates OAuth 2.0 credential from client ID, secret, and tokens.
     */
    private static Credential createOAuthCredential(
            GoogleCommonConfiguration config,
            Collection<String> scopes,
            HttpTransport transport,
            JsonFactory jsonFactory) {

        Credential credential = new GoogleCredential.Builder()
                .setJsonFactory(jsonFactory)
                .setTransport(transport)
                .setClientSecrets(config.getClientId(), config.getClientSecret())
                .setServiceAccountScopes(scopes)
                .build();

        if (ObjectHelper.isNotEmpty(config.getRefreshToken())) {
            credential.setRefreshToken(config.getRefreshToken());
        }

        if (ObjectHelper.isNotEmpty(config.getAccessToken())) {
            credential.setAccessToken(config.getAccessToken());
        }

        return credential;
    }

    /**
     * Loads service account credentials from JSON key file for GCP native clients.
     */
    private static Credentials loadServiceAccountCredentials(
            CamelContext context,
            String serviceAccountKey,
            Collection<String> scopes,
            String delegate)
            throws IOException {

        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, serviceAccountKey)) {
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(is);

            if (scopes != null && !scopes.isEmpty()) {
                credentials = (ServiceAccountCredentials) credentials.createScoped(scopes);
            }

            if (ObjectHelper.isNotEmpty(delegate)) {
                credentials = (ServiceAccountCredentials) credentials.createDelegated(delegate);
            }

            return credentials;
        }
    }

    /**
     * Loads service account credentials as legacy GoogleCredential for older API clients.
     */
    private static Credential loadServiceAccountAsLegacyCredential(
            CamelContext context,
            String serviceAccountKey,
            Collection<String> scopes,
            String delegate,
            HttpTransport transport,
            JsonFactory jsonFactory)
            throws IOException {

        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, serviceAccountKey)) {
            GoogleCredential credential = GoogleCredential
                    .fromStream(is, transport, jsonFactory);

            if (scopes != null && !scopes.isEmpty()) {
                credential = credential.createScoped(scopes);
            }

            if (ObjectHelper.isNotEmpty(delegate)) {
                credential = credential.createDelegated(delegate);
            }

            credential.refreshToken();
            return credential;
        }
    }
}
