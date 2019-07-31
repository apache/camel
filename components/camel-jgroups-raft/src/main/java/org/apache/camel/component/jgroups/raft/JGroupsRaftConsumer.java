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

import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.jgroups.raft.RaftHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes events from the JGroups-raft RaftHandle ({@code org.jgroups.raft.RaftHandle}). Received events
 * are routed to Camel as body and/or headers of {@link org.apache.camel.Exchange} see {@link JGroupsRaftEventType}.
 */
public class JGroupsRaftConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(JGroupsRaftConsumer.class);

    private final RaftHandle raftHandle;
    private final String clusterName;
    private boolean enableRoleChangeEvents;

    private final CamelRoleChangeListener roleListener;
    private final JGroupsRaftEndpoint endpoint;

    public JGroupsRaftConsumer(JGroupsRaftEndpoint endpoint, Processor processor, RaftHandle raftHandle, String clusterName, boolean enableRoleChangeEvents) {
        super(endpoint, processor);

        this.endpoint = endpoint;
        this.raftHandle = raftHandle;
        this.clusterName = clusterName;
        this.enableRoleChangeEvents = enableRoleChangeEvents;

        this.roleListener = new CamelRoleChangeListener(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (enableRoleChangeEvents) {
            LOG.debug("Connecting roleListener : {} to the cluster: {}.", roleListener, clusterName);
            raftHandle.addRoleListener(roleListener);
        }
        endpoint.connect();
    }

    @Override
    protected void doStop() throws Exception {
        if (enableRoleChangeEvents) {
            LOG.debug("Closing connection to cluster: {} from roleListener: {}.", clusterName, roleListener);
            raftHandle.removeRoleListener(roleListener);
        }
        endpoint.disconnect();
        super.doStop();
    }
}
