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
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.protocols.raft.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelRoleChangeListener implements RAFT.RoleChange {
    private static final Logger LOG = LoggerFactory.getLogger(CamelRoleChangeListener.class);

    private final JGroupsRaftConsumer consumer;
    private final JGroupsRaftEndpoint endpoint;
    private final AsyncProcessor processor;

    public CamelRoleChangeListener(JGroupsRaftConsumer consumer, JGroupsRaftEndpoint endpoint, Processor processor) {
        this.consumer = consumer;
        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public void roleChanged(Role role) {
        LOG.trace("New Role {} received.", role);
        JGroupsRaftEventType eventType = toEventType(role);
        if (eventType == null) {
            // Guard against future jgroups-raft Role values: log and ignore rather than throw. RAFT swallows an
            // exception thrown from this callback, so throwing would leave the consumer silently without an event.
            LOG.warn("Ignoring jgroups-raft role change to unhandled role [{}]", role);
            return;
        }
        Exchange exchange = createExchange();
        exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, eventType);
        processExchange(role, exchange);
    }

    /**
     * Maps a jgroups-raft {@link Role} to the {@link JGroupsRaftEventType} dispatched to the route, or {@code null} for
     * a role that produces no event. A {@code Learner} is a non-voting follower (it replicates the log but cannot vote
     * or become leader), so it maps to {@link JGroupsRaftEventType#FOLLOWER}: a route that stops its leader work on a
     * {@code FOLLOWER} event then also stops it when this node steps down to {@code Learner} (CAMEL-25515). This
     * mirrors the cluster-side handling added for CAMEL-25488.
     */
    static JGroupsRaftEventType toEventType(Role role) {
        switch (role) {
            case Leader:
                return JGroupsRaftEventType.LEADER;
            case Follower:
            case Learner:
                return JGroupsRaftEventType.FOLLOWER;
            default:
                return null;
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

    private Exchange createExchange() {
        Exchange exchange = consumer.createExchange(true);
        endpoint.populateJGroupsRaftHeaders(exchange);
        return exchange;
    }

}
