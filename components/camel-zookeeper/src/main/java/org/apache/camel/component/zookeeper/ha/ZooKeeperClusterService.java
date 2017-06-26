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
package org.apache.camel.component.zookeeper.ha;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.impl.ha.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperClusterService extends AbstractCamelClusterService<ZooKeeperClusterView> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperClusterService.class);

    private CuratorFramework client;
    private List<String> nodes;
    private String namespace;
    private long reconnectBaseSleepTime;
    private TimeUnit reconnectBaseSleepTimeUnit;
    private int reconnectMaxRetries;
    private long sessionTimeout;
    private TimeUnit sessionTimeoutUnit;
    private long connectionTimeout;
    private TimeUnit connectionTimeotUnit;
    private List<AuthInfo> authInfoList;
    private long maxCloseWait;
    private TimeUnit maxCloseWaitUnit;
    private boolean closeOnStop;
    private RetryPolicy retryPolicy;

    public ZooKeeperClusterService() {
        this.reconnectBaseSleepTime = 1000;
        this.reconnectBaseSleepTimeUnit = TimeUnit.MILLISECONDS;
        this.reconnectMaxRetries = 3;
        this.closeOnStop = true;

        // from org.apache.curator.framework.CuratorFrameworkFactory
        this.sessionTimeout = Integer.getInteger("curator-default-session-timeout", 60 * 1000);
        this.sessionTimeoutUnit =  TimeUnit.MILLISECONDS;

        // from org.apache.curator.framework.CuratorFrameworkFactory
        this.connectionTimeout = Integer.getInteger("curator-default-connection-timeout", 15 * 1000);
        this.connectionTimeotUnit = TimeUnit.MILLISECONDS;

        // from org.apache.curator.framework.CuratorFrameworkFactory
        this.maxCloseWait = 1000;
        this.maxCloseWaitUnit = TimeUnit.MILLISECONDS;
    }

    // *********************************************
    // Properties
    // *********************************************

    public CuratorFramework getClient() {
        return client;
    }

    public void setClient(CuratorFramework client) {
        this.client = client;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(String nodes) {
        this.nodes = Collections.unmodifiableList(
            Arrays.stream(nodes.split(",")).collect(Collectors.toList())
        );
    }

    public void setNodes(List<String> nodes) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public long getReconnectBaseSleepTime() {
        return reconnectBaseSleepTime;
    }

    public void setReconnectBaseSleepTime(long reconnectBaseSleepTime) {
        this.reconnectBaseSleepTime = reconnectBaseSleepTime;
    }

    public void setReconnectBaseSleepTime(long reconnectBaseSleepTime, TimeUnit reconnectBaseSleepTimeUnit) {
        this.reconnectBaseSleepTime = reconnectBaseSleepTime;
        this.reconnectBaseSleepTimeUnit = reconnectBaseSleepTimeUnit;
    }

    public TimeUnit getReconnectBaseSleepTimeUnit() {
        return reconnectBaseSleepTimeUnit;
    }

    public void setReconnectBaseSleepTimeUnit(TimeUnit reconnectBaseSleepTimeUnit) {
        this.reconnectBaseSleepTimeUnit = reconnectBaseSleepTimeUnit;
    }

    public int getReconnectMaxRetries() {
        return reconnectMaxRetries;
    }

    public void setReconnectMaxRetries(int reconnectMaxRetries) {
        this.reconnectMaxRetries = reconnectMaxRetries;
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout, TimeUnit sessionTimeoutUnit) {
        this.sessionTimeout = sessionTimeout;
        this.sessionTimeoutUnit = sessionTimeoutUnit;
    }

    public TimeUnit getSessionTimeoutUnit() {
        return sessionTimeoutUnit;
    }

    public void setSessionTimeoutUnit(TimeUnit sessionTimeoutUnit) {
        this.sessionTimeoutUnit = sessionTimeoutUnit;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout, TimeUnit connectionTimeotUnit) {
        this.connectionTimeout = connectionTimeout;
        this.connectionTimeotUnit = connectionTimeotUnit;
    }

    public TimeUnit getConnectionTimeotUnit() {
        return connectionTimeotUnit;
    }

    public void setConnectionTimeotUnit(TimeUnit connectionTimeotUnit) {
        this.connectionTimeotUnit = connectionTimeotUnit;
    }

    public List<AuthInfo> getAuthInfoList() {
        return authInfoList;
    }

    public void setAuthInfoList(List<AuthInfo> authInfoList) {
        this.authInfoList = authInfoList;
    }

    public long getMaxCloseWait() {
        return maxCloseWait;
    }

    public void setMaxCloseWait(long maxCloseWait) {
        this.maxCloseWait = maxCloseWait;
    }

    public TimeUnit getMaxCloseWaitUnit() {
        return maxCloseWaitUnit;
    }

    public void setMaxCloseWaitUnit(TimeUnit maxCloseWaitUnit) {
        this.maxCloseWaitUnit = maxCloseWaitUnit;
    }

    public boolean isCloseOnStop() {
        return closeOnStop;
    }

    public void setCloseOnStop(boolean closeOnStop) {
        this.closeOnStop = closeOnStop;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    // *********************************************
    //
    // *********************************************

    @Override
    protected ZooKeeperClusterView createView(String namespace) throws Exception {
        return new ZooKeeperClusterView(this, getOrCreateClient(), namespace);
    }

    @Override
    protected void doStart() throws Exception {
        // instantiate a new CuratorFramework
        getOrCreateClient();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (client != null && closeOnStop) {
            client.close();
        }
    }

    private CuratorFramework getOrCreateClient() throws Exception {
        if (client == null) {
            // Validate parameters
            ObjectHelper.notNull(getCamelContext(), "Camel Context");
            ObjectHelper.notNull(nodes, "ZooKeeper Nodes");

            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(String.join(",", nodes))
                .sessionTimeoutMs((int)sessionTimeoutUnit.toMillis(sessionTimeout))
                .connectionTimeoutMs((int)connectionTimeotUnit.toMillis(connectionTimeout))
                .maxCloseWaitMs((int)maxCloseWaitUnit.toMillis(maxCloseWait))
                .retryPolicy(retryPolicy());

            Optional.ofNullable(namespace).ifPresent(builder::namespace);
            Optional.ofNullable(authInfoList).ifPresent(builder::authorization);

            LOGGER.debug("Connect to ZooKeeper with namespace {},  nodes: {}", namespace, nodes);
            client = builder.build();

            LOGGER.debug("Starting ZooKeeper client");
            client.start();
        }

        return this.client;
    }

    private RetryPolicy retryPolicy() {
        return retryPolicy != null
            ? retryPolicy
            : new ExponentialBackoffRetry(
                (int)reconnectBaseSleepTimeUnit.toMillis(reconnectBaseSleepTime),
                reconnectMaxRetries);
    }
}
