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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.elasticsearch.client.RestClient;

/**
 * Perform queries and other operations on Elasticsearch or OpenSearch (uses low-level client).
 */
@UriEndpoint(firstVersion = "4.3.0", scheme = "elasticsearch-rest-client",
             title = "Elasticsearch Low level Rest Client",
             syntax = "elasticsearch-rest-client:clusterName", producerOnly = true,
             category = { Category.SEARCH }, headersClass = ElasticSearchRestClientConstant.class)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.NULL },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "INDEX_OR_UPDATE",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "GET_BY_ID",
                                                         carriers = { Carrier.TEXT, Carrier.VALUE, Carrier.NULL }),
                                          @BodyType.When(option = "operation", value = "DELETE",
                                                         carriers = { Carrier.TEXT, Carrier.VALUE, Carrier.NULL }),
                                          @BodyType.When(option = "operation", value = "SEARCH",
                                                         carriers = { Carrier.MAP, Carrier.NULL }),
                                          @BodyType.When(option = "operation", value = "CREATE_INDEX", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "DELETE_INDEX",
                                                         carriers = Carrier.ANY) },
                                  description = "By the operation, set by the operation option or the CamelElasticsearchOperation"
                                                + " header. INDEX_OR_UPDATE sends the body as the JSON document: text, bytes, a"
                                                + " stream or a file of it; a Map or a POJO is sent as its toString() and"
                                                + " fails, so marshal it to JSON first. GET_BY_ID and DELETE take the id as"
                                                + " the body, or from the CamelElasticsearchId header when the body is empty."
                                                + " SEARCH takes a Map of field names and values to match, or else the JSON"
                                                + " query of the CamelElasticsearchSearchQuery header; a JSON text body is"
                                                + " not used, and with neither all documents match. CREATE_INDEX and"
                                                + " DELETE_INDEX do not use the body: the settings of a new index are in the"
                                                + " CamelElasticsearchIndexSettings header"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "INDEX_OR_UPDATE",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "GET_BY_ID", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "SEARCH", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "DELETE", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "CREATE_INDEX",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "DELETE_INDEX",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean") },
                                   description = "The id of the document as text for INDEX_OR_UPDATE. GET_BY_ID gives the"
                                                 + " source of the document as JSON text, and SEARCH a JSON array of the"
                                                 + " sources of the hits as text: unmarshal them to read their fields. DELETE,"
                                                 + " CREATE_INDEX and DELETE_INDEX give true. A missing id or index, or an"
                                                 + " index that is already there, fails with the error of the server: a"
                                                 + " GET_BY_ID of a missing id fails with 404 rather than giving null"))
public class ElasticsearchRestClientEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath
    @Metadata(required = true)
    private String clusterName;
    @UriParam
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

    @UriParam(label = "security", security = "secret")
    private String user;
    @UriParam(label = "security", security = "secret")
    private String password;
    @UriParam(label = "security")
    @Metadata(supportFileReference = true)
    private String certificatePath;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

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
    }

    public Producer createProducer() throws Exception {
        return new ElasticsearchRestClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }

    @Override
    public String getServiceUrl() {
        return getHostAddressesList();
    }

    @Override
    public String getServiceProtocol() {
        return "elasticsearch";
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

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters. When configured, this takes precedence over the
     * {@code certificatePath} option. This allows configuring named groups, signature schemes, cipher suites, and
     * protocols for the TLS connection.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
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
