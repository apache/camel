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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.awaitility.core.ConditionTimeoutException;
import org.jgroups.JChannel;
import org.jgroups.protocols.MERGE3;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.protocols.raft.election.BaseElection;
import org.jgroups.raft.RaftHandle;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;

public final class RaftNodesState {

    private static final Logger LOG = LoggerFactory.getLogger(RaftNodesState.class);

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
     * Wait until the election of every node of the JVM is over, that is until no thread votes anymore.
     * <p>
     * A node that closes its channel while it still votes keeps voting forever when it has a majority and no leader (a
     * cluster of a single member): its voting thread is only told to stop. As the log (and thus the term) of a node is
     * shared by the nodes with the same Raft id in the JVM, the thread of a former test keeps increasing the term of
     * the node of the next tests, which then refuses the term of the leader and never accepts it. Wait before closing
     * the channels, so that the thread ends by itself.
     * <p>
     * This is best effort: when an election does not end, the threads still voting are logged and the test goes on with
     * its cleanup.
     */
    public static void awaitElectionsFinished() {
        try {
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(() -> votingThreads().isEmpty());
        } catch (ConditionTimeoutException e) {
            // the cleanup of the test goes on, and its result tells why
            LOG.warn("The election does not end, these threads still vote: {}", votingThreads());
        }
    }

    private static List<String> votingThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith("voting-thread"))
                .map(Thread::getName)
                .toList();
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
