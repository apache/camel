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

public class DefaultGoogleDriveClientFactory implements GoogleDriveClientFactory {
    private NetHttpTransport transport;
    private JacksonFactory jsonFactory;
    private FileDataStoreFactory dataStoreFactory;
    // TODO Directory to store user credentials
    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".store/drive_sample");

    public DefaultGoogleDriveClientFactory() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = new JacksonFactory();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.camel.component.google.drive.GoogleDriveClientFactory#makeClient
     * (java.lang.String, java.lang.String, java.util.Collection)
     */
    @Override
    public Drive makeClient(String clientId, String clientSecret, Collection<String> scopes) {
        Credential credential;
        try {
            credential = authorize(clientId, clientSecret, scopes);
            return new Drive.Builder(transport, jsonFactory, credential).build();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    // Authorizes the installed application to access user's protected data.
    private Credential authorize(String clientId, String clientSecret, Collection<String> scopes) throws Exception {
        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
        // set up authorization code flow
        // TODO refresh token support too
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, clientId, clientSecret, scopes).setDataStoreFactory(dataStoreFactory).build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
}