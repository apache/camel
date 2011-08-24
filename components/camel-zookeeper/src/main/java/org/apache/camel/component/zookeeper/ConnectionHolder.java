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

import static java.lang.String.format;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;

/**
 * <code>ConnectionHolder</code> watches for Connection based events from
 * {@link ZooKeeper} and can be used to block until a connection has been
 * established.
 */
public class ConnectionHolder implements Watcher {

    private static final transient Log LOG = LogFactory.getLog(ConnectionHolder.class);

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
            if (configuration.getSessionId() > 0 && configuration.getSessionPassword() != null) {
                zookeeper = new ZooKeeper(configuration.getConnectString(), configuration.getTimeout(), this, configuration.getSessionId(), configuration.getSessionPassword());
            } else {
                zookeeper = new ZooKeeper(configuration.getConnectString(), configuration.getTimeout(), this);
            }
        } catch (Exception e) {
            ObjectHelper.wrapRuntimeCamelException(e);
        }
        awaitConnection();
        return zookeeper;
    }

    public boolean isConnected() {
        return connectionLatch.getCount() == 0;
    }

    public void awaitConnection() {
        if (LOG.isDebugEnabled()) {
            LOG.debug(format("Awaiting Connection event from Zookeeper cluster %s", configuration.getConnectString()));
        }
        try {
            connectionLatch.await();
        } catch (InterruptedException e) {
            ObjectHelper.wrapRuntimeCamelException(e);
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
            zookeeper.close();
            if (LOG.isDebugEnabled()) {
                LOG.debug(format("Shutting down connection to Zookeeper cluster %s", configuration.getConnectString()));
            }
        } catch (InterruptedException e) {
            LOG.error("Error closing zookeeper connection.", e);
        }
    }
}
