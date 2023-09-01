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
package org.apache.camel.component.es;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.http.HttpHost;

@UriParams
public class ElasticsearchConfiguration {

    private List<HttpHost> hostAddressesList;
    private String user;
    private String password;

    @UriPath
    @Metadata(required = true)
    private String clusterName;
    @UriParam
    private ElasticsearchOperation operation;
    @UriParam
    private Integer size;
    @UriParam
    private Integer from;
    @UriParam
    private String indexName;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_FOR_WAIT_ACTIVE_SHARDS)
    private int waitForActiveShards = ElasticsearchConstants.DEFAULT_FOR_WAIT_ACTIVE_SHARDS;
    @UriParam
    private String hostAddresses;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT)
    private int socketTimeout = ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT;
    @UriParam(defaultValue = "" + ElasticsearchConstants.MAX_RETRY_TIMEOUT)
    private int maxRetryTimeout = ElasticsearchConstants.MAX_RETRY_TIMEOUT;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT)
    private int connectionTimeout = ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT;
    @UriParam(defaultValue = "false")
    private boolean enableDocumentOnlyMode;
    @UriParam
    private boolean disconnect;
    @UriParam(label = "security")
    private boolean enableSSL;
    @UriParam(label = "security")
    @Metadata(supportFileReference = true)
    private String certificatePath;
    @UriParam
    private boolean useScroll;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_SCROLL_KEEP_ALIVE_MS)
    private int scrollKeepAliveMs = ElasticsearchConstants.DEFAULT_SCROLL_KEEP_ALIVE_MS;
    @UriParam(label = "advanced")
    private boolean enableSniffer;
    @UriParam(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL)
    private int snifferInterval = ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL;
    @UriParam(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY)
    private int sniffAfterFailureDelay = ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY;
    @UriParam(label = "advanced", defaultValue = "ObjectNode")
    private Class<?> documentClass = ObjectNode.class;

    /**
     * Starting index of the response.
     */
    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    /**
     * Size of the response.
     */
    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    /**
     * Name of the cluster
     */
    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * What operation to perform
     */
    public ElasticsearchOperation getOperation() {
        return operation;
    }

    public void setOperation(ElasticsearchOperation operation) {
        this.operation = operation;
    }

    /**
     * The name of the index to act against
     */
    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * Comma separated list with ip:port formatted remote transport addresses to use.
     */
    public String getHostAddresses() {
        return hostAddresses;
    }

    public void setHostAddresses(String hostAddresses) {
        this.hostAddresses = hostAddresses;
    }

    /**
     * Index creation waits for the write consistency number of shards to be available
     */
    public int getWaitForActiveShards() {
        return waitForActiveShards;
    }

    public void setWaitForActiveShards(int waitForActiveShards) {
        this.waitForActiveShards = waitForActiveShards;
    }

    public List<HttpHost> getHostAddressesList() {
        return hostAddressesList;
    }

    public void setHostAddressesList(List<HttpHost> hostAddressesList) {
        this.hostAddressesList = hostAddressesList;
    }

    /**
     * The timeout in ms to wait before the socket will timeout.
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    /**
     * The time in ms to wait before connection will timeout.
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Basic authenticate user
     */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Password for authenticate
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Enable SSL
     */
    public boolean isEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    /**
     * The certificate that can be used to access the ES Cluster. It can be loaded by default from classpath, but you
     * can prefix with classpath:, file:, or http: to load the resource from different systems.
     */
    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    /**
     * The time in ms before retry
     */
    public int getMaxRetryTimeout() {
        return maxRetryTimeout;
    }

    public void setMaxRetryTimeout(int maxRetryTimeout) {
        this.maxRetryTimeout = maxRetryTimeout;
    }

    /**
     * Disconnect after it finish calling the producer
     */
    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    /**
     * Enable automatically discover nodes from a running Elasticsearch cluster. If this option is used in conjunction
     * with Spring Boot then it's managed by the Spring Boot configuration (see: Disable Sniffer in Spring Boot).
     */
    public boolean isEnableSniffer() {
        return enableSniffer;
    }

    public void setEnableSniffer(boolean enableSniffer) {
        this.enableSniffer = enableSniffer;
    }

    /**
     * The interval between consecutive ordinary sniff executions in milliseconds. Will be honoured when sniffOnFailure
     * is disabled or when there are no failures between consecutive sniff executions
     */
    public int getSnifferInterval() {
        return snifferInterval;
    }

    public void setSnifferInterval(int snifferInterval) {
        this.snifferInterval = snifferInterval;
    }

    /**
     * The delay of a sniff execution scheduled after a failure (in milliseconds)
     */
    public int getSniffAfterFailureDelay() {
        return sniffAfterFailureDelay;
    }

    public void setSniffAfterFailureDelay(int sniffAfterFailureDelay) {
        this.sniffAfterFailureDelay = sniffAfterFailureDelay;
    }

    /**
     * Enable scroll usage
     */
    public boolean isUseScroll() {
        return useScroll;
    }

    public void setUseScroll(boolean useScroll) {
        this.useScroll = useScroll;
    }

    /**
     * Time in ms during which elasticsearch will keep search context alive
     */
    public int getScrollKeepAliveMs() {
        return scrollKeepAliveMs;
    }

    public void setScrollKeepAliveMs(int scrollKeepAliveMs) {
        this.scrollKeepAliveMs = scrollKeepAliveMs;
    }

    /**
     * The class to use when deserializing the documents.
     */
    public Class<?> getDocumentClass() {
        return documentClass;
    }

    public void setDocumentClass(Class<?> documentClass) {
        this.documentClass = documentClass;
    }

    /**
     * Indicates whether the body of the message contains only documents. By default, it is set to false to be able to
     * do the same requests as what the Document API supports (see
     * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs.html for more details). To ease the
     * migration of routes based on the legacy component camel-elasticsearch-rest, you should consider enabling the mode
     * especially if your routes do update operations.
     */
    public boolean isEnableDocumentOnlyMode() {
        return enableDocumentOnlyMode;
    }

    public void setEnableDocumentOnlyMode(boolean enableDocumentOnlyMode) {
        this.enableDocumentOnlyMode = enableDocumentOnlyMode;
    }
}
