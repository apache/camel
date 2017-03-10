/**
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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.SuspendableService;
import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.component.zookeepermaster.group.GroupListener;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consumer which is only really active while it holds the master lock
 */
public class MasterConsumer extends DefaultConsumer implements GroupListener {
    private static final transient Logger LOG = LoggerFactory.getLogger(MasterConsumer.class);

    private final MasterEndpoint endpoint;
    private final Processor processor;
    private Consumer delegate;
    private SuspendableService delegateService;
    private final Group<CamelNodeState> singleton;

    public MasterConsumer(MasterEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        MasterComponent component = endpoint.getComponent();
        String path = component.getCamelClusterPath(endpoint.getSingletonId());
        this.singleton = component.createGroup(path);
        this.singleton.add(this);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        singleton.start();
        LOG.info("Attempting to become master for endpoint: " + endpoint + " in " + endpoint.getCamelContext() + " with singletonID: " + endpoint.getSingletonId());
        singleton.update(createNodeState());
    }

    private CamelNodeState createNodeState() {
        CamelNodeState state = new CamelNodeState(endpoint.getSingletonId());
        state.consumer = endpoint.getChildEndpoint().getEndpointUri();
        return state;
    }

    @Override
    protected void doStop() throws Exception {
        try {
            stopConsumer();
        } finally {
            singleton.close();
        }
    }

    protected void stopConsumer() throws Exception {
        ServiceHelper.stopAndShutdownServices(delegate);
        ServiceHelper.stopAndShutdownServices(endpoint.getChildEndpoint());
        delegate = null;
        delegateService = null;
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

    @Override
    public void groupEvent(Group group, GroupEvent event) {
        switch (event) {
        case CONNECTED:
            break;
        case CHANGED:
            if (singleton.isConnected()) {
                if (singleton.isMaster()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Master/Standby endpoint is Master for:  " + endpoint + " in " + endpoint.getCamelContext());
                    }
                    onLockOwned();
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Master/Standby endpoint is Standby for: " + endpoint + " in " + endpoint.getCamelContext());
                    }
                }
            }
            break;
        case DISCONNECTED:
            try {
                LOG.info("Disconnecting as Master. Stopping consumer: {}", endpoint.getChildEndpoint());
                stopConsumer();
            } catch (Exception e) {
                LOG.error("Failed to stop master consumer for: " + endpoint + ". Reason: " + e, e);
            }
            break;
        default:
            // noop
        }

    }

    protected void onLockOwned() {
        if (delegate == null) {
            try {
                // ensure endpoint is also started
                LOG.info("Elected as master. Starting consumer: {}", endpoint.getChildEndpoint());
                ServiceHelper.startService(endpoint.getChildEndpoint());

                delegate = endpoint.getChildEndpoint().createConsumer(processor);
                delegateService = null;
                if (delegate instanceof SuspendableService) {
                    delegateService = (SuspendableService) delegate;
                }

                // Lets show we are starting the consumer.
                CamelNodeState nodeState = createNodeState();
                nodeState.started = true;
                singleton.update(nodeState);

                ServiceHelper.startService(delegate);
            } catch (Exception e) {
                LOG.error("Failed to start master consumer for: " + endpoint + ". Reason: " + e, e);
            }

            LOG.info("Elected as master. Consumer started: {}", endpoint.getChildEndpoint());
        }
    }

}
