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
package org.apache.camel.component.zookeepermaster.policy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.component.zookeepermaster.CamelNodeState;
import org.apache.camel.component.zookeepermaster.ContainerIdFactory;
import org.apache.camel.component.zookeepermaster.DefaultContainerIdFactory;
import org.apache.camel.component.zookeepermaster.ZookeeperGroupListenerSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.curator.framework.CuratorFramework;

/**
 * {@link org.apache.camel.spi.RoutePolicy} to run the route in master/slave mode.
 * <p/>
 * <b>Important:</b> Make sure to set the route to autoStartup=false as the route lifecycle
 * is controlled by this route policy which will start/stop the route accordingly to being
 * the master in the zookeeper cluster group.
 */
public class MasterRoutePolicy extends RoutePolicySupport implements CamelContextAware {

    private CuratorFramework curator;
    private int maximumConnectionTimeout = 10 * 1000;
    private String zooKeeperUrl;
    private String zooKeeperPassword;
    private String zkRoot = "/camel/zookeepermaster/clusters/master";
    private String groupName;
    private ContainerIdFactory containerIdFactory = new DefaultContainerIdFactory();

    // state if the consumer has been started
    private final AtomicBoolean masterConsumer = new AtomicBoolean();

    private ZookeeperGroupListenerSupport groupListener;
    private volatile CamelNodeState thisNodeState;
    private CamelContext camelContext;
    private Route route;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getZkRoot() {
        return zkRoot;
    }

    /**
     * The root path to use in zookeeper where information is stored which nodes are master/slave etc.
     * Will by default use: /camel/zookeepermaster/clusters/master
     */
    public void setZkRoot(String zkRoot) {
        this.zkRoot = zkRoot;
    }

    public String getGroupName() {
        return groupName;
    }

    /**
     * The name of the cluster group to use
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public ContainerIdFactory getContainerIdFactory() {
        return containerIdFactory;
    }

    /**
     * To use a custom ContainerIdFactory for creating container ids.
     */
    public void setContainerIdFactory(ContainerIdFactory containerIdFactory) {
        this.containerIdFactory = containerIdFactory;
    }

    public CuratorFramework getCurator() {
        return curator;
    }

    /**
     * To use a custom configured CuratorFramework as connection to zookeeper ensemble.
     */
    public void setCurator(CuratorFramework curator) {
        this.curator = curator;
    }

    public int getMaximumConnectionTimeout() {
        return maximumConnectionTimeout;
    }

    /**
     * Timeout in millis to use when connecting to the zookeeper ensemble
     */
    public void setMaximumConnectionTimeout(int maximumConnectionTimeout) {
        this.maximumConnectionTimeout = maximumConnectionTimeout;
    }

    public String getZooKeeperUrl() {
        return zooKeeperUrl;
    }

    /**
     * The url for the zookeeper ensemble
     */
    public void setZooKeeperUrl(String zooKeeperUrl) {
        this.zooKeeperUrl = zooKeeperUrl;
    }

    public String getZooKeeperPassword() {
        return zooKeeperPassword;
    }

    /**
     * The password to use when connecting to the zookeeper ensemble
     */
    public void setZooKeeperPassword(String zooKeeperPassword) {
        this.zooKeeperPassword = zooKeeperPassword;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);
        this.route = route;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(camelContext, "CamelContext");
        ObjectHelper.notEmpty("groupName", groupName);

        String path = getCamelClusterPath(groupName);
        this.groupListener = new ZookeeperGroupListenerSupport(path, route.getEndpoint(), onLockOwned(), onDisconnected());
        this.groupListener.setCamelContext(camelContext);
        this.groupListener.setCurator(curator);
        this.groupListener.setMaximumConnectionTimeout(maximumConnectionTimeout);
        this.groupListener.setZooKeeperUrl(zooKeeperUrl);
        this.groupListener.setZooKeeperPassword(zooKeeperPassword);
        ServiceHelper.startService(groupListener);

        log.info("Attempting to become master for endpoint: " + route.getEndpoint() + " in " + getCamelContext() + " with singletonID: " + getGroupName());
        thisNodeState = createNodeState();
        groupListener.updateState(thisNodeState);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopAndShutdownServices(groupListener);
        masterConsumer.set(false);
    }

    protected Runnable onLockOwned() {
        return () -> {
            if (masterConsumer.compareAndSet(false, true)) {
                try {
                    // ensure endpoint is also started
                    log.info("Elected as master. Starting consumer: {}", route.getEndpoint());
                    startConsumer(route.getConsumer());

                    // Lets show we are starting the consumer.
                    thisNodeState = createNodeState();
                    thisNodeState.setStarted(true);
                    groupListener.updateState(thisNodeState);
                } catch (Exception e) {
                    log.error("Failed to start master consumer for: " + route.getEndpoint(), e);
                }

                log.info("Elected as master. Consumer started: {}", route.getEndpoint());
            }
        };
    }

    protected Runnable onDisconnected() {
        return () -> {
            masterConsumer.set(false);
            try {
                stopConsumer(route.getConsumer());
            } catch (Exception e) {
                log.warn("Failed to stop master consumer: " + route.getEndpoint(), e);
            }
        };
    }

    protected String getCamelClusterPath(String name) {
        String path = name;
        if (ObjectHelper.isNotEmpty(zkRoot)) {
            path = zkRoot + "/" + name;
        }
        return path;
    }

    private CamelNodeState createNodeState() {
        String containerId = getContainerIdFactory().newContainerId();
        CamelNodeState state = new CamelNodeState(getGroupName(), containerId);
        state.setConsumer(route.getEndpoint().getEndpointUri());
        return state;
    }

}
