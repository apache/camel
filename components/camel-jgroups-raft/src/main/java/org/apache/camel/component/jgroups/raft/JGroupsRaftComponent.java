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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Endpoint;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.jgroups.raft.RaftHandle;
import org.jgroups.raft.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component providing support for JGroups-raft leader election and shared state machine implementation
 * ({@code org.jgroups.raft.RaftHandle}).
 */
@Component("jgroups-raft")
public class JGroupsRaftComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsRaftComponent.class);

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

    // the connected producers and consumers of each RaftHandle that the endpoints did not create (such as the
    // raftHandle of this component), as several endpoints use such a handle
    private final Map<RaftHandle, Integer> sharedHandleUsers = new IdentityHashMap<>();
    // the handles that the endpoints did not create and connected, closed when this component stops
    private final Set<RaftHandle> sharedHandles = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Lock sharedHandleLock = new ReentrantLock();

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

    /**
     * Connects a RaftHandle that several endpoints can use, for a producer or a consumer of one of them.
     */
    void connectSharedHandle(RaftHandle sharedHandle, String clusterName) throws Exception {
        sharedHandleLock.lock();
        try {
            sharedHandle.channel().connect(clusterName);
            sharedHandleUsers.merge(sharedHandle, 1, Integer::sum);
            sharedHandles.add(sharedHandle);
        } finally {
            sharedHandleLock.unlock();
        }
    }

    /**
     * Disconnects a RaftHandle that several endpoints can use, when its last producer or consumer stops.
     */
    void disconnectSharedHandle(RaftHandle sharedHandle) {
        sharedHandleLock.lock();
        try {
            Integer users = sharedHandleUsers.get(sharedHandle);
            if (users == null) {
                return;
            }
            if (users > 1) {
                sharedHandleUsers.put(sharedHandle, users - 1);
            } else {
                sharedHandleUsers.remove(sharedHandle);
                LOG.trace("Disconnecting JGroupsraft Channel {}", sharedHandle.channel());
                sharedHandle.channel().disconnect();
            }
        } finally {
            sharedHandleLock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        List<RaftHandle> handles;
        sharedHandleLock.lock();
        try {
            handles = new ArrayList<>(sharedHandles);
            sharedHandleUsers.clear();
            sharedHandles.clear();
        } finally {
            sharedHandleLock.unlock();
        }
        // the raftHandle of the component is used by all its endpoints, so it is closed with the component, as is a
        // handle given to an endpoint
        if (raftHandle != null && !handles.contains(raftHandle)) {
            handles.add(raftHandle);
        }
        for (RaftHandle handle : handles) {
            // each handle is closed, also when closing another one fails
            try {
                if (handle.channel() != null) {
                    handle.channel().close();
                }
            } catch (Exception e) {
                LOG.warn("Error closing the channel of the RaftHandle {}: {}", handle, e.getMessage(), e);
            }
            try {
                if (handle.log() != null) {
                    handle.log().close();
                }
            } catch (Exception e) {
                LOG.warn("Error closing the log of the RaftHandle {}: {}", handle, e.getMessage(), e);
            }
        }
    }

    //TODO: implement a org.jgroups.protocols.raft.StateMachine as a Camel Consumer.
}
