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
package org.apache.camel.component.elasticsearch5;

import java.util.List;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

@UriParams
public class ElasticsearchConfiguration {

    private List<InetSocketTransportAddress> transportAddressesList;

    @UriPath @Metadata(required = "true")
    private String clusterName;
    @UriParam
    private ElasticsearchOperation operation;
    @UriParam
    private String indexName;
    @UriParam
    private String indexType;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_FOR_WAIT_ACTIVE_SHARDS)
    private int waitForActiveShards = ElasticsearchConstants.DEFAULT_FOR_WAIT_ACTIVE_SHARDS;
    @UriParam
    private String ip;
    @UriParam
    private String transportAddresses;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_PORT)
    private int port = ElasticsearchConstants.DEFAULT_PORT;
    @UriParam
    private boolean clientTransportSniff;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_PING_SCHEDULE)
    private String pingSchedule = ElasticsearchConstants.DEFAULT_PING_SCHEDULE;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_PING_TIMEOUT)
    private String pingTimeout = ElasticsearchConstants.DEFAULT_PING_TIMEOUT;
    @UriParam(defaultValue = "" + ElasticsearchConstants.DEFAULT_TCP_CONNECT_TIMEOUT)
    private String tcpConnectTimeout = ElasticsearchConstants.DEFAULT_TCP_CONNECT_TIMEOUT;
    @UriParam
    private boolean tcpCompress;
    @UriParam(label = "authentication")
    private String user;
    @UriParam(label = "authentication", secret = true)
    private String password;
    @UriParam(label = "advanced,security")
    private boolean enableSSL;


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
     * The type of the index to act against
     */
    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
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
     * Index creation waits for the write consistency number of shards to be available
     */
    public int getWaitForActiveShards() {
        return waitForActiveShards;
    }

    public void setWaitForActiveShards(int waitForActiveShards) {
        this.waitForActiveShards = waitForActiveShards;
    }

    /**
     * Is the client allowed to sniff the rest of the cluster or not. This setting map to the <tt>client.transport.sniff</tt> setting.
     */
    public boolean getClientTransportSniff() {
        return clientTransportSniff;
    }

    public void setClientTransportSniff(boolean clientTransportSniff) {
        this.clientTransportSniff = clientTransportSniff;
    }

    public List<InetSocketTransportAddress> getTransportAddressesList() {
        return transportAddressesList;
    }

    public void setTransportAddressesList(List<InetSocketTransportAddress> transportAddressesList) {
        this.transportAddressesList = transportAddressesList;
    }

    /**
     * The time(in unit) the client ping the cluster.
     */
    public String getPingSchedule() {
        return pingSchedule;
    }

    public void setPingSchedule(String pingSchedule) {
        this.pingSchedule = pingSchedule;
    }

    /**
     *  The time( in unit) to wait for connection timeout.
     */
    public String getTcpConnectTimeout() {
        return tcpConnectTimeout;
    }

    public void setTcpConnectTimeout(String tcpConnectTimeout) {
        this.tcpConnectTimeout = tcpConnectTimeout;
    }

    /**
     * true if compression (LZF) enable between all nodes.
     */
    public boolean getTcpCompress() {
        return tcpCompress;
    }

    public void setTcpCompress(boolean tcpCompress) {
        this.tcpCompress = tcpCompress;
    }

    /**
     *  User for authenticate against the cluster. Requires "transport_client" role
     *  for accessing the cluster. Require XPack client jar on the classpath
     */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     *  Password for authenticate against the cluster. Require XPack client jar on the classpath
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Enable SSL. Require XPack client jar on the classpath
     */
    public boolean getEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    /**
     * The time(in unit) to wait for a ping response from a node too return.
     */
    public String getPingTimeout() {
        return pingTimeout;
    }

    public void setPingTimeout(String pingTimeout) {
        this.pingTimeout = pingTimeout;
    }
}