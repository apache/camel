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
package org.apache.camel.component.zookeepermaster;

import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.zookeepermaster.group.DefaultGroupFactoryStrategy;
import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.component.zookeepermaster.group.ManagedGroupFactory;
import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryOneTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperGroupSupport<T extends NodeState> extends ServiceSupport
        implements CamelContextAware, Callable<CuratorFramework>, ConnectionStateListener {

    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperComponentSupport.class);

    private static final String ZOOKEEPER_URL = "zookeeper.url";
    private static final String ZOOKEEPER_PASSWORD = "zookeeper.password";
    private static final String ZOOKEEPER_URL_ENV = "ZOOKEEPER_URL";
    private static final String ZOOKEEPER_HOST_ENV = "ZK_CLIENT_SERVICE_HOST";
    private static final String ZOOKEEPER_PORT_ENV = "ZK_CLIENT_SERVICE_PORT";

    private CamelContext camelContext;

    @Metadata(label = "advanced", autowired = true)
    private ManagedGroupFactory managedGroupFactory;
    @Metadata(label = "advanced", autowired = true)
    private ManagedGroupFactoryStrategy managedGroupFactoryStrategy;
    @Metadata(label = "advanced")
    private CuratorFramework curator;
    @Metadata(defaultValue = "10000")
    private int maximumConnectionTimeout = 10 * 1000;
    @Metadata(defaultValue = "localhost:2181")
    private String zooKeeperUrl;
    @Metadata(label = "security", secret = true)
    private String zooKeeperPassword;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CuratorFramework getCurator() {
        if (managedGroupFactory == null) {
            throw new IllegalStateException("Component is not started");
        }
        return managedGroupFactory.getCurator();
    }

    public Group<T> createGroup(String path) {
        if (managedGroupFactory == null) {
            throw new IllegalStateException("Component is not started");
        }
        return (Group<T>) managedGroupFactory.createGroup(path, CamelNodeState.class);
    }

    /**
     * To use a custom configured CuratorFramework as connection to zookeeper ensemble.
     */
    public void setCurator(CuratorFramework curator) {
        this.curator = curator;
        registerAsListener();
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

    public ManagedGroupFactory getManagedGroupFactory() {
        return managedGroupFactory;
    }

    public void setManagedGroupFactory(ManagedGroupFactory managedGroupFactory) {
        this.managedGroupFactory = managedGroupFactory;
        this.managedGroupFactory.setClassLoader(this.getClass().getClassLoader());
    }

    public ManagedGroupFactoryStrategy getManagedGroupFactoryStrategy() {
        return managedGroupFactoryStrategy;
    }

    public void setManagedGroupFactoryStrategy(ManagedGroupFactoryStrategy managedGroupFactoryStrategy) {
        this.managedGroupFactoryStrategy = managedGroupFactoryStrategy;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        // attempt to lookup curator framework from registry using the name curator
        if (curator == null) {
            try {
                CuratorFramework aCurator
                        = getCamelContext().getRegistry().lookupByNameAndType("curator", CuratorFramework.class);
                if (aCurator != null) {
                    LOG.debug("CuratorFramework found in CamelRegistry: {}", aCurator);
                    setCurator(aCurator);
                }
            } catch (Exception exception) {
                // ignore
            }
        }

        if (managedGroupFactoryStrategy == null) {
            Set<ManagedGroupFactoryStrategy> set
                    = getCamelContext().getRegistry().findByType(ManagedGroupFactoryStrategy.class);
            if (set.size() == 1) {
                setManagedGroupFactoryStrategy(set.iterator().next());
            }
        }
        if (managedGroupFactory == null) {
            Set<ManagedGroupFactory> set = getCamelContext().getRegistry().findByType(ManagedGroupFactory.class);
            if (set.size() == 1) {
                setManagedGroupFactory(set.iterator().next());
            }
        }
        if (managedGroupFactory == null) {
            Set<ManagedGroupFactoryStrategy> set
                    = getCamelContext().getRegistry().findByType(ManagedGroupFactoryStrategy.class);
            if (set.size() == 1) {
                setManagedGroupFactoryStrategy(set.iterator().next());
            } else {
                setManagedGroupFactoryStrategy(new DefaultGroupFactoryStrategy());
            }
        }
        if (managedGroupFactory == null) {
            setManagedGroupFactory(getManagedGroupFactoryStrategy().createGroupFactory(curator, getClass().getClassLoader(),
                    getCamelContext(), this));
        }
    }

    @Override
    public CuratorFramework call() throws Exception {
        String connectString = getZooKeeperUrl();
        if (connectString == null) {
            connectString = System.getenv(ZOOKEEPER_URL_ENV);
        }
        if (connectString == null) {
            String zkHost = System.getenv(ZOOKEEPER_HOST_ENV);
            if (zkHost != null) {
                String zkPort = System.getenv(ZOOKEEPER_PORT_ENV);
                connectString = zkHost + ":" + (zkPort == null ? "2181" : zkPort);
            }
        }
        if (connectString == null) {
            connectString = System.getProperty(ZOOKEEPER_URL, "localhost:2181");
        }
        String password = getZooKeeperPassword();
        if (password == null) {
            System.getProperty(ZOOKEEPER_PASSWORD);
        }
        LOG.info("Creating new CuratorFramework with connection: {}", connectString);
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .retryPolicy(new RetryOneTime(1000))
                .connectionTimeoutMs(getMaximumConnectionTimeout());

        if (password != null && !password.isEmpty()) {
            builder.authorization("digest", ("fabric:" + password).getBytes());
        }

        curator = builder.build();
        LOG.debug("Starting CuratorFramework {}", curator);
        curator.start();
        return curator;
    }

    @Override
    protected void doStop() throws Exception {
        if (managedGroupFactory != null) {
            managedGroupFactory.close();
            managedGroupFactory = null;
        }
    }

    @Override
    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        LOG.debug("CuratorFramework state changed: {}", newState);
    }

    protected void registerAsListener() {
        if (curator != null) {
            curator.getConnectionStateListenable().addListener(this);
        }
    }
}
