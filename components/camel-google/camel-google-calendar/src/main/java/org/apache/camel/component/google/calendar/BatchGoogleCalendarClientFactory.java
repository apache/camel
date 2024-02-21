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
import java.io.IOException;
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
import org.apache.camel.support.ResourceHelper;

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
        // Service Account
        boolean serviceAccount
                = null != emailAddress && !emailAddress.isEmpty() && null != p12FileName && !p12FileName.isEmpty();

        if (!serviceAccount && (clientId == null || clientSecret == null)) {
            throw new IllegalArgumentException("clientId and clientSecret are required to create Google Calendar client.");
        }

        try {
            Credential credential;
            if (serviceAccount) {
                credential = authorizeServiceAccount(emailAddress, p12FileName, scopes, user);
            } else {
                credential = authorize(clientId, clientSecret);
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    credential.setRefreshToken(refreshToken);
                }
                if (accessToken != null && !accessToken.isEmpty()) {
                    credential.setAccessToken(accessToken);
                }
            }
            return new Calendar.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Google Calendar client.", e);
        }
    }

    // Authorizes the installed application to access user's protected data.
    private Credential authorize(String clientId, String clientSecret) {
        // authorize
        return new GoogleCredential.Builder()
                .setJsonFactory(jsonFactory)
                .setTransport(transport)
                .setClientSecrets(clientId, clientSecret)
                .build();
    }

    // authorize with P12-Certificate file
    private Credential authorizeServiceAccount(String emailAddress, String p12FileName, Collection<String> scopes, String user)
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
            Credential credential = authorizeServiceAccount(camelContext, serviceAccountKey, delegate, scopes);
            return new Calendar.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Google Calendar client.", e);
        }
    }

    // authorize with JSON-Credentials
    private Credential authorizeServiceAccount(
            CamelContext camelContext, String serviceAccountKey, String delegate, Collection<String> scopes) {
        // authorize
        try {
            GoogleCredential cred = GoogleCredential
                    .fromStream(ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, serviceAccountKey),
                            transport,
                            jsonFactory)
                    .createScoped(scopes != null && !scopes.isEmpty() ? scopes : null).createDelegated(delegate);
            cred.refreshToken();
            return cred;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
