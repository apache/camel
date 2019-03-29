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

import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleBigQuerySQLConfiguration {

    @UriParam(description = "ConnectionFactory to obtain connection to Bigquery Service. If non provided the default one will be used")
    private GoogleBigQueryConnectionFactory connectionFactory;
    @UriPath(label = "common", description = "Google Cloud Project Id")
    @Metadata(required = true)
    private String projectId;
    @UriPath(label = "common", description = "BigQuery standard SQL query")
    @Metadata(required = true)
    private String query;

    public void parseRemaining(String remaining) {
        int indexOfColon = remaining.indexOf(":");

        if (indexOfColon < 0) {
            throw new IllegalArgumentException("Google BigQuery Endpoint format \"projectId:query\"");
        }

        projectId = remaining.substring(0, indexOfColon);
        query = remaining.substring(indexOfColon + 1);
    }

    /**
     * ConnectionFactory to obtain connection to Bigquery Service. If non
     * provided the default will be used.
     */
    public GoogleBigQueryConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(GoogleBigQueryConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getQuery() {
        return query;
    }

    public GoogleBigQuerySQLConfiguration setQuery(String query) {
        this.query = query;
        return this;
    }

    public String getProjectId() {
        return projectId;
    }

    public GoogleBigQuerySQLConfiguration setProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }
}
