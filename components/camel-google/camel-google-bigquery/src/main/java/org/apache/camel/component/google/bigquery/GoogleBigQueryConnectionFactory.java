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
package org.apache.camel.component.google.bigquery;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

import com.google.api.client.util.Strings;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleBigQueryConnectionFactory {

    private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryConnectionFactory.class);

    private String credentialsFileLocation;
    private String serviceURL;
    private BigQuery client;

    public GoogleBigQueryConnectionFactory() {
    }

    public GoogleBigQueryConnectionFactory(BigQuery client) {
        this.client = client;
    }

    public synchronized BigQuery getDefaultClient() throws Exception {
        if (this.client == null) {
            this.client = buildClient();
        }
        return this.client;
    }

    private BigQuery buildClient() throws Exception {

        GoogleCredentials credentials = null;

        if (credentials == null && !Strings.isNullOrEmpty(credentialsFileLocation)) {
            logger.debug("Key File Name has been set explicitly. Initialising BigQuery using Key File {}",
                    credentialsFileLocation);

            credentials = createFromFile();
        }

        if (credentials == null) {
            logger.debug(
                    "No explicit Service Account or Key File Name have been provided. Initialising BigQuery using defaults");

            credentials = createDefault();
        }

        BigQueryOptions.Builder builder = BigQueryOptions.newBuilder()
                .setCredentials(credentials);

        if (ObjectHelper.isNotEmpty(serviceURL)) {
            builder.setHost(serviceURL);
        }

        return builder.build().getService();
    }

    private GoogleCredentials createFromFile() throws Exception {
        try (InputStream is = new FileInputStream(credentialsFileLocation)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is);

            if (credentials.createScopedRequired()) {
                credentials = credentials.createScoped(BigqueryScopes.all());
            }

            return credentials;
        }
    }

    private GoogleCredentials createDefault() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        Collection<String> scopes = Collections.singletonList(BigqueryScopes.BIGQUERY);

        if (credentials.createScopedRequired()) {
            credentials = credentials.createScoped(scopes);
        }

        return credentials;
    }

    public String getCredentialsFileLocation() {
        return credentialsFileLocation;
    }

    public GoogleBigQueryConnectionFactory setCredentialsFileLocation(String credentialsFileLocation) {
        this.credentialsFileLocation = credentialsFileLocation;
        resetClient();
        return this;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public GoogleBigQueryConnectionFactory setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
        resetClient();
        return this;
    }

    private synchronized void resetClient() {
        this.client = null;
    }
}
