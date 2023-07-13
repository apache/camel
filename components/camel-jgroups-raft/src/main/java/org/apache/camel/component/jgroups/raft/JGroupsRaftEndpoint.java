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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
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
public class JGroupsRaftEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsRaftEndpoint.class);

    private AtomicInteger connectCount = new AtomicInteger();

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
    public Producer createProducer() throws Exception {
        return new JGroupsRaftProducer(this, clusterName);
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
        resolvedRaftHandle = resolveHandle();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Closing JGroupsraft Channel {}", getEndpointUri());
        if (resolvedRaftHandle != null && resolvedRaftHandle.channel() != null) {
            resolvedRaftHandle.channel().close();
            LOG.info("Closed JGroupsraft Channel {}", getEndpointUri());
        }
        LOG.info("Closing Log {}", getEndpointUri());
        if (resolvedRaftHandle != null && resolvedRaftHandle.log() != null) {
            resolvedRaftHandle.log().close();
            LOG.info("Closed Log Channel {}", getEndpointUri());
        }
        super.doStop();
    }

    private RaftHandle resolveHandle() throws Exception {
        if (raftHandle != null) {
            LOG.trace("Raft Handle resolved as passed by Component: {}", raftHandle);
            return raftHandle;
        }
        if (channelProperties != null && !channelProperties.isEmpty()) {
            LOG.trace("Raft Handle created with configured channelProperties: {} and state machine: {}", channelProperties,
                    stateMachine);
            return new RaftHandle(new JChannel(channelProperties).name(raftId), stateMachine).raftId(raftId);
        }
        LOG.trace("Raft Handle created with defaults: {}, {},", JGroupsRaftConstants.DEFAULT_JGROUPSRAFT_CONFIG, stateMachine);
        return new RaftHandle(new JChannel(JGroupsRaftConstants.DEFAULT_JGROUPSRAFT_CONFIG).name(raftId), stateMachine)
                .raftId(raftId);
    }

    /**
     * Connect shared RaftHandle channel, called by producer and consumer.
     *
     * @throws Exception
     */
    public void connect() throws Exception {
        connectCount.incrementAndGet();
        LOG.trace("Connecting JGroups-raft Channel {} with cluster name: {}, raftHandle: {} and using config: {}",
                getEndpointUri(), clusterName, resolvedRaftHandle, channelProperties == null ? "default" : channelProperties);
        resolvedRaftHandle.channel().connect(clusterName);
    }

    /**
     * Disconnect shared RaftHandle channel, called by producer and consumer.
     */
    public void disconnect() {
        if (connectCount.decrementAndGet() == 0) {
            LOG.trace("Disconnecting JGroupsraft Channel {}", getEndpointUri());
            resolvedRaftHandle.channel().disconnect();
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
