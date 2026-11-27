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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleBigQuerySQLConfiguration implements Cloneable {

    @UriPath(label = "common", description = "The GoogleSQL (standard SQL) query or statement to run, or a resource with the"
                                             + " query, such as classpath:query.sql, loaded when the producer starts. A named"
                                             + " query parameter takes its value from the entry of that name of a Map body, else"
                                             + " from the header of that name, and a ${name} placeholder is replaced by the text"
                                             + " of the header or exchange property of that name.")
    @Metadata(required = true, supportFileReference = true, largeInput = true)
    private String queryString;

    @UriParam(description = "The org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory that provides the"
                            + " BigQuery client. If not set, the connectionFactory of the component is used, and if that is not"
                            + " set either, the endpoint creates one from the serviceAccountKey and projectId options.")
    @Metadata(autowired = true)
    private GoogleBigQueryConnectionFactory connectionFactory;

    @UriPath(label = "common",
             description = "The ID of the Google Cloud project that runs the query jobs. The endpoint also sets"
                           + " it as the project of the BigQuery client that it creates.")
    @Metadata(required = true)
    private String projectId;

    @UriParam(label = "security",
              description = "The location of the JSON key file of the service account to authenticate with Google Cloud, such"
                            + " as a classpath:, file: or https: resource, or base64: followed by the Base64 encoded content of"
                            + " the file. If not set, Application Default Credentials are used. It is not used when a"
                            + " connectionFactory is set.")
    private String serviceAccountKey;

    @UriParam(label = "producer", description = "The token of the page of SELECT results to return, from the"
                                                + " CamelGoogleBigQueryNextPageToken header of a previous call. If not set, the"
                                                + " first page is returned. The CamelGoogleBigQueryPageToken header replaces it.")
    private String pageToken;

    @UriParam(label = "producer", description = "The maximum number of rows in a page of SELECT results. With 0 or less,"
                                                + " BigQuery chooses the page size.",
              defaultValue = "0")
    private long pageSize;

    @UriParam(label = "producer",
              description = "How the producer returns the rows of a SELECT. SELECT_LIST returns one page as a"
                            + " List of Maps, with the CamelGoogleBigQueryNextPageToken and"
                            + " CamelGoogleBigQueryJobId headers. STREAM_LIST returns an Iterator of Maps that"
                            + " fetches all the pages while it is read, and sets no header.",
              defaultValue = "SELECT_LIST")
    private OutputType outputType = OutputType.SELECT_LIST;

    public void parseRemaining(String remaining) {
        int indexOfColon = remaining.indexOf(':');

        if (indexOfColon < 0) {
            throw new IllegalArgumentException("Google BigQuery Endpoint format \"projectId:query\"");
        }

        projectId = remaining.substring(0, indexOfColon);
        queryString = remaining.substring(indexOfColon + 1);
    }

    public GoogleBigQueryConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(GoogleBigQueryConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getQueryString() {
        return queryString;
    }

    public GoogleBigQuerySQLConfiguration setQueryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    public String getProjectId() {
        return projectId;
    }

    public GoogleBigQuerySQLConfiguration setProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public GoogleBigQuerySQLConfiguration setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
        return this;
    }

    public String getPageToken() {
        return pageToken;
    }

    public GoogleBigQuerySQLConfiguration setPageToken(String pageToken) {
        this.pageToken = pageToken;
        return this;
    }

    public long getPageSize() {
        return pageSize;
    }

    public GoogleBigQuerySQLConfiguration setPageSize(long pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public GoogleBigQuerySQLConfiguration setOutputType(OutputType outputType) {
        this.outputType = outputType;
        return this;
    }

    public GoogleBigQuerySQLConfiguration copy() {
        try {
            return (GoogleBigQuerySQLConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
