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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.jgroups.raft.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exchange messages with JGroups-raft clusters.
 */
@UriEndpoint(firstVersion = "2.24.0", scheme = "jgroups-raft", title = "JGroups raft", syntax = "jgroup-raft:clusterName",
             category = { Category.CLUSTERING, Category.MESSAGING }, headersClass = JGroupsRaftConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL,
                                   description = "No body: an event for each change of the role of this member (only with"
                                                 + " enableRoleChangeEvents), with LEADER or FOLLOWER in the"
                                                 + " CamelJGroupsRaftEventType header and the state of the raft in the other"
                                                 + " headers"),
           producerIn = @BodyType(value = { Carrier.BYTES, Carrier.TEXT, Carrier.STREAM, Carrier.FILE },
                                  description = "The bytes to set() in the replicated log: a byte array, or text, a stream or"
                                                + " a file converted to bytes. A null body, a Map, a number or a POJO has no"
                                                + " conversion to bytes and is skipped without an error: nothing is set"),
           producerOut = @BodyType(value = { Carrier.BYTES, Carrier.UNCHANGED },
                                   description = "The byte array the state machine returns for the set (an empty array with"
                                                 + " the default NopStateMachine), with the state of the raft in the headers. A"
                                                 + " body that was skipped is left as it was"))
public class JGroupsRaftEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsRaftEndpoint.class);

    // the producers and consumers connected to the handle this endpoint created, guarded by connectLock
    private int connectCount;
    private final Lock connectLock = new ReentrantLock();

    private RaftHandle raftHandle;
    private RaftHandle resolvedRaftHandle;
    private StateMachine stateMachine;
    private String raftId;
    private String channelProperties;

    @UriPath
    @Metadata(required = true)
    private String clusterName;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean enableRoleChangeEvents;

    public JGroupsRaftEndpoint(String endpointUri, String clusterName, Component component,
                               String raftId, String channelProperties, StateMachine stateMachine, RaftHandle raftHandle) {
        super(endpointUri, component);
        this.clusterName = clusterName;

        this.raftId = raftId;
        this.channelProperties = channelProperties;
        this.stateMachine = stateMachine;
        this.raftHandle = raftHandle;
    }

    @Override
    public Producer createProducer() {
        return new JGroupsRaftProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        JGroupsRaftConsumer consumer = new JGroupsRaftConsumer(this, processor, clusterName, enableRoleChangeEvents);
        configureConsumer(consumer);
        return consumer;
    }

    public void populateJGroupsRaftHeaders(Exchange exchange) {
        exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_COMMIT_INDEX, resolvedRaftHandle.commitIndex());
        exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_CURRENT_TERM, resolvedRaftHandle.currentTerm());
        exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_IS_LEADER, resolvedRaftHandle.isLeader());
        exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LAST_APPLIED, resolvedRaftHandle.lastApplied());
        exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_LOG_SIZE, resolvedRaftHandle.logSize());
        exchange.getIn().setHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_RAFT_ID, resolvedRaftHandle.raftId());
    }

    @Override
    protected void doStart() throws Exception {
        LOG.info("Resolving JGroupsraft handle {}", getEndpointUri());
        super.doStart();
        connectLock.lock();
        try {
            // the handle this endpoint created is kept when the endpoint was stopped while producers or consumers
            // still used it (such as when another route that consumes from this endpoint was removed)
            if (resolvedRaftHandle == null || !isOwnHandle() || resolvedRaftHandle.channel() == null
                    || resolvedRaftHandle.channel().isClosed()) {
                resolvedRaftHandle = resolveHandle();
            }
        } finally {
            connectLock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // a handle this endpoint did not create (such as the raftHandle of the component) is used by other endpoints,
        // and is closed by the component
        if (resolvedRaftHandle != null && isOwnHandle()) {
            connectLock.lock();
            try {
                // the endpoint is stopped when a route that consumes from it is removed, also when the producers and
                // consumers of other routes still use its handle: then the last of them that disconnects closes it.
                // When Camel stops, the consumers of all routes have stopped and their in-flight exchanges are done,
                // so the handle is closed also when a producer that Camel does not stop (such as one that the
                // application created and started itself) still uses it
                if (connectCount == 0 || getCamelContext().isStopping()) {
                    closeOwnHandle();
                }
            } finally {
                connectLock.unlock();
            }
        }
        super.doStop();
    }

    private void closeOwnHandle() {
        // the channel and the log are each closed, also when closing the other one fails (as the component does)
        LOG.info("Closing JGroupsraft Channel {}", getEndpointUri());
        try {
            if (resolvedRaftHandle.channel() != null) {
                resolvedRaftHandle.channel().close();
                LOG.info("Closed JGroupsraft Channel {}", getEndpointUri());
            }
        } catch (Exception e) {
            LOG.warn("Error closing the JGroupsraft channel of {}: {}", getEndpointUri(), e.getMessage(), e);
        }
        LOG.info("Closing Log {}", getEndpointUri());
        try {
            if (resolvedRaftHandle.log() != null) {
                resolvedRaftHandle.log().close();
                LOG.info("Closed Log Channel {}", getEndpointUri());
            }
        } catch (Exception e) {
            LOG.warn("Error closing the JGroupsraft log of {}: {}", getEndpointUri(), e.getMessage(), e);
        }
    }

    private boolean isOwnHandle() {
        return raftHandle == null;
    }

    /**
     * The handle of the endpoint: the raftHandle option, or a handle it creates. Package-private for tests.
     */
    RaftHandle resolveHandle() throws Exception {
        if (raftHandle != null) {
            LOG.trace("Raft Handle resolved as passed by Component: {}", raftHandle);
            return raftHandle;
        }
        if (channelProperties != null && !channelProperties.isEmpty()) {
            LOG.trace("Raft Handle created with configured channelProperties: {} and state machine: {}", channelProperties,
                    stateMachine);
            @SuppressWarnings("resource")
            // NOTE: channel will be closed by this endpoint during
            // stop() lifecycle step.
            JChannel channel = new JChannel(channelProperties).name(raftId); // NOSONAR
            return new RaftHandle(channel, stateMachine).raftId(raftId);
        }
        LOG.trace("Raft Handle created with defaults: {}, {},", JGroupsRaftConstants.DEFAULT_JGROUPSRAFT_CONFIG, stateMachine);
        @SuppressWarnings("resource")
        // NOTE: channel will be closed by this endpoint during
        // stop() lifecycle step.
        JChannel channel = new JChannel(JGroupsRaftConstants.DEFAULT_JGROUPSRAFT_CONFIG).name(raftId); // NOSONAR
        return new RaftHandle(channel, stateMachine).raftId(raftId);
    }

    /**
     * Connect shared RaftHandle channel, called by producer and consumer.
     *
     * @throws Exception
     */
    public void connect() throws Exception {
        LOG.trace("Connecting JGroups-raft Channel {} with cluster name: {}, raftHandle: {} and using config: {}",
                getEndpointUri(), clusterName, resolvedRaftHandle, channelProperties == null ? "default" : channelProperties);
        if (!isOwnHandle()) {
            ((JGroupsRaftComponent) getComponent()).connectSharedHandle(resolvedRaftHandle, clusterName);
            return;
        }
        connectLock.lock();
        try {
            resolvedRaftHandle.channel().connect(clusterName);
            // counted after a successful connect, as a producer or consumer whose connect failed does not disconnect
            connectCount++;
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Disconnect shared RaftHandle channel, called by producer and consumer.
     */
    public void disconnect() {
        if (!isOwnHandle()) {
            ((JGroupsRaftComponent) getComponent()).disconnectSharedHandle(resolvedRaftHandle);
            return;
        }
        connectLock.lock();
        try {
            if (--connectCount == 0) {
                LOG.trace("Disconnecting JGroupsraft Channel {}", getEndpointUri());
                try {
                    resolvedRaftHandle.channel().disconnect();
                } catch (Exception e) {
                    LOG.warn("Error disconnecting the JGroupsraft channel of {}: {}", getEndpointUri(), e.getMessage(), e);
                }
                if (isStoppingOrStopped()) {
                    // the endpoint was stopped while this producer or consumer still used the handle
                    closeOwnHandle();
                }
            }
        } finally {
            connectLock.unlock();
        }
    }

    public String getClusterName() {
        return clusterName;
    }

    /**
     * The name of the JGroupsraft cluster the component should connect to.
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public boolean isEnableRoleChangeEvents() {
        return enableRoleChangeEvents;
    }

    /**
     * If set to true, the consumer endpoint will receive roleChange event as well (not just connecting and/or using the
     * state machine). By default it is set to false.
     */
    public void setEnableRoleChangeEvents(boolean enableRoleChangeEvents) {
        this.enableRoleChangeEvents = enableRoleChangeEvents;
    }

    public String getChannelProperties() {
        return channelProperties;
    }

    public void setChannelProperties(String channelProperties) {
        this.channelProperties = channelProperties;
    }

    public String getRaftId() {
        return raftId;
    }

    public void setRaftId(String raftId) {
        this.raftId = raftId;
    }

    public RaftHandle getRaftHandle() {
        return raftHandle;
    }

    public void setRaftHandle(RaftHandle raftHandle) {
        this.raftHandle = raftHandle;
    }

    public StateMachine getStateMachine() {
        return stateMachine;
    }

    public void setStateMachine(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public RaftHandle getResolvedRaftHandle() {
        return resolvedRaftHandle;
    }
}
