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
package org.apache.camel.component.google.mail;

import java.util.Collection;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.common.GoogleCredentialsHelper;

public class BatchGoogleMailClientFactory implements GoogleMailClientFactory {
    private NetHttpTransport transport;
    private JacksonFactory jsonFactory;

    public BatchGoogleMailClientFactory() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = new JacksonFactory();
    }

    @Override
    public Gmail makeClient(
            String clientId, String clientSecret, Collection<String> scopes, String applicationName, String refreshToken,
            String accessToken) {
        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("clientId and clientSecret are required to create Gmail client.");
        }
        try {
            // Use GoogleCredentialsHelper for OAuth credentials
            GoogleMailConfiguration tempConfig = new GoogleMailConfiguration();
            tempConfig.setClientId(clientId);
            tempConfig.setClientSecret(clientSecret);
            tempConfig.setRefreshToken(refreshToken);
            tempConfig.setAccessToken(accessToken);

            Credential credential
                    = GoogleCredentialsHelper.getOAuthCredential(null, tempConfig, scopes, transport, jsonFactory);
            return new Gmail.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Gmail client.", e);
        }
    }

    @Override
    public Gmail makeClient(
            CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
            String delegate) {
        if (serviceAccountKey == null) {
            throw new IllegalArgumentException("serviceAccountKey is required to create Gmail client.");
        }
        try {
            // Use GoogleCredentialsHelper for service account credentials
            GoogleMailConfiguration tempConfig = new GoogleMailConfiguration();
            tempConfig.setServiceAccountKey(serviceAccountKey);
            tempConfig.setDelegate(delegate);

            Credential credential
                    = GoogleCredentialsHelper.getOAuthCredential(camelContext, tempConfig, scopes, transport, jsonFactory);
            return new Gmail.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Gmail client.", e);
        }
    }
}
