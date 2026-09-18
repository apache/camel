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

import java.util.concurrent.TimeUnit;

import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;

import static org.awaitility.Awaitility.await;

public abstract class JGroupsRaftClusterAbstractTest {

    /**
     * Wait until a leader has been elected AND all given handles know who the leader is. Only connected handles are
     * checked; disconnected or closed handles are skipped. Without checking leader() on every active handle, a follower
     * node may not yet have discovered the leader, causing set() to throw when the REDIRECT protocol has no leader
     * address to forward to.
     */
    protected void waitForLeader(int attempts, RaftHandle... handles) {
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
