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
package org.apache.camel.component.zookeepermaster;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.SuspendableService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consumer which is only really active while it holds the master lock
 */
@ManagedResource(description = "Managed ZooKeeper Master Consumer")
public class MasterConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(MasterConsumer.class);

    private ZookeeperGroupListenerSupport groupListener;
    private final MasterEndpoint endpoint;
    private final Processor processor;
    private Consumer delegate;
    private SuspendableService delegateService;
    private volatile CamelNodeState thisNodeState;

    public MasterConsumer(MasterEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
    }

    @ManagedAttribute(description = "Are we connected to ZooKeeper")
    public boolean isConnected() {
        return groupListener.getGroup().isConnected();
    }

    @ManagedAttribute(description = "Are we the master")
    public boolean isMaster() {
        return groupListener.getGroup().isMaster();
    }

    @ManagedOperation(description = "Information about all the slaves")
    public String slaves() {
        try {
            return new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .writeValueAsString(groupListener.getGroup().slaves());
        } catch (Exception e) {
            return null;
        }
    }

    @ManagedOperation(description = "Information about the last event in the cluster group")
    public String lastEvent() {
        Object event = groupListener.getGroup().getLastState();
        return event != null ? event.toString() : null;
    }

    @ManagedOperation(description = "Information about this node")
    public String thisNode() {
        return thisNodeState != null ? thisNodeState.toString() : null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String path = endpoint.getComponent().getCamelClusterPath(endpoint.getGroupName());
        this.groupListener = new ZookeeperGroupListenerSupport(path, endpoint, onLockOwned(), onDisconnected());
        this.groupListener.setCamelContext(endpoint.getCamelContext());
        this.groupListener.setZooKeeperUrl(endpoint.getComponent().getZooKeeperUrl());
        this.groupListener.setZooKeeperPassword(endpoint.getComponent().getZooKeeperPassword());
        this.groupListener.setCurator(endpoint.getComponent().getCurator());
        this.groupListener.setMaximumConnectionTimeout(endpoint.getComponent().getMaximumConnectionTimeout());
        ServiceHelper.startService(groupListener);

        LOG.info("Attempting to become master for endpoint: " + endpoint + " in " + endpoint.getCamelContext() + " with singletonID: " + endpoint.getGroupName());
        thisNodeState = createNodeState();
        groupListener.updateState(thisNodeState);
    }

    @Override
    protected void doStop() throws Exception {
        try {
            stopConsumer();
        } finally {
            ServiceHelper.stopAndShutdownServices(groupListener);
        }
        super.doStop();
    }

    private CamelNodeState createNodeState() {
        String containerId = endpoint.getComponent().getContainerIdFactory().newContainerId();
        CamelNodeState state = new CamelNodeState(endpoint.getGroupName(), containerId);
        state.setConsumer(endpoint.getConsumerEndpoint().getEndpointUri());
        return state;
    }

    private void stopConsumer() throws Exception {
        ServiceHelper.stopAndShutdownServices(delegate);
        ServiceHelper.stopAndShutdownServices(endpoint.getConsumerEndpoint());
        delegate = null;
        delegateService = null;
        thisNodeState = null;
    }

    @Override
    protected void doResume() throws Exception {
        if (delegateService != null) {
            delegateService.resume();
        }
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (delegateService != null) {
            delegateService.suspend();
        }
        super.doSuspend();
    }

    protected Runnable onLockOwned() {
        return () -> {
            if (delegate == null) {
                try {
                    // ensure endpoint is also started
                    LOG.info("Elected as master. Starting consumer: {}", endpoint.getConsumerEndpoint());
                    ServiceHelper.startService(endpoint.getConsumerEndpoint());

                    delegate = endpoint.getConsumerEndpoint().createConsumer(processor);
                    delegateService = null;
                    if (delegate instanceof SuspendableService) {
                        delegateService = (SuspendableService) delegate;
                    }

                    // Lets show we are starting the consumer.
                    thisNodeState = createNodeState();
                    thisNodeState.setStarted(true);
                    groupListener.updateState(thisNodeState);

                    ServiceHelper.startService(delegate);
                } catch (Exception e) {
                    LOG.error("Failed to start master consumer for: {}", endpoint, e);
                }

                LOG.info("Elected as master. Consumer started: {}", endpoint.getConsumerEndpoint());
            }
        };
    }

    protected Runnable onDisconnected() {
        return () -> {
            try {
                stopConsumer();
            } catch (Exception e) {
                LOG.warn("Failed to stop master consumer for: {}", endpoint, e);
            }
        };
    }

}
