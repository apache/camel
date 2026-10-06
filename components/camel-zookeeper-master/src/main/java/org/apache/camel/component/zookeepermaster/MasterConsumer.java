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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    // delay before the master tries again to start a consumer whose start failed
    private static final long RETRY_DELAY_MILLIS = 5000;

    private ZookeeperGroupListenerSupport groupListener;
    private final MasterEndpoint endpoint;
    private final Processor processor;
    private volatile Consumer delegate;
    private volatile SuspendableService delegateService;
    private volatile CamelNodeState thisNodeState;
    // the leadership events come from the group thread (CHANGED) and the ZooKeeper connection thread
    // (DISCONNECTED), and the consumer can be stopped meanwhile: the delegate and these fields are guarded by the lock
    // (delegate and delegateService are also volatile, as doSuspend/doResume read them without it)
    private final Lock leadershipLock = new ReentrantLock();
    // incremented when the leadership may be lost or the consumer stops: a start that began before does not publish
    // its consumer, it stops it
    private long generation;
    private boolean starting;
    // the state published when this node starts its consumer, once per leadership term (a new state is a change of
    // the group, which would trigger another leadership event)
    private CamelNodeState startedState;
    private ScheduledExecutorService retryExecutor;
    private ScheduledFuture<?> retryTask;
    private volatile long retryDelay = RETRY_DELAY_MILLIS;
    // failed starts in a row, for the log
    private int failedStarts;

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
        this.retryExecutor = endpoint.getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "ZooKeeperMasterRetry");
        ServiceHelper.startService(groupListener);

        LOG.info("Attempting to become master for endpoint: {} in {} with singletonID: {}", endpoint,
                endpoint.getCamelContext(), endpoint.getGroupName());
        thisNodeState = createNodeState();
        groupListener.updateState(thisNodeState);
    }

    @Override
    protected void doStop() throws Exception {
        try {
            leadershipLock.lock();
            try {
                generation++;
                failedStarts = 0;
                cancelRetry();
                stopConsumer();
            } finally {
                leadershipLock.unlock();
            }
        } finally {
            ServiceHelper.stopAndShutdownServices(groupListener);
            if (retryExecutor != null) {
                endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(retryExecutor);
                retryExecutor = null;
            }
        }
        super.doStop();
    }

    private CamelNodeState createNodeState() {
        String containerId = endpoint.getComponent().getContainerIdFactory().newContainerId();
        CamelNodeState state = new CamelNodeState(endpoint.getGroupName(), containerId);
        state.setConsumer(endpoint.getConsumerEndpoint().getEndpointUri());
        return state;
    }

    private void stopConsumer() {
        ServiceHelper.stopAndShutdownServices(delegate);
        ServiceHelper.stopAndShutdownServices(endpoint.getConsumerEndpoint());
        delegate = null;
        delegateService = null;
        thisNodeState = null;
        startedState = null;
    }

    /**
     * The delay before the master tries again to start a consumer whose start failed (5 seconds). For tests.
     */
    void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    private void cancelRetry() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
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
        return () -> startConsumer(-1);
    }

    /**
     * Starts the delegate consumer, as this node holds the leadership.
     *
     * @param retryGeneration -1 for a leadership event, else the generation of the start that failed
     */
    private void startConsumer(long retryGeneration) {
        long startGeneration;
        CamelNodeState state = null;
        leadershipLock.lock();
        try {
            if (retryGeneration >= 0 && retryGeneration == generation) {
                // this is the scheduled task (a task of an older generation was cancelled, and a newer one may be
                // scheduled meanwhile)
                retryTask = null;
            }
            if (delegate != null || starting || retryTask != null || !isRunAllowed()
                    || retryGeneration >= 0 && retryGeneration != generation || !isLeader()) {
                return;
            }
            starting = true;
            startGeneration = generation;
            if (startedState == null) {
                startedState = createNodeState();
                startedState.setStarted(true);
                state = startedState;
            }
        } finally {
            leadershipLock.unlock();
        }

        // the consumer is created and started without holding the lock, as a start can take long (it may connect to
        // a broker or a server), and the lock is needed to handle a disconnect or a stop meanwhile
        Consumer consumer = null;
        Exception cause = null;
        try {
            // ensure endpoint is also started
            LOG.info("Elected as master. Starting consumer: {}", endpoint.getConsumerEndpoint());
            ServiceHelper.startService(endpoint.getConsumerEndpoint());

            consumer = endpoint.getConsumerEndpoint().createConsumer(processor);

            // Lets show we are starting the consumer.
            if (state != null) {
                thisNodeState = state;
                groupListener.updateState(state);
            }

            ServiceHelper.startService(consumer);
        } catch (Exception e) {
            cause = e;
        }

        leadershipLock.lock();
        try {
            starting = false;
            if (startGeneration != generation || !isRunAllowed()) {
                // disconnected (no longer the master) or stopping while the consumer started: no event is coming to
                // stop it later
                LOG.info("Lost the leadership or stopping while the consumer started. Stopping consumer: {}",
                        endpoint.getConsumerEndpoint());
                ServiceHelper.stopAndShutdownServices(consumer);
                // a leadership event that came meanwhile (reconnected, or the route was started again) was skipped,
                // as this start was in flight: start again if this node is the master now
                if (isRunAllowed() && isLeader() && delegate == null && retryTask == null) {
                    LOG.info("Elected as master again while the consumer started. Starting consumer again: {}",
                            endpoint.getConsumerEndpoint());
                    scheduleStart(0);
                }
            } else if (cause != null) {
                // forget the consumer (its start stopped it) and try again later, as long as this node is the master
                failedStarts++;
                getExceptionHandler().handleException("Failed to start master consumer for: " + endpoint + " (attempt #"
                                                      + failedStarts + "). Trying again in " + retryDelay + " millis.",
                        cause);
                scheduleStart(retryDelay);
            } else {
                failedStarts = 0;
                delegate = consumer;
                delegateService = consumer instanceof SuspendableService suspendable ? suspendable : null;
                LOG.info("Elected as master. Consumer started: {}", endpoint.getConsumerEndpoint());
            }
        } finally {
            leadershipLock.unlock();
        }
    }

    // under the lock: starts the consumer for the current generation, unless a disconnect or a stop comes first
    private void scheduleStart(long delay) {
        if (retryExecutor != null) {
            final long scheduledGeneration = generation;
            retryTask = retryExecutor.schedule(() -> startConsumer(scheduledGeneration), delay, TimeUnit.MILLISECONDS);
        }
    }

    private boolean isLeader() {
        // the group sets connected to false before it tells the listeners that it is disconnected
        return groupListener != null && groupListener.getGroup() != null && groupListener.getGroup().isConnected()
                && groupListener.getGroup().isMaster();
    }

    protected Runnable onDisconnected() {
        return () -> {
            leadershipLock.lock();
            try {
                generation++;
                failedStarts = 0;
                cancelRetry();
                stopConsumer();
            } catch (Exception e) {
                LOG.warn("Failed to stop master consumer for: {}", endpoint, e);
            } finally {
                leadershipLock.unlock();
            }
        };
    }

}
