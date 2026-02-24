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
package org.apache.camel.component.google.calendar;

import java.io.File;
import java.util.Collection;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.common.GoogleCredentialsHelper;

public class BatchGoogleCalendarClientFactory implements GoogleCalendarClientFactory {
    private NetHttpTransport transport;
    private JacksonFactory jsonFactory;

    public BatchGoogleCalendarClientFactory() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = new JacksonFactory();
    }

    @Override
    public Calendar makeClient(
            String clientId, String clientSecret, Collection<String> scopes, String applicationName, String refreshToken,
            String accessToken,
            String emailAddress, String p12FileName, String user) {
        // if emailAddress and p12FileName values are present, assume Google
        // Service Account with P12 file (legacy)
        boolean serviceAccount
                = null != emailAddress && !emailAddress.isEmpty() && null != p12FileName && !p12FileName.isEmpty();

        if (!serviceAccount && (clientId == null || clientSecret == null)) {
            throw new IllegalArgumentException("clientId and clientSecret are required to create Google Calendar client.");
        }

        try {
            Credential credential;
            if (serviceAccount) {
                // Legacy P12 file authentication - keep as is since GoogleCredentialsHelper doesn't support P12
                credential = authorizeServiceAccountWithP12(emailAddress, p12FileName, scopes, user);
            } else {
                // Use GoogleCredentialsHelper for OAuth credentials
                GoogleCalendarConfiguration tempConfig = new GoogleCalendarConfiguration();
                tempConfig.setClientId(clientId);
                tempConfig.setClientSecret(clientSecret);
                tempConfig.setRefreshToken(refreshToken);
                tempConfig.setAccessToken(accessToken);

                credential = GoogleCredentialsHelper.getOAuthCredential(null, tempConfig, scopes, transport, jsonFactory);
            }
            return new Calendar.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Google Calendar client.", e);
        }
    }

    // authorize with P12-Certificate file (legacy method - kept for backward compatibility)
    private Credential authorizeServiceAccountWithP12(
            String emailAddress, String p12FileName, Collection<String> scopes, String user)
            throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        // set the service account user when provided
        return new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(emailAddress)
                .setServiceAccountPrivateKeyFromP12File(new File(p12FileName))
                .setServiceAccountScopes(scopes)
                .setServiceAccountUser(user)
                .build();
    }

    @Override
    public Calendar makeClient(
            CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
            String delegate) {
        if (serviceAccountKey == null) {
            throw new IllegalArgumentException("serviceAccountKey is required to create Google Calendar client.");
        }
        try {
            // Use GoogleCredentialsHelper for service account JSON key
            GoogleCalendarConfiguration tempConfig = new GoogleCalendarConfiguration();
            tempConfig.setServiceAccountKey(serviceAccountKey);
            tempConfig.setDelegate(delegate);

            Credential credential
                    = GoogleCredentialsHelper.getOAuthCredential(camelContext, tempConfig, scopes, transport, jsonFactory);
            return new Calendar.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Google Calendar client.", e);
        }
    }
}
