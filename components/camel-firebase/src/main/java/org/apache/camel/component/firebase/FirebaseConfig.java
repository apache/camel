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
package org.apache.camel.component.firebase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/**
 * Contains the elements needed to connect to Firebase
 */
public final class FirebaseConfig {

    private final String databaseUrl;

    private final String rootReference;

    private final String serviceAccountFile;

    private final String keyName;

    private final boolean async;

    private FirebaseApp firebaseApp;

    private FirebaseConfig(Builder builder) {
        this.databaseUrl = builder.databaseUrl;
        this.rootReference = builder.rootReference;
        this.serviceAccountFile = builder.serviceAccountFile;
        this.keyName = builder.keyName;
        this.async = builder.async;
    }

    public void init() throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get(serviceAccountFile))) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setServiceAccount(in)
                    .setDatabaseUrl(this.databaseUrl)
                    .build();
            firebaseApp = FirebaseApp.initializeApp(options, UUID.randomUUID().toString());
        }
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public String getRootReference() {
        return rootReference;
    }

    public String getServiceAccountFile() {
        return serviceAccountFile;
    }

    public String getKeyName() {
        return keyName;
    }

    public FirebaseApp getFirebaseApp() {
        return firebaseApp;
    }

    public boolean isAsync() {
        return async;
    }

    public static class Builder {

        private final String databaseUrl;

        private final String rootReference;

        private final String serviceAccountFile;

        private String keyName;

        private boolean async;

        public Builder(String databaseUrl, String rootReference, String serviceAccountFile) {
            this.databaseUrl = databaseUrl;
            this.rootReference = rootReference;
            this.serviceAccountFile = serviceAccountFile;
        }

        public Builder setKeyName(String keyName) {
            this.keyName = keyName;
            return this;
        }

        public Builder setAsync(boolean async) {
            this.async = async;
            return this;
        }

        public FirebaseConfig build() {
            return new FirebaseConfig(this);
        }
    }
}
