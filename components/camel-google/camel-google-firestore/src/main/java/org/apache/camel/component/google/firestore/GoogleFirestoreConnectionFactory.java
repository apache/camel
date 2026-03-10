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
package org.apache.camel.component.google.firestore;

import com.google.api.client.util.Strings;
import com.google.auth.Credentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.common.GoogleCredentialsHelper;

/**
 * Factory for creating Google Firestore client connections.
 */
public final class GoogleFirestoreConnectionFactory {

    /**
     * Prevent instantiation.
     */
    private GoogleFirestoreConnectionFactory() {
    }

    /**
     * Creates a Firestore client based on the provided configuration.
     *
     * @param  context       the Camel context
     * @param  configuration the Firestore configuration
     * @return               a configured Firestore client
     * @throws Exception     if the client cannot be created
     */
    public static Firestore create(CamelContext context, GoogleFirestoreConfiguration configuration) throws Exception {
        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder();

        // Use GoogleCredentialsHelper for credential resolution (service account key or ADC fallback)
        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, configuration);
        if (credentials != null) {
            builder.setCredentials(credentials);
        }

        // Set project ID if provided
        if (!Strings.isNullOrEmpty(configuration.getProjectId())) {
            builder.setProjectId(configuration.getProjectId());
        }

        // Set database ID if provided (otherwise uses default)
        if (!Strings.isNullOrEmpty(configuration.getDatabaseId())) {
            builder.setDatabaseId(configuration.getDatabaseId());
        }

        return builder.build().getService();
    }
}
