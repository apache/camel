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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.common.GoogleCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleBigQueryConfiguration implements Cloneable, GoogleCommonConfiguration {

    @UriParam(description = "The org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory that provides the"
                            + " BigQuery client. If not set, the connectionFactory of the component is used, and if that is not"
                            + " set either, the endpoint creates one from the serviceAccountKey and projectId options. With a"
                            + " factory that is set, the rows go to the dataset in the default project of its client.")
    @Metadata(autowired = true)
    private GoogleBigQueryConnectionFactory connectionFactory;

    @UriParam(description = "The name of the field of each row whose value is the insert ID of the row, which BigQuery uses to"
                            + " drop duplicate rows on a best-effort basis. It applies to a Map body and to each Map of a List"
                            + " body. When set, the CamelGoogleBigQueryInsertId header is not used.")
    private String useAsInsertId;

    @UriPath(label = "common", description = "The ID of the Google Cloud project of the dataset. The endpoint sets it as the"
                                             + " project of the BigQuery client that it creates.")
    @Metadata(required = true)
    private String projectId;

    @UriPath(label = "common", description = "The ID of the BigQuery dataset that holds the table.")
    @Metadata(required = true)
    private String datasetId;

    @UriPath(label = "common",
             description = "The ID of the table to insert the rows into. The CamelGoogleBigQueryTableId header"
                           + " replaces it. If neither is set, the exchange fails.")
    @Metadata(required = false)
    private String tableId;

    @UriParam(label = "security",
              description = "The location of the JSON key file of the service account to authenticate with Google Cloud, such"
                            + " as a classpath:, file: or https: resource, or base64: followed by the Base64 encoded content of"
                            + " the file. If not set, Application Default Credentials are used. It is not used when a"
                            + " connectionFactory is set.")
    @Metadata(required = false)
    private String serviceAccountKey;

    public void parseRemaining(String remaining) {
        String[] parts = remaining.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Google BigQuery Endpoint format \"projectId:datasetId[:tableName]\"");
        }

        projectId = parts[0];
        datasetId = parts[1];
        if (parts.length > 2) {
            tableId = parts[2];
        }
    }

    public GoogleBigQueryConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(GoogleBigQueryConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getUseAsInsertId() {
        return useAsInsertId;
    }

    public GoogleBigQueryConfiguration setUseAsInsertId(String useAsInsertId) {
        this.useAsInsertId = useAsInsertId;
        return this;
    }

    public String getProjectId() {
        return projectId;
    }

    public GoogleBigQueryConfiguration setProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public GoogleBigQueryConfiguration setDatasetId(String datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public String getTableId() {
        return tableId;
    }

    public GoogleBigQueryConfiguration setTableId(String tableId) {
        this.tableId = tableId;
        return this;
    }

    @Override
    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public GoogleBigQueryConfiguration setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
        return this;
    }

    public GoogleBigQueryConfiguration copy() {
        try {
            return (GoogleBigQueryConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
