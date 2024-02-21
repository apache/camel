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

import com.google.cloud.bigquery.BigQuery;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.google.bigquery.GoogleBigQueryConstants.SCHEME_BIGQUERY_SQL;

/**
 * Access Google Cloud BigQuery service using SQL queries.
 *
 * BigQuery Endpoint Definition Represents a table within a BigQuery dataset Contains configuration details for a single
 * table and the utility methods (such as check, create) to ease operations URI Parameters: * Logger ID - To ensure that
 * logging is unified under Route Logger, the logger ID can be passed on via an endpoint URI parameter * Partitioned -
 * to indicate that the table needs to be partitioned - every UTC day to be written into a timestamped separate table
 * side effect: Australian operational day is always split between two UTC days, and, therefore, tables
 *
 * Another consideration is that exceptions are not handled within the class. They are expected to bubble up and be
 * handled by Camel.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = SCHEME_BIGQUERY_SQL, title = "Google BigQuery Standard SQL",
             syntax = "google-bigquery-sql:projectId:queryString", category = { Category.CLOUD, Category.MESSAGING },
             producerOnly = true,
             headersClass = GoogleBigQueryConstants.class)
public class GoogleBigQuerySQLEndpoint extends DefaultEndpoint {

    @UriParam
    protected final GoogleBigQuerySQLConfiguration configuration;

    private BigQuery bigQuery;

    protected GoogleBigQuerySQLEndpoint(String endpointUri, GoogleBigQuerySQLComponent component,
                                        GoogleBigQuerySQLConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        return new GoogleBigQuerySQLProducer(bigQuery, this, configuration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        GoogleBigQueryConnectionFactory connFactory = configuration.getConnectionFactory();
        if (connFactory == null) {
            connFactory = new GoogleBigQueryConnectionFactory()
                    .setCamelContext(getCamelContext())
                    .setServiceAccountKeyFile(configuration.getServiceAccountKey());
            configuration.setConnectionFactory(connFactory);
        }
        bigQuery = connFactory.getDefaultClient();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from the BigQuery endpoint: " + getEndpointUri());
    }

    public GoogleBigQuerySQLConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public GoogleBigQuerySQLComponent getComponent() {
        return (GoogleBigQuerySQLComponent) super.getComponent();
    }

}
