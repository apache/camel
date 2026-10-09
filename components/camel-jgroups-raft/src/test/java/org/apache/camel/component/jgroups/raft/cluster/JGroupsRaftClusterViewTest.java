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
import java.util.Optional;

import org.apache.camel.cluster.CamelClusterMember;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link JGroupsRaftClusterView} lifecycle methods that do not require a real JGroups cluster.
 */
public class JGroupsRaftClusterViewTest {

    /**
     * doStop() must not throw NullPointerException when raftHandle is null (e.g. when the view failed to start before a
     * handle was initialised).
     */
    @Test
    void doStopWithNullRaftHandleDoesNotThrow() {
        JGroupsRaftClusterView view = new JGroupsRaftClusterView(null, "test-ns", null, null, null, "A") {
            @Override
            public Optional<CamelClusterMember> getLeader() {
                return Optional.empty();
            }

            @Override
            public CamelClusterMember getLocalMember() {
                return new CamelClusterMember() {
                    @Override
                    public boolean isLeader() {
                        return false;
                    }

                    @Override
                    public boolean isLocal() {
                        return true;
                    }

                    @Override
                    public String getId() {
                        return "A";
                    }
                };
            }

            @Override
            public List<CamelClusterMember> getMembers() {
                return List.of(getLocalMember());
            }
        };

        // raftHandle is null (never set / doStart never ran) — doStop must be safe.
        assertDoesNotThrow(view::doStop);
    }
}
