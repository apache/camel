/**
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
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchGoogleCalendarClientFactory implements GoogleCalendarClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(BatchGoogleCalendarClientFactory.class);
    private NetHttpTransport transport;
    private JacksonFactory jsonFactory;

    public BatchGoogleCalendarClientFactory() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = new JacksonFactory();
    }

    @Override
    public Calendar makeClient(String clientId, String clientSecret,
                               Collection<String> scopes, String applicationName, String refreshToken,
                               String accessToken, String emailAddress, String p12FileName, String user) {
        boolean serviceAccount = false;
        // if emailAddress and p12FileName values are present, assume Google Service Account
        if (null != emailAddress && !"".equals(emailAddress) && null != p12FileName && !"".equals(p12FileName)) {
            serviceAccount = true;
        }
        if (!serviceAccount && (clientId == null || clientSecret == null)) {
            throw new IllegalArgumentException("clientId and clientSecret are required to create Google Calendar client.");
        }

        try {
            Credential credential;
            if (serviceAccount) {
                credential = authorizeServiceAccount(emailAddress, p12FileName, scopes, user);
            } else {
                credential = authorize(clientId, clientSecret, scopes);
                if (refreshToken != null && !"".equals(refreshToken)) {
                    credential.setRefreshToken(refreshToken);
                }
                if (accessToken != null && !"".equals(accessToken)) {
                    credential.setAccessToken(accessToken);
                }
            }
            return new Calendar.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Google Calendar client.", e);
        }
    }

    // Authorizes the installed application to access user's protected data.
    private Credential authorize(String clientId, String clientSecret, Collection<String> scopes) throws Exception {
        // authorize
        return new GoogleCredential.Builder()
                .setJsonFactory(jsonFactory)
                .setTransport(transport)
                .setClientSecrets(clientId, clientSecret)
                .build();
    }

    private Credential authorizeServiceAccount(String emailAddress, String p12FileName, Collection<String> scopes, String user) throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        // set the service account user when provided
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(emailAddress)
                .setServiceAccountPrivateKeyFromP12File(new File(p12FileName))
                .setServiceAccountScopes(scopes)
                .setServiceAccountUser(user)
                .build();
        return credential;
    }
}
