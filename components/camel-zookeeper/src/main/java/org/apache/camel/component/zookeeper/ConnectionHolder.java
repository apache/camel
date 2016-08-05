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
package org.apache.camel.component.zookeeper;

import java.util.concurrent.CountDownLatch;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ConnectionHolder</code> watches for Connection based events from
 * {@link ZooKeeper} and can be used to block until a connection has been
 * established.
 */
public class ConnectionHolder implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHolder.class);

    private CountDownLatch connectionLatch = new CountDownLatch(1);

    private ZooKeeperConfiguration configuration;

    private ZooKeeper zookeeper;

    public ConnectionHolder(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    public ZooKeeper getZooKeeper() {
        if (zookeeper != null) {
            return zookeeper;
        }
        if (configuration.getConnectString() == null) {
            throw new RuntimeCamelException("Cannot create ZooKeeper connection as connection string is null. Have servers been configured?");
        }
        try {
            zookeeper = new ZooKeeper(configuration.getConnectString(), configuration.getTimeout(), this);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        awaitConnection();
        return zookeeper;
    }

    public boolean isConnected() {
        return connectionLatch.getCount() == 0;
    }

    public void awaitConnection() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Awaiting Connection event from Zookeeper cluster {}", configuration.getConnectString());
        }
        try {
            connectionLatch.await();
        } catch (InterruptedException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void process(WatchedEvent event) {
        if (event.getState() == KeeperState.SyncConnected) {
            connectionLatch.countDown();
        }
        connectionLatch.countDown();
    }

    public void closeConnection() {
        try {
            if (zookeeper != null) {
                zookeeper.close();
                zookeeper = null;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Shutting down connection to Zookeeper cluster {}", configuration.getConnectString());
            }
        } catch (InterruptedException e) {
            LOG.warn("Error closing zookeeper connection " + configuration.getConnectString() + ". This exception will be ignored.", e);
        }
    }
}
