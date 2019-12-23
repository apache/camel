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
package org.apache.camel.component.splunk;

import com.splunk.SSLSecurityProtocol;
import com.splunk.Service;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class SplunkConfiguration {

    private SplunkConnectionFactory connectionFactory;

    @UriPath(description = "Name has no purpose") @Metadata(required = true)
    private String name;
    @UriParam(defaultValue = "https")
    private String scheme = Service.DEFAULT_SCHEME;
    @UriParam(defaultValue = "localhost")
    private String host = Service.DEFAULT_HOST;
    @UriParam(defaultValue = "8089")
    private int port = Service.DEFAULT_PORT;
    @UriParam(enums = "TLSv1.2,TLSv1.1,TLSv1,SSLv3", defaultValue = "TLSv1.2", label = "security")
    private SSLSecurityProtocol sslProtocol = SSLSecurityProtocol.TLSv1_2;
    @UriParam
    private String app;
    @UriParam
    private String owner;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(defaultValue = "5000")
    private int connectionTimeout = 5000;
    @UriParam(label = "security")
    private boolean useSunHttpsHandler;

    @UriParam(label = "producer")
    private String index;
    @UriParam(label = "producer")
    private String sourceType;
    @UriParam(label = "producer")
    private String source;
    @UriParam(label = "producer")
    private String eventHost;
    @UriParam(label = "producer")
    private int tcpReceiverPort;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean raw;

    @UriParam(label = "consumer")
    private int count;
    @UriParam(label = "consumer")
    private String search;
    @UriParam(label = "consumer")
    private String savedSearch;
    @UriParam(label = "consumer")
    private String earliestTime;
    @UriParam(label = "consumer")
    private String latestTime;
    @UriParam(label = "consumer")
    private String initEarliestTime;
    @UriParam(label = "consumer")
    private boolean streaming;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInitEarliestTime() {
        return initEarliestTime;
    }

    /**
     * Initial start offset of the first search
     */
    public void setInitEarliestTime(String initEarliestTime) {
        this.initEarliestTime = initEarliestTime;
    }

    public int getCount() {
        return count;
    }

    /**
     * A number that indicates the maximum number of entities to return.
     */
    public void setCount(int count) {
        this.count = count;
    }

    public String getSearch() {
        return search;
    }

    /**
     * The Splunk query to run
     */
    public void setSearch(String search) {
        this.search = search;
    }

    public String getEarliestTime() {
        return earliestTime;
    }

    /**
     * Earliest time of the search time window.
     */
    public void setEarliestTime(String earliestTime) {
        this.earliestTime = earliestTime;
    }

    public String getLatestTime() {
        return latestTime;
    }

    /**
     * Latest time of the search time window.
     */
    public void setLatestTime(String latestTime) {
        this.latestTime = latestTime;
    }

    public int getTcpReceiverPort() {
        return tcpReceiverPort;
    }

    /**
     * Splunk tcp receiver port
     */
    public void setTcpReceiverPort(int tcpReceiverPort) {
        this.tcpReceiverPort = tcpReceiverPort;
    }

    public boolean isRaw() {
        return raw;
    }

    /**
     * Should the payload be inserted raw
     */
    public void setRaw(boolean raw) {
        this.raw = raw;
    }

    public String getSourceType() {
        return sourceType;
    }

    /**
     * Splunk sourcetype argument
     */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSource() {
        return source;
    }

    /**
     * Splunk source argument
     */
    public void setSource(String source) {
        this.source = source;
    }

    public String getEventHost() {
        return eventHost;
    }

    /**
     * Override the default Splunk event host field
     */
    public void setEventHost(String eventHost) {
        this.eventHost = eventHost;
    }

    /**
     * Splunk index to write to
     */
    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndex() {
        return index;
    }

    public String getHost() {
        return host;
    }

    /**
     * Splunk host.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Splunk port
     */
    public void setPort(int port) {
        this.port = port;
    }

    public SSLSecurityProtocol getSslProtocol() {
        return sslProtocol;
    }

    /**
     * Set the ssl protocol to use
     * 
     * @param sslProtocol
     */
    public void setSslProtocol(SSLSecurityProtocol sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public String getScheme() {
        return scheme;
    }

    /**
     * Splunk scheme
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getApp() {
        return app;
    }

    /**
     * Splunk app
     */
    public void setApp(String app) {
        this.app = app;
    }

    public String getOwner() {
        return owner;
    }

    /**
     * Splunk owner
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for Splunk
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for Splunk
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Sets streaming mode.
     * <p>
     * Streaming mode sends exchanges as they are received, rather than in a batch.
     */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Timeout in MS when connecting to Splunk server
     */
    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    public boolean isUseSunHttpsHandler() {
        return useSunHttpsHandler;
    }

    /**
     * Use sun.net.www.protocol.https.Handler Https handler to establish the Splunk Connection.
     * Can be useful when running in application servers to avoid app. server https handling.
     */
    public void setUseSunHttpsHandler(boolean useSunHttpsHandler) {
        this.useSunHttpsHandler = useSunHttpsHandler;
    }

    public String getSavedSearch() {
        return this.savedSearch;
    }

    /**
     * The name of the query saved in Splunk to run
     */
    public void setSavedSearch(String savedSearch) {
        this.savedSearch = savedSearch;
    }

    public SplunkConnectionFactory getConnectionFactory() {
        return connectionFactory != null ? connectionFactory : createDefaultConnectionFactory();
    }

    /**
     * Splunk connection factory.
     */
    public void setConnectionFactory(SplunkConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    private SplunkConnectionFactory createDefaultConnectionFactory() {
        SplunkConnectionFactory splunkConnectionFactory;
        if (ObjectHelper.isNotEmpty(getHost()) && getPort() > 0) {
            splunkConnectionFactory = new SplunkConnectionFactory(getHost(), getPort(), getUsername(), getPassword());
        } else {
            splunkConnectionFactory = new SplunkConnectionFactory(getUsername(), getPassword());
        }
        splunkConnectionFactory.setApp(getApp());
        splunkConnectionFactory.setConnectionTimeout(getConnectionTimeout());
        splunkConnectionFactory.setScheme(getScheme());
        splunkConnectionFactory.setUseSunHttpsHandler(isUseSunHttpsHandler());
        splunkConnectionFactory.setSslProtocol(getSslProtocol());
        return splunkConnectionFactory;
    }

}
