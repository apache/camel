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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("google-bigquery")
public class GoogleBigQueryComponent extends DefaultComponent {

    @Metadata(description = "The ID of a Google Cloud project. Not used: an endpoint takes the project ID from its URI.")
    private String projectId;

    @Metadata(description = "The ID of a BigQuery dataset. Not used: an endpoint takes the dataset ID from its URI.")
    private String datasetId;

    @Metadata
    private GoogleBigQueryConfiguration configuration;

    @Metadata(description = "The org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory that provides the"
                            + " BigQuery client for the endpoints that do not set their own connectionFactory.",
              autowired = true)
    private GoogleBigQueryConnectionFactory connectionFactory;

    public GoogleBigQueryComponent() {
    }

    public GoogleBigQueryComponent(GoogleBigQueryConfiguration configuration) {
        this.configuration = configuration;
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

        GoogleBigQueryConfiguration conf = configuration != null ? configuration.copy() : new GoogleBigQueryConfiguration();
        conf.parseRemaining(remaining);
        if (conf.getConnectionFactory() == null) {
            // the connection factory of the component, unless the endpoint has its own
            conf.setConnectionFactory(connectionFactory);
        }

        GoogleBigQueryEndpoint endpoint = new GoogleBigQueryEndpoint(uri, this, conf);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public GoogleBigQueryConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(GoogleBigQueryConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public GoogleBigQueryConfiguration getConfiguration() {
        return configuration;
    }
}
