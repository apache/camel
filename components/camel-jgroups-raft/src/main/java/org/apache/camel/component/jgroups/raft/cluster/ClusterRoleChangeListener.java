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

import java.util.Optional;

import org.apache.camel.util.ObjectHelper;
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.protocols.raft.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterRoleChangeListener implements RAFT.RoleChange {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterRoleChangeListener.class);

    private final JGroupsRaftClusterView jgroupsRaftClusterView;

    public ClusterRoleChangeListener(JGroupsRaftClusterView jgroupsRaftClusterView) {
        ObjectHelper.notNull(jgroupsRaftClusterView, "endpoint");

        this.jgroupsRaftClusterView = jgroupsRaftClusterView;
    }

    @Override
    public void roleChanged(Role role) {
        LOG.debug("Role received {}.", role);
        switch (role) {
            case Leader:
                if (!jgroupsRaftClusterView.isMaster()) {
                    jgroupsRaftClusterView.setMaster(true);
                    jgroupsRaftClusterView
                            .fireLeadershipChangedEvent(Optional.ofNullable(jgroupsRaftClusterView.getLocalMember()));
                }
                break;
            case Follower:
                if (jgroupsRaftClusterView.isMaster()) {
                    jgroupsRaftClusterView.setMaster(false);
                    jgroupsRaftClusterView.fireLeadershipChangedEvent(Optional.empty());
                }
                break;
            default:
                LOG.error("Role {} unknown.", role);
                throw new UnsupportedOperationException("Role " + role + " unknown.");
        }
    }
}
