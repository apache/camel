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
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.http.HttpHost;

@UriParams
public class ElasticsearchConfiguration {

    private List<HttpHost> hostAddressesList;
    private String user;
    private String password;
    private String apiKey;

    @UriPath(description = "The name of the Elasticsearch cluster. The producer uses it only in its log messages: the"
                           + " hostAddresses option or the client option of the component sets the nodes to connect to.")
    @Metadata(required = true)
    private String clusterName;
    @UriParam(description = "The operation that the producer performs. The CamelElasticsearchOperation header replaces this"
                            + " option. A request of the Elasticsearch Java API Client in the body, such as an IndexRequest or"
                            + " a SearchRequest, sets the operation by its type and replaces both.")
    private ElasticsearchOperation operation;
    @UriParam(description = "The maximum number of hits that Search returns when the body is JSON text or a Map. It replaces"
                            + " the size of the body, and the CamelElasticsearchSize header replaces this option.")
    private Integer size;
    @UriParam(description = "The offset of the first hit that Search returns when the body is JSON text or a Map. It replaces"
                            + " the from of the body, and the CamelElasticsearchFrom header replaces this option.")
    private Integer from;
    @UriParam(description = "The name of the index that the operation acts on. Bulk uses it as the default index of its"
                            + " operations, and DeleteIndex deletes this index instead of the one named by the body. The"
                            + " CamelElasticsearchIndexName header replaces this option, and a request of the Elasticsearch Java"
                            + " API Client in the body keeps its own index.")
    private String indexName;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_FOR_WAIT_ACTIVE_SHARDS,
              description = "The number of active shard copies that Index and Update wait for before they write. The default"
                            + " of 1 waits only for the primary shard. The CamelElasticsearchWaitForActiveShards header replaces"
                            + " this option.")
    private int waitForActiveShards = ElasticsearchConstants.DEFAULT_FOR_WAIT_ACTIVE_SHARDS;
    @UriParam(description = "The comma-separated list of the nodes to connect to, each as host:port, for example"
                            + " es1:9200,es2:9200. The port is 9200 if not set, and the scheme is https when enableSSL is set."
                            + " Required unless the client option of the component is set.")
    private String hostAddresses;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT,
              description = "The socket timeout in milliseconds: the longest time that the client waits for data from a node"
                            + " before the request fails. Not used when the client option of the component is set.")
    private int socketTimeout = ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT;
    @Deprecated
    @UriParam(defaultValue = "" + ElasticsearchConstants.MAX_RETRY_TIMEOUT,
              description = "Deprecated: this option has no effect. It was used by the old low-level REST client and is"
                            + " ignored by the current client.")
    private int maxRetryTimeout = ElasticsearchConstants.MAX_RETRY_TIMEOUT;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT,
              description = "The connect timeout in milliseconds: the longest time that the client waits to establish the"
                            + " connection to a node. Not used when the client option of the component is set.")
    private int connectionTimeout = ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT;
    @UriParam(defaultValue = "false",
              description = "Whether the body of Update is only the partial document to merge into the document. If false,"
                            + " the body is the request body of the Update API, such as {\"doc\": {...}} or a script. Only JSON"
                            + " text, bytes, a stream, a file or a Map is read this way. The CamelElasticsearchEnableDocumentOnlyMode"
                            + " header replaces this option.")
    private boolean enableDocumentOnlyMode;
    @UriParam(description = "Whether the producer creates the client for each exchange and closes it when the exchange is"
                            + " done, instead of keeping one client from its start to its stop. Not used when the client"
                            + " option of the component is set.")
    private boolean disconnect;
    @UriParam(label = "security",
              description = "Whether to connect to the nodes of hostAddresses with HTTPS instead of HTTP. The default"
                            + " SSLContext of the JVM is used unless sslContextParameters or certificatePath is set.")
    private boolean enableSSL;
    @UriParam(label = "security",
              description = "The CA certificate, in X.509 format, that the client trusts for HTTPS instead of the default"
                            + " trust store of the JVM. It is loaded from the classpath by default, or with the prefix"
                            + " classpath:, file: or http:. It needs enableSSL, and sslContextParameters takes precedence.")
    @Metadata(supportFileReference = true)
    private String certificatePath;
    @UriParam(label = "security",
              description = "To configure security using SSLContextParameters, which sets the protocols, cipher suites, named"
                            + " groups and signature schemes of the HTTPS connection. It takes precedence over certificatePath,"
                            + " and it needs enableSSL.")
    private SSLContextParameters sslContextParameters;
    @UriParam(description = "Whether Search reads all the hits with the scroll API. The body is then an"
                            + " ElasticsearchScrollRequestIterator of the hits, which sends the scroll requests while it is"
                            + " read and is to be closed after use. The CamelElasticsearchUseScroll header replaces this option.")
    private boolean useScroll;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_SCROLL_KEEP_ALIVE_MS,
              description = "The time in milliseconds that Elasticsearch keeps the search context of a scroll alive between"
                            + " two scroll requests, when useScroll is set. The CamelElasticsearchScrollKeepAliveMs header"
                            + " replaces this option.")
    private int scrollKeepAliveMs = ElasticsearchConstants.DEFAULT_SCROLL_KEEP_ALIVE_MS;
    @UriParam(label = "advanced",
              description = "Whether to start the Sniffer of the Elasticsearch REST client, which reads the nodes of the"
                            + " cluster from its Nodes Info API at an interval and updates the nodes that the client uses. Not used"
                            + " when the client option of the component is set or autowired, as Spring Boot does.")
    private boolean enableSniffer;
    @UriParam(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL,
              description = "The interval in milliseconds between two reads of the nodes of the cluster, when enableSniffer"
                            + " is set.")
    private int snifferInterval = ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL;
    @UriParam(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY,
              description = "The delay in milliseconds of the read of the nodes that the Sniffer schedules after a failed"
                            + " request. The component does not register the client listener that reports failures to the"
                            + " Sniffer, so this option has no effect.")
    private int sniffAfterFailureDelay = ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY;
    @UriParam(label = "advanced", defaultValue = "ObjectNode",
              description = "The class to which the documents in the responses of GetById, MultiGet, Search and MultiSearch"
                            + " are deserialized. The default is the Jackson ObjectNode. The CamelElasticsearchDocumentClass"
                            + " header replaces this option.")
    private Class<?> documentClass = ObjectNode.class;

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public ElasticsearchOperation getOperation() {
        return operation;
    }

    public void setOperation(ElasticsearchOperation operation) {
        this.operation = operation;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getHostAddresses() {
        return hostAddresses;
    }

    public void setHostAddresses(String hostAddresses) {
        this.hostAddresses = hostAddresses;
    }

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

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    /**
     * @deprecated this option has no effect and is ignored by the current client
     */
    @Deprecated
    public int getMaxRetryTimeout() {
        return maxRetryTimeout;
    }

    @Deprecated
    public void setMaxRetryTimeout(int maxRetryTimeout) {
        this.maxRetryTimeout = maxRetryTimeout;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isEnableSniffer() {
        return enableSniffer;
    }

    public void setEnableSniffer(boolean enableSniffer) {
        this.enableSniffer = enableSniffer;
    }

    public int getSnifferInterval() {
        return snifferInterval;
    }

    public void setSnifferInterval(int snifferInterval) {
        this.snifferInterval = snifferInterval;
    }

    public int getSniffAfterFailureDelay() {
        return sniffAfterFailureDelay;
    }

    public void setSniffAfterFailureDelay(int sniffAfterFailureDelay) {
        this.sniffAfterFailureDelay = sniffAfterFailureDelay;
    }

    public boolean isUseScroll() {
        return useScroll;
    }

    public void setUseScroll(boolean useScroll) {
        this.useScroll = useScroll;
    }

    public int getScrollKeepAliveMs() {
        return scrollKeepAliveMs;
    }

    public void setScrollKeepAliveMs(int scrollKeepAliveMs) {
        this.scrollKeepAliveMs = scrollKeepAliveMs;
    }

    public Class<?> getDocumentClass() {
        return documentClass;
    }

    public void setDocumentClass(Class<?> documentClass) {
        this.documentClass = documentClass;
    }

    public boolean isEnableDocumentOnlyMode() {
        return enableDocumentOnlyMode;
    }

    public void setEnableDocumentOnlyMode(boolean enableDocumentOnlyMode) {
        this.enableDocumentOnlyMode = enableDocumentOnlyMode;
    }
}
