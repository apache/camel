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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.util.ObjectHelper;
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.protocols.raft.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelRoleChangeListener implements RAFT.RoleChange {
    private static final transient Logger LOG = LoggerFactory.getLogger(CamelRoleChangeListener.class);

    private final JGroupsRaftEndpoint endpoint;
    private final AsyncProcessor processor;

    public CamelRoleChangeListener(JGroupsRaftEndpoint endpoint, Processor processor) {
        ObjectHelper.notNull(endpoint, "endpoint");
        ObjectHelper.notNull(processor, "processor");

        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public void roleChanged(Role role) {
        LOG.trace("New Role {} received.", role);
        Exchange exchange = endpoint.createExchange();
        switch (role) {
            case Leader:
                exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.LEADER);
                processExchange(role, exchange);
                break;
            case Follower:
                exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.FOLLOWER);
                processExchange(role, exchange);
                break;
            case Candidate:
                exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.CANDIDATE);
                processExchange(role, exchange);
                break;
            default:
                throw new JGroupsRaftException("Role [" + role + "] unknown.");
        }
    }

    private void processExchange(Role role, Exchange exchange) {
        try {
            LOG.debug("Processing Role: {}", role);
            processor.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    // noop
                }
            });
        } catch (Exception e) {
            throw new JGroupsRaftException("Error in consumer while dispatching exchange containing role " + role, e);
        }
    }
}
