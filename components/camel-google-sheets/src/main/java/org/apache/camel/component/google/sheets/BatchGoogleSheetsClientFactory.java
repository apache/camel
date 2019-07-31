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
package org.apache.camel.component.google.sheets;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;

public class BatchGoogleSheetsClientFactory implements GoogleSheetsClientFactory {

    private final HttpTransport transport;
    private final JacksonFactory jsonFactory;

    public BatchGoogleSheetsClientFactory() {
        this(new NetHttpTransport(), new JacksonFactory());
    }

    public BatchGoogleSheetsClientFactory(HttpTransport httpTransport) {
        this(httpTransport, new JacksonFactory());
    }

    public BatchGoogleSheetsClientFactory(HttpTransport httpTransport, JacksonFactory jacksonFactory) {
        this.transport = httpTransport;
        this.jsonFactory = jacksonFactory;
    }

    @Override
    public Sheets makeClient(String clientId,
                             String clientSecret,
                             String applicationName,
                             String refreshToken,
                             String accessToken) {
        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("clientId and clientSecret are required to create Google Sheets client.");
        }

        try {
            Credential credential = authorize(clientId, clientSecret, refreshToken, accessToken);

            Sheets.Builder clientBuilder = new Sheets.Builder(transport, jsonFactory, credential)
                                                     .setApplicationName(applicationName);
            configure(clientBuilder);
            return clientBuilder.build();
        } catch (Exception e) {
            throw new RuntimeCamelException("Could not create Google Sheets client.", e);
        }
    }

    /**
     * Subclasses may add customized configuration to client builder.
     * @param clientBuilder
     */
    protected void configure(Sheets.Builder clientBuilder) {
        clientBuilder.setRootUrl(Sheets.DEFAULT_ROOT_URL);
    }

    // Authorizes the installed application to access user's protected data.
    private Credential authorize(String clientId, String clientSecret, String refreshToken, String accessToken) {
        // authorize
        Credential credential = new GoogleCredential.Builder()
                        .setJsonFactory(jsonFactory)
                        .setTransport(transport)
                        .setClientSecrets(clientId, clientSecret)
                        .build();

        if (ObjectHelper.isNotEmpty(refreshToken)) {
            credential.setRefreshToken(refreshToken);
        }

        if (ObjectHelper.isNotEmpty(accessToken)) {
            credential.setAccessToken(accessToken);
        }

        return credential;
    }
}
