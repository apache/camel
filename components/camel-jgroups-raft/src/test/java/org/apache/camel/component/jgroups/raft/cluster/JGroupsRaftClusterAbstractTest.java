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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jgroups.raft.RaftNodesState;
import org.awaitility.core.ConditionTimeoutException;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.jgroups.raft.StateMachine;
import org.junit.jupiter.api.AfterEach;

import static org.awaitility.Awaitility.await;

public abstract class JGroupsRaftClusterAbstractTest {

    protected final List<CamelContext> contexts = new ArrayList<>();
    protected final List<String> routeNames = new ArrayList<>();
    private final List<RaftHandle> openHandles = new ArrayList<>();

    /**
     * The channels are registered in the static routing table of SHARED_LOOPBACK, so a channel left open by a failed
     * test would make a rerun of the test fail on joining with the same member names ("duplicate members"), and would
     * hide the reason of the first failure.
     */
    @AfterEach
    void closeAll() {
        for (CamelContext context : contexts) {
            try {
                context.stop();
            } catch (Exception e) {
                // best effort cleanup
            }
        }
        for (RaftHandle handle : openHandles) {
            JChannel channel = handle.channel();
            if (channel != null) {
                channel.close();
            }
        }
        contexts.clear();
        routeNames.clear();
        openHandles.clear();
    }

    /**
     * Create the Raft handle of a node and keep track of it, so that its channel is closed at the end of the test.
     */
    protected RaftHandle createHandle(String id, StateMachine stateMachine) throws Exception {
        RaftHandle handle = new RaftHandle(new JChannel("raftABC.xml").name(id), stateMachine).raftId(id);
        openHandles.add(handle);
        return handle;
    }

    /**
     * Wait until a leader has been elected AND all given handles know who the leader is. Only connected handles are
     * checked; disconnected or closed handles are skipped. Without checking leader() on every active handle, a follower
     * node may not yet have discovered the leader, causing set() to throw when the REDIRECT protocol has no leader
     * address to forward to.
     */
    protected void waitForLeader(int attempts, RaftHandle... handles) {
        try {
            await().atMost(attempts, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .until(() -> {
                        boolean hasLeader = false;
                        for (RaftHandle rh : handles) {
                            if (!rh.channel().isConnected()) {
                                continue;
                            }
                            if (rh.isLeader()) {
                                hasLeader = true;
                            }
                            if (rh.leader() == null) {
                                return false;
                            }
                        }
                        return hasLeader;
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(e.getMessage() + System.lineSeparator() + RaftNodesState.describe(handles), e);
        }
    }

    /**
     * Wait until the given channel's view has exactly the expected number of members. Use this after closing a channel
     * to ensure the remaining nodes have processed the LEAVE before creating new channels with the same member name.
     */
    protected void waitForViewSize(JChannel channel, int expectedSize, int timeoutSeconds) {
        await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> channel.isConnected() && channel.getView() != null
                        && channel.getView().size() == expectedSize);
    }
}
