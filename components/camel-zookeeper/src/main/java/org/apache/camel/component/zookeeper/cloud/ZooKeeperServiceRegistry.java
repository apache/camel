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
package org.apache.camel.component.zookeeper.cloud;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorHelper;
import org.apache.camel.impl.cloud.AbstractServiceRegistry;
import org.apache.camel.util.ObjectHelper;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperServiceRegistry extends AbstractServiceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperServiceRegistry.class);

    private final Set<String> serviceList;
    private final boolean managedInstance;
    private ZooKeeperServiceRegistryConfiguration configuration;
    private CuratorFramework curator;
    private ServiceDiscovery<MetaData> serviceDiscovery;

    public ZooKeeperServiceRegistry() {
        this.serviceList = ConcurrentHashMap.newKeySet();
        this.configuration = new ZooKeeperServiceRegistryConfiguration();
        this.curator = configuration.getCuratorFramework();
        this.managedInstance = Objects.isNull(curator);
    }

    public ZooKeeperServiceRegistry(ZooKeeperServiceRegistryConfiguration configuration) {
        this.serviceList = ConcurrentHashMap.newKeySet();
        this.configuration = configuration.copy();
        this.curator = configuration.getCuratorFramework();
        this.managedInstance = Objects.isNull(curator);
    }

    // ****************
    // Properties
    // ****************

    public ZooKeeperServiceRegistryConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ZooKeeperServiceRegistryConfiguration configuration) {
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

    public boolean isDeregisterServicesOnStop() {
        return configuration.isDeregisterServicesOnStop();
    }

    public void setDeregisterServicesOnStop(boolean deregisterServicesOnStop) {
        configuration.setDeregisterServicesOnStop(deregisterServicesOnStop);
    }

    public boolean isOverrideServiceHost() {
        return configuration.isOverrideServiceHost();
    }

    public void setOverrideServiceHost(boolean overrideServiceHost) {
        configuration.setOverrideServiceHost(overrideServiceHost);
    }

    public String getServiceHost() {
        return configuration.getServiceHost();
    }

    public void setServiceHost(String serviceHost) {
        configuration.setServiceHost(serviceHost);
    }

    // ****************
    // Lifecycle
    // ****************

    @Override
    protected void doStart() throws Exception {
        if (curator == null) {
            // Validation
            ObjectHelper.notNull(getCamelContext(), "Camel Context");
            ObjectHelper.notNull(configuration.getBasePath(), "ZooKeeper base path");

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Starting ZooKeeper Curator with namespace '{}', nodes: '{}'",
                        configuration.getNamespace(),
                        String.join(",", configuration.getNodes()));
            }

            curator = ZooKeeperCuratorHelper.createCurator(configuration);
            curator.start();
        }

        if (serviceDiscovery == null) {
            // Validation
            ObjectHelper.notNull(configuration.getBasePath(), "ZooKeeper base path");

            LOGGER.debug("Starting ZooKeeper ServiceDiscoveryBuilder with base path '{}'",
                    configuration.getBasePath());

            serviceDiscovery = ZooKeeperCuratorHelper.createServiceDiscovery(configuration, curator, MetaData.class);
            serviceDiscovery.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (serviceDiscovery != null) {
            try {
                if (configuration.isDeregisterServicesOnStop()) {
                    for (String serviceName : serviceDiscovery.queryForNames()) {
                        for (ServiceInstance<MetaData> serviceInstance : serviceDiscovery.queryForInstances(serviceName)) {
                            if (serviceList.contains(serviceInstance.getId())) {
                                serviceDiscovery.unregisterService(serviceInstance);

                                // remove the serviceId to the list of known server
                                serviceList.remove(serviceInstance.getId());
                            }
                        }
                    }
                }

                serviceDiscovery.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing Curator ServiceDiscovery", e);
            }
        }

        if (curator != null && managedInstance) {
            curator.close();
        }
    }

    // ****************
    // Registry
    // ****************

    @Override
    public void register(ServiceDefinition definition) {
        if (definition.getId() == null) {
            throw new IllegalArgumentException("Service ID must be defined (definition=" + definition + ")");
        }
        if (definition.getName() == null) {
            throw new IllegalArgumentException("Service Name must be defined (definition=" + definition + ")");
        }

        try {
            ServiceInstance<MetaData> instance = ServiceInstance.<MetaData> builder()
                    .address(computeServiceHost(definition))
                    .port(definition.getPort())
                    .name(definition.getName())
                    .id(definition.getId())
                    .payload(new MetaData(definition.getMetadata()))
                    .build();

            serviceDiscovery.registerService(instance);

            // add the serviceId to the list of known server
            serviceList.add(definition.getId());
        } catch (Exception e) {
            LOGGER.warn("", e);
        }
    }

    @Override
    public void deregister(ServiceDefinition definition) {
        if (definition.getId() == null) {
            throw new IllegalArgumentException("Service ID must be defined (definition=" + definition + ")");
        }
        if (definition.getName() == null) {
            throw new IllegalArgumentException("Service Name must be defined (definition=" + definition + ")");
        }

        try {
            for (ServiceInstance<MetaData> serviceInstance : serviceDiscovery.queryForInstances(definition.getName())) {
                if (Objects.equals(serviceInstance.getId(), definition.getId())) {
                    serviceDiscovery.unregisterService(serviceInstance);

                    // remove the serviceId to the list of known server
                    serviceList.remove(serviceInstance.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("", e);
        }
    }

    // *********************************************
    // Helpers
    // *********************************************

    private String computeServiceHost(ServiceDefinition definition) {
        String host = definition.getHost();

        if (configuration.isOverrideServiceHost() && configuration.getServiceHost() != null) {
            host = configuration.getServiceHost();
        }

        return ObjectHelper.notNull(host, "service host");
    }

}
