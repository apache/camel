/**
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
package org.apache.camel.component.elasticsearch;

import java.io.File;
import java.util.List;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

@UriParams
public class ElasticsearchConfiguration {

    private boolean local;
    private List<InetSocketTransportAddress> transportAddressesList;

    @UriPath @Metadata(required = "true")
    private String clusterName;
    @UriParam(enums = "INDEX,UPDATE,BULK,BULK_INDEX,GET_BY_ID,MULTIGET,DELETE,EXISTS,SEARCH,MULTISEARCH,DELETE_INDEX")
    private String operation;
    @UriParam
    private String indexName;
    @UriParam
    private String indexType;
    @UriParam(defaultValue = "DEFAULT")
    private WriteConsistencyLevel consistencyLevel = ElasticsearchConstants.DEFAULT_CONSISTENCY_LEVEL;
    @UriParam
    private Boolean data;
    @UriParam
    private String ip;
    @UriParam
    private String transportAddresses;
    @UriParam(defaultValue = "9300")
    private int port = ElasticsearchConstants.DEFAULT_PORT;
    @UriParam(defaultValue = "true")
    private Boolean clientTransportSniff = true;
    @UriParam(defaultValue = "${user.home}/.elasticsearch")
    private String pathHome = System.getProperty("user.home") + File.separator + ".elasticsearch";

    /**
     * Name of cluster or use local for local mode
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
    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
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
     * The type of the index to act against
     */
    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    /**
     * The write consistency level to use with INDEX and BULK operations (can be any of ONE, QUORUM, ALL or DEFAULT)
     */
    public WriteConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(WriteConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }
    
    /**
     * Is the node going to be allowed to allocate data (shards) to it or not. This setting map to the <tt>node.data</tt> setting.
     */
    public Boolean getData() {
        return data;
    }

    public void setData(Boolean data) {
        this.data = data;
    }

    /**
     * The TransportClient remote host ip to use
     */
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * Comma separated list with ip:port formatted remote transport addresses to use.
     * The ip and port options must be left blank for transportAddresses to be considered instead.
     */
    public String getTransportAddresses() {
        return transportAddresses;
    }

    public void setTransportAddresses(String transportAddresses) {
        this.transportAddresses = transportAddresses;
    }

    /**
     * The TransportClient remote port to use (defaults to 9300)
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Is the client allowed to sniff the rest of the cluster or not (default true). This setting map to the <tt>client.transport.sniff</tt> setting.
     */
    public Boolean getClientTransportSniff() {
        return clientTransportSniff;
    }

    public void setClientTransportSniff(Boolean clientTransportSniff) {
        this.clientTransportSniff = clientTransportSniff;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public List<InetSocketTransportAddress> getTransportAddressesList() {
        return transportAddressesList;
    }

    public void setTransportAddressesList(List<InetSocketTransportAddress> transportAddressesList) {
        this.transportAddressesList = transportAddressesList;
    }

    /**
     * The path.home property of ElasticSearch configuration. You need to provide a valid path, otherwise the default, ${user.home}/.elasticsearch, will be used.
     */
    public String getPathHome() {
        return pathHome;
    }

    public void setPathHome(String pathHome) {
        this.pathHome = pathHome;
    }
}
