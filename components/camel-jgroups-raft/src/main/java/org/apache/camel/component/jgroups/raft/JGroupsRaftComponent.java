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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.jgroups.raft.RaftHandle;
import org.jgroups.raft.StateMachine;

/**
 * Component providing support for JGroups-raft leader election and shared state machine implementation
 * ({@code org.jgroups.raft.RaftHandle}).
 */
@Component("jgroups-raft")
public class JGroupsRaftComponent extends DefaultComponent {
    @UriParam
    @Metadata(defaultValue = "null")
    private RaftHandle raftHandle;
    @UriParam
    @Metadata(defaultValue = "NopStateMachine")
    private StateMachine stateMachine = new NopStateMachine();
    @UriParam
    @Metadata(required = true)
    private String raftId;
    @UriParam
    @Metadata(defaultValue = "raft.xml")
    private String channelProperties;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
        return new JGroupsRaftEndpoint(
                uri, remaining, this, raftId, channelProperties, stateMachine, raftHandle);
    }

    public RaftHandle getRaftHandle() {
        return raftHandle;
    }

    /**
     * RaftHandle to use.
     */
    public void setRaftHandle(RaftHandle raftHandle) {
        this.raftHandle = raftHandle;
    }

    public StateMachine getStateMachine() {
        return stateMachine;
    }

    /**
     *
     * StateMachine to use.
     */
    public void setStateMachine(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public String getRaftId() {
        return raftId;
    }

    /**
     *
     * Unique raftId to use.
     */
    public void setRaftId(String raftId) {
        this.raftId = raftId;
    }

    public String getChannelProperties() {
        return channelProperties;
    }

    /**
     * Specifies configuration properties of the RaftHandle JChannel used by the endpoint (ignored if raftHandle ref is
     * provided).
     */
    public void setChannelProperties(String channelProperties) {
        this.channelProperties = channelProperties;
    }

    //TODO: implement a org.jgroups.protocols.raft.StateMachine as a Camel Consumer.
}
