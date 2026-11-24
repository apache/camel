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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.cluster.CamelClusterEventListener;
import org.jgroups.protocols.raft.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ClusterRoleChangeListener} that verify role transitions without requiring a real JGroups
 * cluster.
 */
public class ClusterRoleChangeListenerTest {

    private JGroupsRaftClusterView view;
    private ClusterRoleChangeListener listener;

    @BeforeEach
    void setUp() {
        // Minimal in-memory view: no cluster service needed, no listeners registered,
        // so fireLeadershipChangedEvent() is a safe no-op.
        view = new JGroupsRaftClusterView(null, "test-ns", null, null, null, "A");
        listener = new ClusterRoleChangeListener(view);
    }

    @Test
    void leaderRoleSetsViewAsMaster() {
        assertFalse(view.isMaster());
        listener.roleChanged(Role.Leader);
        assertTrue(view.isMaster());
    }

    @Test
    void followerRoleClearsViewMasterStatus() {
        view.setMaster(true);
        listener.roleChanged(Role.Follower);
        assertFalse(view.isMaster());
    }

    @Test
    void learnerRoleClearsViewMasterStatus() {
        view.setMaster(true);
        // Role.Learner previously fell through to default: throw (swallowed by RAFT), leaving the view as master.
        // It must now de-master the view without throwing.
        assertDoesNotThrow(() -> listener.roleChanged(Role.Learner));
        assertFalse(view.isMaster());
    }

    @Test
    void roleChangeAfterTheViewStoppedIsIgnored() throws Exception {
        AtomicInteger events = new AtomicInteger();
        view.addEventListener((CamelClusterEventListener.Leadership) (v, leader) -> events.incrementAndGet());
        // the view stops, while the handle of the node may still notify a role change to the view's listener
        view.doStop();
        int afterStop = events.get();

        listener.roleChanged(Role.Leader);
        assertFalse(view.isMaster());
        assertFalse(view.getLeader().isPresent());
        assertEquals(afterStop, events.get(), "A stopped view must not fire a leadership event");
    }

    @Test
    void learnerRoleWhenNotMasterIsNoOp() {
        assertFalse(view.isMaster());
        assertDoesNotThrow(() -> listener.roleChanged(Role.Learner));
        assertFalse(view.isMaster());
    }
}
