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
package org.apache.camel.component.zookeeper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.RuntimeCamelException;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;

public class ZooKeeperCuratorConfiguration implements Cloneable {
    private CuratorFramework curatorFramework;
    private List<String> nodes;
    private String namespace;
    private long reconnectBaseSleepTime;
    private TimeUnit reconnectBaseSleepTimeUnit;
    private int reconnectMaxRetries;
    private long reconnectMaxSleepTime;
    private TimeUnit reconnectMaxSleepTimeUnit;
    private long sessionTimeout;
    private TimeUnit sessionTimeoutUnit;
    private long connectionTimeout;
    private TimeUnit connectionTimeoutUnit;
    private List<AuthInfo> authInfoList;
    private long maxCloseWait;
    private TimeUnit maxCloseWaitUnit;
    private RetryPolicy retryPolicy;
    private String basePath;

    public ZooKeeperCuratorConfiguration() {
        this.reconnectBaseSleepTime = 1000;
        this.reconnectBaseSleepTimeUnit = TimeUnit.MILLISECONDS;
        this.reconnectMaxSleepTime = Integer.MAX_VALUE;
        this.reconnectMaxSleepTimeUnit = TimeUnit.MILLISECONDS;
        this.reconnectMaxRetries = 3;

        // from org.apache.curator.framework.CuratorFrameworkFactory
        this.sessionTimeout = Integer.getInteger("curator-default-session-timeout", 60 * 1000);
        this.sessionTimeoutUnit =  TimeUnit.MILLISECONDS;

        // from org.apache.curator.framework.CuratorFrameworkFactory
        this.connectionTimeout = Integer.getInteger("curator-default-connection-timeout", 15 * 1000);
        this.connectionTimeoutUnit = TimeUnit.MILLISECONDS;

        // from org.apache.curator.framework.CuratorFrameworkFactory
        this.maxCloseWait = 1000;
        this.maxCloseWaitUnit = TimeUnit.MILLISECONDS;
    }

    // *******************************
    // Properties
    // *******************************

    public CuratorFramework getCuratorFramework() {
        return curatorFramework;
    }

    public void setCuratorFramework(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;
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

    public long getReconnectMaxSleepTime() {
        return reconnectMaxSleepTime;
    }

    public void setReconnectMaxSleepTime(long reconnectMaxSleepTime) {
        this.reconnectMaxSleepTime = reconnectMaxSleepTime;
    }

    public void setReconnectMaxSleepTime(long reconnectMaxSleepTime, TimeUnit reconnectBaseSleepTimeUnit) {
        this.reconnectMaxSleepTime = reconnectMaxSleepTime;
        this.reconnectBaseSleepTimeUnit = reconnectBaseSleepTimeUnit;
    }

    public TimeUnit getReconnectMaxSleepTimeUnit() {
        return reconnectMaxSleepTimeUnit;
    }

    public void setReconnectMaxSleepTimeUnit(TimeUnit reconnectMaxSleepTimeUnit) {
        this.reconnectMaxSleepTimeUnit = reconnectMaxSleepTimeUnit;
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
        this.connectionTimeoutUnit = connectionTimeotUnit;
    }

    public TimeUnit getConnectionTimeoutUnit() {
        return connectionTimeoutUnit;
    }

    public void setConnectionTimeoutUnit(TimeUnit connectionTimeoutUnit) {
        this.connectionTimeoutUnit = connectionTimeoutUnit;
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

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    // *******************************
    // Clone
    // *******************************

    public ZooKeeperCuratorConfiguration copy() {
        try {
            return (ZooKeeperCuratorConfiguration)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
