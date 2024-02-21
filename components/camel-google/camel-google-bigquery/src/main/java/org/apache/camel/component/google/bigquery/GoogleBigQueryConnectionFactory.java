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

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

import com.google.api.client.util.Strings;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleBigQueryConnectionFactory {

    private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryConnectionFactory.class);

    private String serviceAccountKeyFile;
    private String serviceURL;
    private BigQuery client;
    private CamelContext camelContext;

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

        if (!Strings.isNullOrEmpty(serviceAccountKeyFile)) {
            logger.debug("Key File Name has been set explicitly. Initialising BigQuery using Key File {}",
                    // limit the output as the value could be a long base64 string, we don't want to show it whole
                    StringHelper.limitLength(serviceAccountKeyFile, 70));

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
        if (camelContext == null) {
            throw new CamelException("CamelContext is null, but must be set when creating GoogleBigQueryConnectionFactory.");
        }
        try (InputStream is
                = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, serviceAccountKeyFile);) {
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

    private synchronized void resetClient() {
        this.client = null;
    }

    public GoogleBigQueryConnectionFactory setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        return this;
    }
}
