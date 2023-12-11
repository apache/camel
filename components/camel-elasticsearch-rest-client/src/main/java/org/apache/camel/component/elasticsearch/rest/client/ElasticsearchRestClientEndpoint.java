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
package org.apache.camel.component.elasticsearch.rest.client;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.elasticsearch.client.RestClient;

/**
 * Perform queries and other operations on Elasticsearch or OpenSearch (uses low-level client).
 */
@UriEndpoint(firstVersion = "4.3.0", scheme = "elasticsearch-rest-client",
             title = "Elasticsearch Low level Rest Client",
             syntax = "elasticsearch-rest-client:clusterName", producerOnly = true,
             category = { Category.SEARCH })
public class ElasticsearchRestClientEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String clusterName;
    @UriParam
    @Metadata(required = true)
    ElasticsearchRestClientOperation operation;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    RestClient restClient;

    @UriParam
    String indexName;
    @UriParam
    String hostAddressesList;

    @UriParam(defaultValue = "" + ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT)
    private int connectionTimeout = ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT;
    @UriParam(defaultValue = "" + ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT)
    private int socketTimeout = ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT;

    @UriParam(label = "security", secret = true)
    private String user;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "security")
    @Metadata(supportFileReference = true)
    private String certificatePath;

    @UriParam(label = "advanced")
    private boolean enableSniffer;
    @UriParam(label = "advanced", defaultValue = "" + ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY)
    private int snifferInterval = ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY;
    @UriParam(label = "advanced", defaultValue = "" + ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY)
    private int sniffAfterFailureDelay = ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY;

    public ElasticsearchRestClientEndpoint(String uri, ElasticsearchRestClientComponent component) {
        super(uri, component);
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        ObjectHelper.notNull(operation, "operation");
    }

    public Producer createProducer() throws Exception {
        return new ElasticsearchRestClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }

    /**
     * Cluster Name
     */
    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * Operation
     */
    public ElasticsearchRestClientOperation getOperation() {
        return operation;
    }

    public void setOperation(ElasticsearchRestClientOperation operation) {
        this.operation = operation;
    }

    /**
     * Rest Client of type org.elasticsearch.client.RestClient. This is only for advanced usage
     */
    public RestClient getRestClient() {
        return restClient;
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Index Name
     */
    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * List of host Addresses, multiple hosts can be separated by comma.
     */
    public String getHostAddressesList() {
        return hostAddressesList;
    }

    public void setHostAddressesList(String hostAddressesList) {
        this.hostAddressesList = hostAddressesList;
    }

    /**
     * Connection timeout
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Socket timeout
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    /**
     * Username
     */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Password
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Certificate Path
     */
    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    /**
     * Enabling Sniffer
     */
    public boolean isEnableSniffer() {
        return enableSniffer;
    }

    public void setEnableSniffer(boolean enableSniffer) {
        this.enableSniffer = enableSniffer;
    }

    /**
     * Sniffer interval (in millis)
     */
    public int getSnifferInterval() {
        return snifferInterval;
    }

    public void setSnifferInterval(int snifferInterval) {
        this.snifferInterval = snifferInterval;
    }

    /**
     * Sniffer after failure delay (in millis)
     */
    public int getSniffAfterFailureDelay() {
        return sniffAfterFailureDelay;
    }

    public void setSniffAfterFailureDelay(int sniffAfterFailureDelay) {
        this.sniffAfterFailureDelay = sniffAfterFailureDelay;
    }
}
