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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.jgroups.protocols.raft.Role;
import org.jgroups.raft.RaftHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGroupsRaftClusterView extends AbstractCamelClusterView {

    private static final transient Logger LOG = LoggerFactory.getLogger(JGroupsRaftClusterView.class);
    private final CamelClusterMember localMember = new JGropusraftLocalMember();
    private String raftId;
    private volatile boolean isMaster;
    // serializes the leadership changes (role changes of the node, start and stop of this view)
    private final Lock roleLock = new ReentrantLock();
    // the view was stopped: a role change still being notified to it is ignored, guarded by roleLock
    private boolean disconnected;
    private volatile ClusterRoleChangeListener roleListener;

    protected JGroupsRaftClusterView(CamelClusterService cluster, String namespace, String jgroupsConfig,
                                     String jgroupsClusterName, RaftHandle raftHandle, String raftId) {
        super(cluster, namespace);

        // the views of all namespaces share the RaftHandle of the node, which the cluster service configures and connects
        this.raftId = raftId;
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        if (isMaster) {
            return Optional.of(localMember);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        return new ArrayList<CamelClusterMember>() {
            {
                add(localMember);
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        fireLeadershipChangedEvent((CamelClusterMember) null);
        roleLock.lock();
        try {
            disconnected = false;
        } finally {
            roleLock.unlock();
        }

        ClusterRoleChangeListener listener = new ClusterRoleChangeListener(this);
        RaftHandle handle = getRaftClusterService().connectView(listener);
        roleListener = listener;

        // the node may already be the leader (elected before this view started), and then no role change is notified
        roleLock.lock();
        try {
            leadershipChanged(Role.Leader.name().equals(handle.raft().role()));
        } finally {
            roleLock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        leadershipLost();
        ClusterRoleChangeListener listener = roleListener;
        roleListener = null;
        if (listener != null) {
            // the handle is disconnected when the views of the other namespaces no longer use it either
            LOG.debug("Disconnecting JGroupsRaftClusterView {} with Id {}", getNamespace(), raftId);
            getRaftClusterService().disconnectView(listener);
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        doStop();
    }

    private JGroupsRaftClusterService getRaftClusterService() {
        return (JGroupsRaftClusterService) getClusterService();
    }

    /**
     * Updates the leadership of this view, and notifies the listeners when it changes.
     */
    void leadershipChanged(boolean leader) {
        roleLock.lock();
        try {
            if (!disconnected && leader != isMaster) {
                isMaster = leader;
                fireLeadershipChangedEvent(leader ? localMember : null);
            }
        } finally {
            roleLock.unlock();
        }
    }

    private void leadershipLost() {
        roleLock.lock();
        try {
            // the role changes that the handle notifies until this view's listener is removed are ignored
            disconnected = true;
            isMaster = false;
            fireLeadershipChangedEvent((CamelClusterMember) null);
        } finally {
            roleLock.unlock();
        }
    }

    public boolean isMaster() {
        return isMaster;
    }

    public void setMaster(boolean master) {
        isMaster = master;
    }

    @Override
    protected void fireLeadershipChangedEvent(CamelClusterMember leader) {
        super.fireLeadershipChangedEvent(leader);
    }

    private final class JGropusraftLocalMember implements CamelClusterMember {

        @Override
        public boolean isLeader() {
            return isMaster;
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public String getId() {
            return getClusterService().getId();
        }
    }
}
