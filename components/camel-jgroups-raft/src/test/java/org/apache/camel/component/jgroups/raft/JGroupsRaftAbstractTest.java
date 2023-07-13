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

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.jgroups.raft.RaftHandle;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class JGroupsRaftAbstractTest extends CamelTestSupport {
    protected void checkHeaders(Exchange exchange) {
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_COMMIT_INDEX, int.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_CURRENT_TERM, int.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_IS_LEADER, boolean.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LAST_APPLIED, int.class));
        //assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LEADER_ADDRESS, Address.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LOG_SIZE, int.class));
        assertNotNull(exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_RAFT_ID, String.class));
    }

    protected void waitForLeader(int attempts, RaftHandle rh, RaftHandle rh2, RaftHandle rh3) throws InterruptedException {
        boolean thereIsLeader = rh.isLeader() || rh2.isLeader() || rh3.isLeader();
        while (!thereIsLeader && attempts > 0) {
            thereIsLeader = rh.isLeader() || rh2.isLeader() || rh3.isLeader();
            TimeUnit.SECONDS.sleep(1);
            attempts--;
        }
        if (attempts <= 0) {
            throw new RuntimeCamelException("No leader in time!");
        }
    }
}
