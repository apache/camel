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

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.jgroups.raft.JGroupsRaftConstants;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGroupsRaftClusterView extends AbstractCamelClusterView {

    private static final transient Logger LOG = LoggerFactory.getLogger(JGroupsRaftClusterView.class);
    private final CamelClusterMember localMember = new JGropusraftLocalMember();
    private String jgroupsConfig;
    private String jgroupsClusterName;
    private RaftHandle raftHandle;
    private String raftId;
    private volatile boolean isMaster;

    protected JGroupsRaftClusterView(CamelClusterService cluster, String namespace, String jgroupsConfig, String jgroupsClusterName, RaftHandle raftHandle, String raftId) {
        super(cluster, namespace);

        this.jgroupsConfig = jgroupsConfig;
        this.jgroupsClusterName = jgroupsClusterName;
        this.raftHandle = raftHandle;
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
        return new ArrayList<CamelClusterMember>() {{ add(localMember); }};
    }

    @Override
    protected void doStart() throws Exception {
        if (raftHandle == null && jgroupsConfig != null && !jgroupsConfig.isEmpty()) {
            raftHandle = new RaftHandle(new JChannel(jgroupsConfig), new NopStateMachine()).raftId(raftId);
        } else if (raftHandle == null) {
            raftHandle = new RaftHandle(new JChannel(JGroupsRaftConstants.DEFAULT_JGROUPSRAFT_CONFIG), new NopStateMachine()).raftId(raftId);
        }
        fireLeadershipChangedEvent(Optional.empty());
        raftHandle.addRoleListener(new ClusterRoleChangeListener(this));
        raftHandle.channel().connect(jgroupsClusterName);
    }

    @Override
    protected void doStop() throws Exception {
        isMaster = false;
        fireLeadershipChangedEvent(Optional.empty());
        LOG.info("Disconnecting JGroupsraft Channel for JGroupsRaftClusterView with Id {}", raftId);
        raftHandle.channel().disconnect();
        if (raftHandle != null && raftHandle.log() != null) {
            raftHandle.log().close();
            LOG.info("Closed Log for JGroupsRaftClusterView with Id {}", raftId);
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        isMaster = false;
        fireLeadershipChangedEvent(Optional.empty());
        if (raftHandle != null) {
            raftHandle.channel().close();
            raftHandle = null;
        }

        LOG.info("Closing JGroupsraft Channel for JGroupsRaftClusterView with Id {}", raftId);
        if (raftHandle != null && raftHandle.channel() != null) {
            raftHandle.channel().close();

            LOG.info("Closed JGroupsraft Channel Channel for JGroupsRaftClusterView with Id {}", raftId);
        }
        LOG.info("Closing Log for JGroupsRaftClusterView with Id {}", raftId);
        if (raftHandle != null && raftHandle.log() != null) {
            raftHandle.log().close();
            LOG.info("Closed Log for JGroupsRaftClusterView with Id {}", raftId);
        }
        raftHandle = null;
    }

    public boolean isMaster() {
        return isMaster;
    }

    public void setMaster(boolean master) {
        isMaster = master;
    }

    @Override
    protected void fireLeadershipChangedEvent(Optional<CamelClusterMember> leader) {
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
