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
package org.apache.camel.component.google.bigquery.sql;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("google-bigquery-sql")
public class GoogleBigQuerySQLComponent extends DefaultComponent {
    private String projectId;
    private GoogleBigQueryConnectionFactory connectionFactory;

    public GoogleBigQuerySQLComponent() {
    }

    public GoogleBigQuerySQLComponent(CamelContext camelContext) {
        super(camelContext);
    }

    // Endpoint represents a single table
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String[] parts = remaining.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Google BigQuery Endpoint format \"projectId:<query>\"");
        }

        GoogleBigQuerySQLConfiguration configuration = new GoogleBigQuerySQLConfiguration();
        configuration.parseRemaining(remaining);

        if (configuration.getConnectionFactory() == null) {
            configuration.setConnectionFactory(getConnectionFactory());
        }

        GoogleBigQuerySQLEndpoint endpoint = new GoogleBigQuerySQLEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public String getProjectId() {
        return projectId;
    }

    /**
     * Google Cloud Project Id
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public GoogleBigQueryConnectionFactory getConnectionFactory() {
        if (connectionFactory == null) {
            connectionFactory = new GoogleBigQueryConnectionFactory();
        }
        return connectionFactory;
    }

    /**
     * ConnectionFactory to obtain connection to Bigquery Service. If non
     * provided the default one will be used
     */
    public void setConnectionFactory(GoogleBigQueryConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
}
