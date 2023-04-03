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
package org.apache.camel.component.google.drive;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.Collection;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchGoogleDriveClientFactory implements GoogleDriveClientFactory {
    private static final Logger LOG = LoggerFactory.getLogger(BatchGoogleDriveClientFactory.class);
    private NetHttpTransport transport;
    private JacksonFactory jsonFactory;

    public BatchGoogleDriveClientFactory() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = new JacksonFactory();
    }

    public BatchGoogleDriveClientFactory(String proxyHost, int proxyPort) {
        try {
            Proxy proxy = new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(InetAddress.getByName(proxyHost), proxyPort));
            this.transport = new NetHttpTransport.Builder().setProxy(proxy).build();
            this.jsonFactory = new JacksonFactory();
        } catch (UnknownHostException e) {
            LOG.error("Unknow proxy host", e);
        }
    }

    @Override
    public Drive makeClient(
            String clientId, String clientSecret, Collection<String> scopes, String applicationName, String refreshToken,
            String accessToken) {
        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("clientId and clientSecret are required to create Google Drive client.");
        }
        try {
            Credential credential = authorize(clientId, clientSecret, scopes);

            if (refreshToken != null && !refreshToken.isEmpty()) {
                credential.setRefreshToken(refreshToken);
            }
            if (accessToken != null && !accessToken.isEmpty()) {
                credential.setAccessToken(accessToken);
            }
            return new Drive.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Google Drive client.", e);
        }
    }

    // Authorizes the installed application to access user's protected data.
    private Credential authorize(String clientId, String clientSecret, Collection<String> scopes) {
        // authorize
        return new GoogleCredential.Builder()
                .setJsonFactory(jsonFactory)
                .setTransport(transport)
                .setClientSecrets(clientId, clientSecret)
                .setServiceAccountScopes(scopes)
                .build();
    }

    @Override
    public Drive makeClient(
            CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
            String delegate) {
        if (serviceAccountKey == null) {
            throw new IllegalArgumentException("serviceAccountKey is required to create Gmail client.");
        }
        try {
            Credential credential = authorizeServiceAccount(camelContext, serviceAccountKey, delegate, scopes);
            return new Drive.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Gmail client.", e);
        }
    }

    private Credential authorizeServiceAccount(
            CamelContext camelContext, String serviceAccountKey, String delegate, Collection<String> scopes) {
        // authorize
        try {
            GoogleCredential cred = GoogleCredential
                    .fromStream(ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, serviceAccountKey),
                            transport,
                            jsonFactory)
                    .createScoped(scopes != null && !scopes.isEmpty() ? scopes : null)
                    .createDelegated(delegate);
            cred.refreshToken();
            return cred;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
