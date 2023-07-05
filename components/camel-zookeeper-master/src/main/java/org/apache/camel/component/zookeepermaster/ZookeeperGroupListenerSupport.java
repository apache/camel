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

import org.apache.camel.Endpoint;
import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.component.zookeepermaster.group.GroupListener;
import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperGroupListenerSupport<T extends NodeState> extends ZookeeperGroupSupport<T> implements GroupListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperGroupListenerSupport.class);
    private Group<T> singleton;
    private final String clusterPath;
    private final Endpoint endpoint;
    private final Runnable onLockAcquired;
    private final Runnable onDisconnected;

    public ZookeeperGroupListenerSupport(String clusterPath, Endpoint endpoint, Runnable onLockAcquired,
                                         Runnable onDisconnected) {
        this.clusterPath = clusterPath;
        this.endpoint = endpoint;
        this.onLockAcquired = onLockAcquired;
        this.onDisconnected = onDisconnected;
    }

    public void updateState(T state) {
        singleton.update(state);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.singleton = createGroup(clusterPath);
        this.singleton.add(this);
        singleton.start();
    }

    @Override
    protected void doStop() throws Exception {
        IOHelper.close(singleton);
        super.doStop();
    }

    public String getClusterPath() {
        return clusterPath;
    }

    public Group<T> getGroup() {
        return singleton;
    }

    @Override
    public void groupEvent(Group<T> group, GroupEvent event) {
        switch (event) {
            case CONNECTED:
                break;
            case CHANGED:
                if (singleton.isConnected()) {
                    if (singleton.isMaster()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Master/Standby endpoint is Master for: {} in {}", endpoint,
                                    endpoint.getCamelContext());
                        }
                        onLockOwned();
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Master/Standby endpoint is Standby for: {} in {}", endpoint,
                                    endpoint.getCamelContext());
                        }
                    }
                }
                break;
            case DISCONNECTED:
                try {
                    LOG.info("Disconnecting as master. Stopping consumer: {}", endpoint);
                    onDisconnected();
                } catch (Exception e) {
                    LOG.warn("Failed to stop master consumer for: {}. This exception is ignored.", endpoint, e);
                }
                break;
            default:
                // noop
        }
    }

    protected void onDisconnected() {
        onDisconnected.run();
    }

    protected void onLockOwned() {
        onLockAcquired.run();
    }

}
