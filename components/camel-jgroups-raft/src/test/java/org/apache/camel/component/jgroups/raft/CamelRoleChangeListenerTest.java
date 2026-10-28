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
package org.apache.camel.component.jgroups.raft;

import org.jgroups.protocols.raft.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link CamelRoleChangeListener#toEventType(Role)} (CAMEL-25515).
 */
class CamelRoleChangeListenerTest {

    @Test
    void leaderMapsToLeaderEvent() {
        assertEquals(JGroupsRaftEventType.LEADER, CamelRoleChangeListener.toEventType(Role.Leader));
    }

    @Test
    void followerMapsToFollowerEvent() {
        assertEquals(JGroupsRaftEventType.FOLLOWER, CamelRoleChangeListener.toEventType(Role.Follower));
    }

    @Test
    void learnerMapsToFollowerEvent() {
        // CAMEL-25515: a learner is a non-voting follower; it must produce a FOLLOWER event (previously it hit the
        // default branch and threw, which RAFT swallowed, so the consumer got no event and a route that steps down
        // on FOLLOWER kept acting as leader after the node was moved to Learner).
        assertEquals(JGroupsRaftEventType.FOLLOWER, CamelRoleChangeListener.toEventType(Role.Learner));
    }
}
