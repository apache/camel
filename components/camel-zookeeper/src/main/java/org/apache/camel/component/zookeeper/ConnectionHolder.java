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

import java.util.concurrent.CountDownLatch;

import org.apache.camel.RuntimeCamelException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ConnectionHolder</code> watches for Connection based events from {@link ZooKeeper} and can be used to block
 * until a connection has been established.
 */
public class ConnectionHolder implements Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHolder.class);

    private volatile CountDownLatch connectionLatch = new CountDownLatch(1);

    private ZooKeeperConfiguration configuration;

    private volatile ZooKeeper zookeeper;

    public ConnectionHolder(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the ZooKeeper handle, and creates one when there is none or when the current one can no longer be used: a
     * handle whose session expired (or that was closed) stays closed for good, so a new handle with a new session is
     * needed. Waits until the new handle is connected.
     */
    public ZooKeeper getZooKeeper() {
        ZooKeeper created;
        CountDownLatch latch;
        synchronized (this) {
            ZooKeeper current = zookeeper;
            if (current != null) {
                if (current.getState().isAlive()) {
                    return current;
                }
                LOG.info("The ZooKeeper session 0x{} to {} can no longer be used ({}). Creating a new session.",
                        Long.toHexString(current.getSessionId()), configuration.getConnectString(), current.getState());
                closeQuietly(current);
                zookeeper = null;
            }
            if (configuration.getConnectString() == null) {
                throw new RuntimeCamelException(
                        "Cannot create ZooKeeper connection as connection string is null. Have servers been configured?");
            }
            latch = new CountDownLatch(1);
            connectionLatch = latch;
            try {
                // each handle counts down its own latch, so that a late event of a previous handle cannot release
                // the wait for a new one
                created = new ZooKeeper(configuration.getConnectString(), configuration.getTimeout(), event -> {
                    process(event, latch);
                });
                zookeeper = created;
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        // wait on the latch of this handle: another thread may already have replaced the field
        awaitConnection(latch);
        return created;
    }

    public boolean isConnected() {
        return connectionLatch.getCount() == 0;
    }

    public void awaitConnection() {
        awaitConnection(connectionLatch);
    }

    private void awaitConnection(CountDownLatch latch) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Awaiting Connection event from Zookeeper cluster {}", configuration.getConnectString());
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void process(WatchedEvent event) {
        process(event, connectionLatch);
    }

    private static void process(WatchedEvent event, CountDownLatch latch) {
        if (event.getState() == KeeperState.SyncConnected) {
            latch.countDown();
        }
        latch.countDown();
    }

    private void closeQuietly(ZooKeeper handle) {
        try {
            handle.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void closeConnection() {
        try {
            if (zookeeper != null) {
                zookeeper.close();
                zookeeper = null;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Shutting down connection to Zookeeper cluster {}", configuration.getConnectString());
            }
        } catch (InterruptedException e) {
            LOG.warn("Error closing zookeeper connection {}. This exception will be ignored.",
                    configuration.getConnectString(), e);
            Thread.currentThread().interrupt();
        }
    }
}
