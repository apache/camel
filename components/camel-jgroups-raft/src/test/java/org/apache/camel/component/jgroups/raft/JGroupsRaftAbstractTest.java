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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.core.ConditionTimeoutException;
import org.jgroups.Address;
import org.jgroups.protocols.raft.InMemoryLog;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.AfterEach;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class JGroupsRaftAbstractTest extends CamelTestSupport {
    /**
     * Runs before the Camel context is stopped (the methods of a class run before the ones of its superclass).
     */
    @AfterEach
    void awaitElectionsFinished() {
        RaftNodesState.awaitElectionsFinished();
    }

    /**
     * The in-memory logs are in a map of the JVM, keyed by the Raft id of the node: forget them, so that each test has
     * logs of its own and does not start with the term of a node of a former test with the same Raft id.
     */
    @Override
    protected void cleanupResources() throws Exception {
        InMemoryLog.logs.clear();
        super.cleanupResources();
    }

    protected void checkHeaders(Exchange exchange) {
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_COMMIT_INDEX, int.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_CURRENT_TERM, int.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_IS_LEADER, boolean.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LAST_APPLIED, int.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LOG_SIZE, int.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_RAFT_ID, String.class));
    }

    /**
     * Checked apart from {@link #checkHeaders(Exchange)}: the leader address is only known once a leader has been
     * elected, so an exchange whose headers were populated while the election was still running (a role change event,
     * for instance) legitimately carries none.
     */
    protected void checkLeaderAddressHeader(Exchange exchange) {
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LEADER_ADDRESS, Address.class));
    }

    /**
     * Send the exchange until it succeeds. The leader checked by {@link #waitForLeader(int, RaftHandle...)} can be lost
     * again right after (the election has not settled yet), and then <code>set()</code> fails because REDIRECT has no
     * leader to forward the request to.
     */
    protected Exchange requestWhenLeaderKnown(String uri, Processor processor) {
        AtomicReference<Exchange> last = new AtomicReference<>();
        try {
            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(200, TimeUnit.MILLISECONDS)
                    .until(() -> {
                        Exchange exchange = template.request(uri, processor);
                        last.set(exchange);
                        return exchange.getException() == null;
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    e.getMessage() + System.lineSeparator() + "Last failure: "
                                                + last.get().getException(),
                    e);
        }
        return last.get();
    }

    /**
     * Wait until a leader has been elected AND all given handles know who the leader is. Only connected handles are
     * checked; disconnected or closed handles are skipped. Without checking leader() on every active handle, a follower
     * node may not yet have discovered the leader, causing set() to throw when the REDIRECT protocol has no leader
     * address to forward to.
     */
    protected void waitForLeader(int attempts, RaftHandle... handles) {
        for (RaftHandle rh : handles) {
            RaftNodesState.enableElectionLogging(rh.channel());
        }
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
}
