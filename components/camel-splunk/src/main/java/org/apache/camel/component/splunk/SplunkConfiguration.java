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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.splunk.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SplunkConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SplunkConfiguration.class);

    private String host = Service.DEFAULT_HOST;
    private int port = Service.DEFAULT_PORT;
    private String scheme = Service.DEFAULT_SCHEME;
    private String app;
    private String owner;
    private String username;
    private String password;
    private int connectionTimeout = 5000;
    private String index;
    private String sourceType;
    private String source;
    private int tcpReceiverPort;

    // consumer properties
    private int count;
    private String fieldList;
    private String search;
    private String savedSearch;
    private String earliestTime;
    private String latestTime;
    private String initEarliestTime;

    public SplunkConfiguration(final String host, final int port, final String username, final String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public SplunkConfiguration(final String username, final String password) {
        this(Service.DEFAULT_HOST, Service.DEFAULT_PORT, username, password);
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

    public String getFieldList() {
        return fieldList;
    }

    public void setFieldList(String fieldList) {
        this.fieldList = fieldList;
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

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    public String getSavedSearch() {
        return this.savedSearch;
    }

    public void setSavedSearch(String savedSearch) {
        this.savedSearch = savedSearch;
    }

    public Service createService() {
        final Map<String, Object> args = new HashMap<String, Object>();
        if (host != null) {
            args.put("host", host);
        }
        if (port > 0) {
            args.put("port", port);
        }
        if (scheme != null) {
            args.put("scheme", scheme);
        }
        if (app != null) {
            args.put("app", app);
        }
        if (owner != null) {
            args.put("owner", owner);
        }

        args.put("username", username);
        args.put("password", password);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<Service> future = executor.submit(new Callable<Service>() {
            public Service call() throws Exception {
                return Service.connect(args);
            }
        });
        try {
            Service service;
            if (connectionTimeout > 0) {
                service = future.get(connectionTimeout, TimeUnit.MILLISECONDS);
            } else {
                service = future.get();
            }
            LOG.info("Successfully connected to Splunk");
            return service;
        } catch (Exception e) {
            throw new RuntimeException(String.format("could not connect to Splunk Server @ %s:%d - %s", host, port, e.getMessage()));
        }
    }
}
