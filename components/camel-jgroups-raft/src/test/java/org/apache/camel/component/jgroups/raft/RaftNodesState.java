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
import org.jgroups.protocols.MERGE3;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.protocols.raft.election.BaseElection;
import org.jgroups.raft.RaftHandle;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;

public final class RaftNodesState {

    private RaftNodesState() {
    }

    /**
     * Raise the log level of the protocols involved in the election of the leader, to find out how it goes when it
     * stalls. The logs of JGroups go to the console, and the output of a test is small when it succeeds.
     */
    public static void enableElectionLogging(JChannel channel) {
        ProtocolStack stack = channel.getProtocolStack();
        setLevel(stack.findProtocol(BaseElection.class), "trace");
        setLevel(stack.findProtocol(RAFT.class), "debug");
        setLevel(stack.findProtocol(GMS.class), "debug");
        setLevel(stack.findProtocol(MERGE3.class), "debug");
    }

    private static void setLevel(Protocol protocol, String level) {
        if (protocol != null) {
            protocol.setLevel(level);
        }
    }

    /**
     * Describe the state of the given nodes, to find out why no leader has been elected.
     */
    public static String describe(RaftHandle... handles) {
        StringBuilder sb = new StringBuilder("State of the nodes when giving up waiting for a leader:");
        for (RaftHandle rh : handles) {
            JChannel channel = rh.channel();
            BaseElection election = channel.getProtocolStack().findProtocol(BaseElection.class);
            sb.append(System.lineSeparator())
                    .append("  raftId=").append(rh.raftId())
                    .append(" role=").append(rh.raft().role())
                    .append(" connected=").append(channel.isConnected())
                    .append(" isLeader=").append(rh.isLeader())
                    .append(" leader=").append(rh.leader())
                    .append(" term=").append(rh.currentTerm())
                    .append(" votedFor=").append(rh.raft().votedFor())
                    .append(" votingThreadRunning=").append(election != null && election.isVotingThreadRunning())
                    .append(" view=").append(channel.getView());
        }
        return sb.toString();
    }
}
