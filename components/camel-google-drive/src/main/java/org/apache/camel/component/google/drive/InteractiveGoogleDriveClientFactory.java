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

import java.util.Collection;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractiveGoogleDriveClientFactory implements GoogleDriveClientFactory {
    private static final Logger LOG = LoggerFactory.getLogger(InteractiveGoogleDriveClientFactory.class);
    private static final java.io.File DATA_STORE_DIR = new java.io.File(".google_drive");
    private NetHttpTransport transport;
    private JacksonFactory jsonFactory;
    private FileDataStoreFactory dataStoreFactory;
    
    public InteractiveGoogleDriveClientFactory() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = new JacksonFactory();
    }

    @Override
    public Drive makeClient(String clientId, String clientSecret, Collection<String> scopes, String applicationName, String refreshToken, String accessToken) {
        Credential credential;
        try {
            credential = authorize(clientId, clientSecret, scopes);
            return new Drive.Builder(transport, jsonFactory, credential).setApplicationName(applicationName).build();
        } catch (Exception e) {
            LOG.error("Could not create Google Drive client.", e);
        }
        return null;
    }

    /**
     * This method interactively creates the necessary authorization tokens on first run, 
     * and stores the tokens in the data store. Subsequent runs will no longer require interactivity
     * as long as the credentials file is not removed.
     */
    private Credential authorize(String clientId, String clientSecret, Collection<String> scopes) throws Exception {
        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, clientId, clientSecret, scopes)
            .setDataStoreFactory(dataStoreFactory)
            .setAccessType("offline")
            .build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
}
