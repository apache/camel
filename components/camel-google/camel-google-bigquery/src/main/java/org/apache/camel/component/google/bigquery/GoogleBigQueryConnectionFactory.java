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

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.api.services.bigquery.BigqueryScopes;
import com.google.auth.Credentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.common.GoogleCommonConfiguration;
import org.apache.camel.component.google.common.GoogleCredentialsHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleBigQueryConnectionFactory {

    private static final Collection<String> BIGQUERY_SCOPES = Collections.singletonList(BigqueryScopes.BIGQUERY);

    private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryConnectionFactory.class);

    private String serviceAccountKeyFile;
    private String serviceURL;
    private String projectId;
    private BigQuery client;
    private CamelContext camelContext;
    private final Lock lock = new ReentrantLock();

    public GoogleBigQueryConnectionFactory() {
    }

    public GoogleBigQueryConnectionFactory(BigQuery client) {
        this.client = client;
    }

    public BigQuery getDefaultClient() throws Exception {
        lock.lock();
        try {
            if (this.client == null) {
                this.client = buildClient();
            }
            return this.client;
        } finally {
            lock.unlock();
        }
    }

    private BigQuery buildClient() throws Exception {
        Credentials credentials = null;

        if (ObjectHelper.isNotEmpty(serviceAccountKeyFile)) {
            logger.debug("Key File Name has been set explicitly. Initialising BigQuery using Key File {}",
                    // limit the output as the value could be a long base64 string, we don't want to show it whole
                    StringHelper.limitLength(serviceAccountKeyFile, 70));

            // Create a simple configuration adapter to use GoogleCredentialsHelper
            GoogleCommonConfiguration config = createConfigAdapter();
            credentials = GoogleCredentialsHelper.getCredentials(camelContext, config, BIGQUERY_SCOPES);
        }

        if (credentials == null) {
            logger.debug(
                    "No explicit Service Account or Key File Name have been provided. Initialising BigQuery using defaults");

            // Use GoogleCredentialsHelper with null config to get ADC
            credentials = GoogleCredentialsHelper.getCredentials(camelContext, createEmptyConfigAdapter(), BIGQUERY_SCOPES);
        }

        BigQueryOptions.Builder builder = BigQueryOptions.newBuilder()
                .setCredentials(credentials);

        if (ObjectHelper.isNotEmpty(projectId)) {
            builder.setProjectId(projectId);
        }

        if (ObjectHelper.isNotEmpty(serviceURL)) {
            builder.setHost(serviceURL);
        }

        return builder.build().getService();
    }

    /**
     * Creates a configuration adapter for GoogleCredentialsHelper using the factory's serviceAccountKeyFile.
     */
    private GoogleCommonConfiguration createConfigAdapter() {
        final String keyFile = this.serviceAccountKeyFile;
        return new GoogleCommonConfiguration() {
            @Override
            public String getServiceAccountKey() {
                return keyFile;
            }
        };
    }

    /**
     * Creates an empty configuration adapter (no service account key) for ADC fallback.
     */
    private GoogleCommonConfiguration createEmptyConfigAdapter() {
        return new GoogleCommonConfiguration() {
            @Override
            public String getServiceAccountKey() {
                return null;
            }
        };
    }

    public String getServiceAccountKeyFile() {
        return serviceAccountKeyFile;
    }

    public GoogleBigQueryConnectionFactory setServiceAccountKeyFile(String serviceAccountKeyFile) {
        this.serviceAccountKeyFile = serviceAccountKeyFile;
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

    public String getProjectId() {
        return projectId;
    }

    public GoogleBigQueryConnectionFactory setProjectId(String projectId) {
        this.projectId = projectId;
        resetClient();
        return this;
    }

    private void resetClient() {
        lock.lock();
        try {
            this.client = null;
        } finally {
            lock.unlock();
        }
    }

    public GoogleBigQueryConnectionFactory setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        return this;
    }
}
