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
package org.apache.camel.component.splunk;

import com.splunk.Service;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class SplunkConfiguration {

    @UriPath(description = "Name has no purpose") @Metadata(required = "true")
    private String name;
    @UriParam
    private String scheme = Service.DEFAULT_SCHEME;
    @UriParam
    private String host = Service.DEFAULT_HOST;
    @UriParam
    private int port = Service.DEFAULT_PORT;
    @UriParam
    private String app;
    @UriParam
    private String owner;
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam(defaultValue = "5000")
    private int connectionTimeout = 5000;
    @UriParam
    private boolean useSunHttpsHandler;
    @UriParam
    private String index;
    @UriParam
    private String sourceType;
    @UriParam
    private String source;
    @UriParam
    private int tcpReceiverPort;

    // consumer properties
    @UriParam
    private int count;
    @UriParam
    private String search;
    @UriParam
    private String savedSearch;
    @UriParam
    private String earliestTime;
    @UriParam
    private String latestTime;
    @UriParam
    private String initEarliestTime;
    private SplunkConnectionFactory connectionFactory;

    /**
     * Streaming mode sends exchanges as they are received, rather than in a batch
     */
    @UriParam
    private Boolean streaming;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInitEarliestTime() {
        return initEarliestTime;
    }

    public void setInitEarliestTime(String initEarliestTime) {
        this.initEarliestTime = initEarliestTime;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getEarliestTime() {
        return earliestTime;
    }

    public void setEarliestTime(String earliestTime) {
        this.earliestTime = earliestTime;
    }

    public String getLatestTime() {
        return latestTime;
    }

    public void setLatestTime(String latestTime) {
        this.latestTime = latestTime;
    }

    public int getTcpReceiverPort() {
        return tcpReceiverPort;
    }

    public void setTcpReceiverPort(int tcpReceiverPort) {
        this.tcpReceiverPort = tcpReceiverPort;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndex() {
        return index;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns streaming mode.
     * <p>
     * Streaming mode sends exchanges as they are received, rather than in a batch.
     */
    public boolean isStreaming() {
        return streaming != null ? streaming : false;
    }
    
    /**
     * Sets streaming mode.
     * <p>
     * Streaming mode sends exchanges as they are received, rather than in a batch.
     *  
     * @param streaming
     */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    public boolean isUseSunHttpsHandler() {
        return useSunHttpsHandler;
    }

    public void setUseSunHttpsHandler(boolean useSunHttpsHandler) {
        this.useSunHttpsHandler = useSunHttpsHandler;
    }

    public String getSavedSearch() {
        return this.savedSearch;
    }

    public void setSavedSearch(String savedSearch) {
        this.savedSearch = savedSearch;
    }

    public SplunkConnectionFactory getConnectionFactory() {
        return connectionFactory != null ? connectionFactory : createDefaultConnectionFactory();
    }

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
        return splunkConnectionFactory;
    }
}
