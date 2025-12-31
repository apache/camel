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

import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorHelper;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Metadata(label = "bean",
          description = "ZooKeeper based cluster locking",
          annotations = { "interfaceName=org.apache.camel.cluster.CamelClusterService" })
@Configurer(metadataOnly = true)
public class ZooKeeperClusterService extends AbstractCamelClusterService<ZooKeeperClusterView> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperClusterService.class);

    @Metadata(description = "The Zookeeper server hosts (multiple servers can be separated by comma)", required = true)
    private String nodes;
    @Metadata(description = "The base path to store in ZooKeeper", required = true)
    private String basePath;
    @Metadata(description = "Node id", required = true)
    private String id;
    @Metadata(description = "ZooKeeper namespace. If a namespace is set here, all paths will get pre-pended with the namespace")
    private String namespace;
    @Metadata(description = "Max number of times to retry", defaultValue = "3")
    private int reconnectMaxRetries;
    @Metadata(description = "Initial amount of time (millis) to wait between retries", defaultValue = "1000")
    private long reconnectBaseSleepTime;
    @Metadata(description = "Max time (millis) to sleep on each retry")
    private long reconnectMaxSleepTime;
    @Metadata(description = "Session timeout (millis)", defaultValue = "60000")
    private long sessionTimeout;
    @Metadata(description = "Connect timeout (millis)", defaultValue = "15000")
    private long connectTimeout;
    @Metadata(description = "Time to wait (millis) during close to join background threads", defaultValue = "1000")
    private long maxCloseWait;
    @Metadata(label = "advanced", description = "To use a custom retry policy implementation")
    private RetryPolicy retryPolicy;

    private CuratorFramework curator;
    private ZooKeeperCuratorConfiguration configuration;
    private boolean managedInstance;

    public ZooKeeperClusterService() {
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

    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public void setId(String id) {
        this.id = id;
        super.setId(id);
    }

    public String getNodes() {
        return nodes;
    }

    public void setNodes(String nodes) {
        this.nodes = nodes;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getReconnectMaxRetries() {
        return reconnectMaxRetries;
    }

    public void setReconnectMaxRetries(int reconnectMaxRetries) {
        this.reconnectMaxRetries = reconnectMaxRetries;
    }

    public long getReconnectBaseSleepTime() {
        return reconnectBaseSleepTime;
    }

    public void setReconnectBaseSleepTime(long reconnectBaseSleepTime) {
        this.reconnectBaseSleepTime = reconnectBaseSleepTime;
    }

    public long getReconnectMaxSleepTime() {
        return reconnectMaxSleepTime;
    }

    public void setReconnectMaxSleepTime(long reconnectMaxSleepTime) {
        this.reconnectMaxSleepTime = reconnectMaxSleepTime;
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getMaxCloseWait() {
        return maxCloseWait;
    }

    public void setMaxCloseWait(long maxCloseWait) {
        this.maxCloseWait = maxCloseWait;
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

        // Validation
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        ObjectHelper.notNull(configuration.getBasePath(), "ZooKeeper base path");

        return new ZooKeeperClusterView(this, configuration, getOrCreateCurator(), namespace);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (configuration == null) {
            configuration = new ZooKeeperCuratorConfiguration();
        }
        if (nodes != null) {
            configuration.setNodes(nodes);
        }
        if (basePath != null) {
            configuration.setBasePath(basePath);
        }
        if (namespace != null) {
            configuration.setNamespace(namespace);
        }
        if (reconnectMaxRetries != 0) {
            configuration.setReconnectMaxRetries(reconnectMaxRetries);
        }
        if (reconnectBaseSleepTime != 0) {
            configuration.setReconnectBaseSleepTime(reconnectBaseSleepTime);
        }
        if (reconnectMaxSleepTime != 0) {
            configuration.setReconnectMaxSleepTime(reconnectMaxSleepTime);
        }
        if (sessionTimeout != 0) {
            configuration.setSessionTimeout(sessionTimeout);
        }
        if (connectTimeout != 0) {
            configuration.setConnectionTimeout(connectTimeout);
        }
        if (maxCloseWait != 0) {
            configuration.setMaxCloseWait(maxCloseWait);
        }
        if (retryPolicy != null) {
            configuration.setRetryPolicy(retryPolicy);
        }
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
            IOHelper.close(curator);
            curator = null;
        }
    }

    private CuratorFramework getOrCreateCurator() {
        if (curator == null) {
            curator = configuration.getCuratorFramework();

            if (curator == null) {
                managedInstance = true;

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Starting ZooKeeper Curator with namespace '{}',  nodes: '{}'",
                            configuration.getNamespace(),
                            String.join(",", configuration.getNodes()));
                }

                curator = ZooKeeperCuratorHelper.createCurator(configuration);
                curator.start();
            } else {
                managedInstance = false;
            }
        }

        return curator;
    }
}
