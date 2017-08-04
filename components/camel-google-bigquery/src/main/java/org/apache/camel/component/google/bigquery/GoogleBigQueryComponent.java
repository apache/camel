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

package org.apache.camel.component.google.bigquery;

import java.util.Map;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.bigquery.Bigquery;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

public class GoogleBigQueryComponent extends DefaultComponent {
    private String projectId;
    private String datasetId;

    private GoogleBigQueryConnectionFactory connectionFactory;

    public GoogleBigQueryComponent() {
        super();
    }

    public GoogleBigQueryComponent(CamelContext camelContext) {
        super(camelContext);
    }

    // Endpoint represents a single table
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String[] parts = remaining.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Google BigQuery Endpoint format \"projectId:datasetId:tableName\"");
        }

        GoogleBigQueryConfiguration configuration = new GoogleBigQueryConfiguration();
        setProperties(configuration, parameters);
        configuration.parseRemaining(remaining);

        if (configuration.getConnectionFactory() == null) {
            configuration.setConnectionFactory(getConnectionFactory());
        }

        return new GoogleBigQueryEndpoint(uri, this, configuration);
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    /**
     * Sets the connection factory to use:
     * provides the ability to explicitly manage connection credentials:
     * - the path to the key file
     * - the Service Account Key / Email pair
     */
    public GoogleBigQueryConnectionFactory getConnectionFactory() {
        if (connectionFactory == null) {
            connectionFactory = new GoogleBigQueryConnectionFactory();
        }
        return connectionFactory;
    }

    public void setConnectionFactory(GoogleBigQueryConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
}
