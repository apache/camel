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

import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;

public final class RaftNodesState {

    private RaftNodesState() {
    }

    /**
     * Describe the state of the given nodes, to find out why no leader has been elected.
     */
    public static String describe(RaftHandle... handles) {
        StringBuilder sb = new StringBuilder("State of the nodes when giving up waiting for a leader:");
        for (RaftHandle rh : handles) {
            JChannel channel = rh.channel();
            sb.append(System.lineSeparator())
                    .append("  raftId=").append(rh.raftId())
                    .append(" connected=").append(channel.isConnected())
                    .append(" isLeader=").append(rh.isLeader())
                    .append(" leader=").append(rh.leader())
                    .append(" term=").append(rh.currentTerm())
                    .append(" view=").append(channel.getView());
        }
        return sb.toString();
    }
}
