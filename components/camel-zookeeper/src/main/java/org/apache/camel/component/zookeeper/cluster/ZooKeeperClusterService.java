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
package org.apache.camel.component.zookeeper.cluster;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorHelper;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperClusterService extends AbstractCamelClusterService<ZooKeeperClusterView> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperClusterService.class);

    private CuratorFramework curator;
    private ZooKeeperCuratorConfiguration configuration;
    private boolean managedInstance;

    public ZooKeeperClusterService() {
        this.configuration = new ZooKeeperCuratorConfiguration();
        this.managedInstance = true;
    }

    public ZooKeeperClusterService(ZooKeeperCuratorConfiguration configuration) {
        this.configuration = configuration.copy();
        this.managedInstance = true;
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

    public TimeUnit getConnectionTimeotUnit() {
        return configuration.getConnectionTimeoutUnit();
    }

    public void setConnectionTimeotUnit(TimeUnit connectionTimeotUnit) {
        configuration.setConnectionTimeoutUnit(connectionTimeotUnit);
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
    //
    // *********************************************

    @Override
    protected ZooKeeperClusterView createView(String namespace) throws Exception {

        // Validation
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        ObjectHelper.notNull(configuration.getBasePath(), "ZooKeeper base path");

        return new ZooKeeperClusterView(this, configuration, getOrCreateCurator(), namespace);
    }

    @Override
    protected void doStart() throws Exception {
        // instantiate a new CuratorFramework
        getOrCreateCurator();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (curator != null && managedInstance) {
            curator.close();
        }
    }

    private CuratorFramework getOrCreateCurator() throws Exception {
        if (curator == null) {
            curator = configuration.getCuratorFramework();

            if (curator == null) {
                managedInstance = true;

                LOGGER.debug("Starting ZooKeeper Curator with namespace '{}',  nodes: '{}'",
                    configuration.getNamespace(),
                    String.join(",", configuration.getNodes())
                );

                curator = ZooKeeperCuratorHelper.createCurator(configuration);
                curator.start();
            } else {
                managedInstance = false;
            }
        }

        return curator;
    }
}
