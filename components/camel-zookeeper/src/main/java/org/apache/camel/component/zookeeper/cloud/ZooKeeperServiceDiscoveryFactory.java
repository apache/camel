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
package org.apache.camel.component.zookeeper.cloud;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;

public class ZooKeeperServiceDiscoveryFactory implements ServiceDiscoveryFactory {

    private ZooKeeperCuratorConfiguration configuration;

    public ZooKeeperServiceDiscoveryFactory() {
        this.configuration = new ZooKeeperCuratorConfiguration();
    }

    public ZooKeeperServiceDiscoveryFactory(ZooKeeperCuratorConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    // *********************************************
    // Properties
    // *********************************************

    public ZooKeeperCuratorConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ZooKeeperCuratorConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    public CuratorFramework getCuratorFramework() {
        return configuration.getCuratorFramework();
    }

    public void setCuratorFramework(CuratorFramework curatorFramework) {
        configuration.setCuratorFramework(curatorFramework);
    }

    public List<String> getNodes() {
        return configuration.getNodes();
    }

    public void setNodes(String nodes) {
        configuration.setNodes(nodes);
    }

    public void setNodes(List<String> nodes) {
        configuration.setNodes(nodes);
    }

    public String getNamespace() {
        return configuration.getNamespace();
    }

    public void setNamespace(String namespace) {
        configuration.setNamespace(namespace);
    }

    public long getReconnectBaseSleepTime() {
        return configuration.getReconnectBaseSleepTime();
    }

    public void setReconnectBaseSleepTime(long reconnectBaseSleepTime) {
        configuration.setReconnectBaseSleepTime(reconnectBaseSleepTime);
    }

    public void setReconnectBaseSleepTime(long reconnectBaseSleepTime, TimeUnit reconnectBaseSleepTimeUnit) {
        configuration.setReconnectBaseSleepTime(reconnectBaseSleepTime, reconnectBaseSleepTimeUnit);
    }

    public TimeUnit getReconnectBaseSleepTimeUnit() {
        return configuration.getReconnectBaseSleepTimeUnit();
    }

    public void setReconnectBaseSleepTimeUnit(TimeUnit reconnectBaseSleepTimeUnit) {
        configuration.setReconnectBaseSleepTimeUnit(reconnectBaseSleepTimeUnit);
    }

    public long getReconnectMaxSleepTime() {
        return configuration.getReconnectMaxSleepTime();
    }

    public void setReconnectMaxSleepTime(long reconnectMaxSleepTime) {
        configuration.setReconnectMaxSleepTime(reconnectMaxSleepTime);
    }

    public void setReconnectMaxSleepTime(long reconnectMaxSleepTime, TimeUnit reconnectBaseSleepTimeUnit) {
        configuration.setReconnectMaxSleepTime(reconnectMaxSleepTime, reconnectBaseSleepTimeUnit);
    }

    public TimeUnit getReconnectMaxSleepTimeUnit() {
        return configuration.getReconnectMaxSleepTimeUnit();
    }

    public void setReconnectMaxSleepTimeUnit(TimeUnit reconnectMaxSleepTimeUnit) {
        configuration.setReconnectMaxSleepTimeUnit(reconnectMaxSleepTimeUnit);
    }

    public int getReconnectMaxRetries() {
        return configuration.getReconnectMaxRetries();
    }

    public void setReconnectMaxRetries(int reconnectMaxRetries) {
        configuration.setReconnectMaxRetries(reconnectMaxRetries);
    }

    public long getSessionTimeout() {
        return configuration.getSessionTimeout();
    }

    public void setSessionTimeout(long sessionTimeout) {
        configuration.setSessionTimeout(sessionTimeout);
    }

    public void setSessionTimeout(long sessionTimeout, TimeUnit sessionTimeoutUnit) {
        configuration.setSessionTimeout(sessionTimeout, sessionTimeoutUnit);
    }

    public TimeUnit getSessionTimeoutUnit() {
        return configuration.getSessionTimeoutUnit();
    }

    public void setSessionTimeoutUnit(TimeUnit sessionTimeoutUnit) {
        configuration.setSessionTimeoutUnit(sessionTimeoutUnit);
    }

    public long getConnectionTimeout() {
        return configuration.getConnectionTimeout();
    }

    public void setConnectionTimeout(long connectionTimeout) {
        configuration.setConnectionTimeout(connectionTimeout);
    }

    public void setConnectionTimeout(long connectionTimeout, TimeUnit connectionTimeotUnit) {
        configuration.setConnectionTimeout(connectionTimeout, connectionTimeotUnit);
    }

    public TimeUnit getConnectionTimeoutUnit() {
        return configuration.getConnectionTimeoutUnit();
    }

    public void setConnectionTimeoutUnit(TimeUnit connectionTimeoutUnit) {
        configuration.setConnectionTimeoutUnit(connectionTimeoutUnit);
    }

    public ZooKeeperCuratorConfiguration copy() {
        return configuration.copy();
    }

    public List<AuthInfo> getAuthInfoList() {
        return configuration.getAuthInfoList();
    }

    public void setAuthInfoList(List<AuthInfo> authInfoList) {
        configuration.setAuthInfoList(authInfoList);
    }

    public long getMaxCloseWait() {
        return configuration.getMaxCloseWait();
    }

    public void setMaxCloseWait(long maxCloseWait) {
        configuration.setMaxCloseWait(maxCloseWait);
    }

    public TimeUnit getMaxCloseWaitUnit() {
        return configuration.getMaxCloseWaitUnit();
    }

    public void setMaxCloseWaitUnit(TimeUnit maxCloseWaitUnit) {
        configuration.setMaxCloseWaitUnit(maxCloseWaitUnit);
    }

    public RetryPolicy getRetryPolicy() {
        return configuration.getRetryPolicy();
    }

    public void setRetryPolicy(RetryPolicy retryPolicy) {
        configuration.setRetryPolicy(retryPolicy);
    }

    public String getBasePath() {
        return configuration.getBasePath();
    }

    public void setBasePath(String basePath) {
        configuration.setBasePath(basePath);
    }

    // *********************************************
    // Factory
    // *********************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext context) throws Exception {
        ZooKeeperServiceDiscovery discovery = new ZooKeeperServiceDiscovery(configuration);
        discovery.setCamelContext(context);

        return discovery;
    }
}
