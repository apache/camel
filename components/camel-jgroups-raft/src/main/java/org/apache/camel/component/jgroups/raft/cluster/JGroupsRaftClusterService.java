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
package org.apache.camel.component.jgroups.raft.cluster;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.jgroups.JChannel;
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.raft.RaftHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("cluster-service-jgroups-raft")
@Metadata(label = "bean",
          description = "JGroups Raft based cluster locking",
          annotations = { "interfaceName=org.apache.camel.cluster.CamelClusterService" })
@Configurer(metadataOnly = true)
public class JGroupsRaftClusterService extends AbstractCamelClusterService<JGroupsRaftClusterView> {

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsRaftClusterService.class);
    private static final String DEFAULT_JGROUPS_CONFIG = "raft.xml";
    private static final String DEFAULT_JGROUPS_CLUSTERNAME = "jgroupsraft-master";

    @Metadata(description = "Unique Raft id", required = true)
    private String id;
    @Metadata(description = "The path to the JGroups Raft configuration", defaultValue = DEFAULT_JGROUPS_CONFIG)
    private String jgroupsConfig;
    @Metadata(description = "The name of the cluster", defaultValue = DEFAULT_JGROUPS_CLUSTERNAME)
    private String jgroupsClusterName;
    @Metadata(label = "advanced", description = "To use a custom RaftHandler")
    private RaftHandle raftHandle;

    // the views of all namespaces share one RaftHandle, as the node is one Raft member (one raft id): the configured
    // raftHandle, or one this service creates. It is connected while views use it, guarded by handleLock
    private final Lock handleLock = new ReentrantLock();
    private RaftHandle connectedHandle;
    private int connectedViews;
    // the role listeners of the connected views, notified by the one role listener of the handle
    private final List<RAFT.RoleChange> viewRoleListeners = new CopyOnWriteArrayList<>();
    private final RAFT.RoleChange handleRoleListener = role -> viewRoleListeners.forEach(l -> l.roleChanged(role));

    public JGroupsRaftClusterService() {
        this.jgroupsConfig = DEFAULT_JGROUPS_CONFIG;
        this.jgroupsClusterName = DEFAULT_JGROUPS_CLUSTERNAME;
    }

    public JGroupsRaftClusterService(String jgroupsConfig, String jgroupsClusterName, RaftHandle raftHandle, String raftId) {
        this.jgroupsConfig = jgroupsConfig;
        this.jgroupsClusterName = jgroupsClusterName;
        this.raftHandle = raftHandle;
        setId(raftId);
    }

    @Override
    protected JGroupsRaftClusterView createView(String namespace) throws Exception {
        return new JGroupsRaftClusterView(this, namespace, jgroupsConfig, jgroupsClusterName, raftHandle, getId());
    }

    /**
     * Connects the RaftHandle of this node for a view, which then gets the role changes of the node.
     *
     * @return the connected handle
     */
    RaftHandle connectView(RAFT.RoleChange roleListener) throws Exception {
        handleLock.lock();
        try {
            if (connectedViews == 0) {
                RaftHandle handle = raftHandle != null ? raftHandle : createRaftHandle();
                // listen before connecting, so the role changes of the election are not missed
                handle.addRoleListener(handleRoleListener);
                try {
                    connect(handle);
                } catch (Exception e) {
                    handle.removeRoleListener(handleRoleListener);
                    if (handle != raftHandle) {
                        close(handle);
                    }
                    throw e;
                }
                connectedHandle = handle;
            }
            viewRoleListeners.add(roleListener);
            connectedViews++;
            return connectedHandle;
        } finally {
            handleLock.unlock();
        }
    }

    /**
     * Disconnects a view from the RaftHandle of this node, which is disconnected when no view uses it anymore.
     */
    void disconnectView(RAFT.RoleChange roleListener) {
        handleLock.lock();
        try {
            if (!viewRoleListeners.remove(roleListener) || --connectedViews > 0) {
                return;
            }
            RaftHandle handle = connectedHandle;
            connectedHandle = null;
            handle.removeRoleListener(handleRoleListener);
            if (handle == raftHandle) {
                // the configured handle is connected again when a view starts again
                LOG.info("Disconnecting JGroupsraft Channel of JGroupsRaftClusterService with Id {}", getId());
                try {
                    handle.channel().disconnect();
                } catch (Exception e) {
                    LOG.warn("Error disconnecting the JGroupsraft Channel with Id {}: {}", getId(), e.getMessage(), e);
                }
                closeLog(handle);
            } else {
                close(handle);
            }
        } finally {
            handleLock.unlock();
        }
    }

    private RaftHandle createRaftHandle() throws Exception {
        String config = jgroupsConfig != null && !jgroupsConfig.isEmpty() ? jgroupsConfig : DEFAULT_JGROUPS_CONFIG;
        return new RaftHandle(new JChannel(config), new NopStateMachine()).raftId(getId());
    }

    private void connect(RaftHandle handle) throws Exception {
        // it may take a while for event to trigger and allow us to join so retry a while
        Exception cause = null;
        for (int i = 1; i < 11; i++) {
            LOG.debug("Attempt #{} for raft {} to join {}", i, getId(), jgroupsClusterName);
            try {
                handle.channel().connect(jgroupsClusterName);
                LOG.debug("Joined and connected to {} with raft id: {}", jgroupsClusterName, getId());
                return;
            } catch (Exception e) {
                cause = e;
            }
            // wait for next attempt
            Thread.sleep(5000);
        }
        throw cause;
    }

    private void close(RaftHandle handle) {
        LOG.info("Closing JGroupsraft Channel of JGroupsRaftClusterService with Id {}", getId());
        try {
            handle.channel().close();
        } catch (Exception e) {
            LOG.warn("Error closing the JGroupsraft Channel with Id {}: {}", getId(), e.getMessage(), e);
        }
        closeLog(handle);
    }

    private void closeLog(RaftHandle handle) {
        try {
            if (handle.log() != null) {
                handle.log().close();
                LOG.info("Closed Log of JGroupsRaftClusterService with Id {}", getId());
            }
        } catch (Exception e) {
            LOG.warn("Error closing the Log with Id {}: {}", getId(), e.getMessage(), e);
        }
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        this.id = id;
    }

    @Override
    public String getId() {
        return super.getId();
    }

    public String getRaftId() {
        return getId();
    }

    public void setRaftId(String raftId) {
        setId(raftId);
    }

    public RaftHandle getRaftHandle() {
        return raftHandle;
    }

    public void setRaftHandle(RaftHandle raftHandle) {
        this.raftHandle = raftHandle;
    }

    public String getJgroupsConfig() {
        return jgroupsConfig;
    }

    public void setJgroupsConfig(String jgroupsConfig) {
        this.jgroupsConfig = jgroupsConfig;
    }

    public String getJgroupsClusterName() {
        return jgroupsClusterName;
    }

    public void setJgroupsClusterName(String jgroupsClusterName) {
        this.jgroupsClusterName = jgroupsClusterName;
    }
}
