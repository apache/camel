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
package org.apache.camel.component.zookeepermaster.policy;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.zookeepermaster.CamelNodeState;
import org.apache.camel.component.zookeepermaster.ContainerIdFactory;
import org.apache.camel.component.zookeepermaster.DefaultContainerIdFactory;
import org.apache.camel.component.zookeepermaster.ZookeeperGroupListenerSupport;
import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteAddedEvent;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link org.apache.camel.spi.RoutePolicy} to run the route in master/slave mode.
 * <p/>
 * <b>Important:</b> Make sure to set the route to autoStartup=false as the route lifecycle is controlled by this route
 * policy which will start/stop the route accordingly to being the master in the zookeeper cluster group.
 * <p/>
 * Use one policy instance per route. When the route is removed (or reloaded) the policy leaves the group, so another
 * node can become the master.
 */
@ManagedResource(description = "Managed MasterRoutePolicy")
public class MasterRoutePolicy extends RoutePolicySupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(MasterRoutePolicy.class);
    // delay before the master tries again to start a consumer whose start failed
    private static final long RETRY_DELAY_MILLIS = 5000;

    private CuratorFramework curator;
    private int maximumConnectionTimeout = 10 * 1000;
    private String zooKeeperUrl;
    private String zooKeeperPassword;
    private String zkRoot = "/camel/zookeepermaster/clusters/master";
    private String groupName;
    private ContainerIdFactory containerIdFactory = new DefaultContainerIdFactory();

    // state if the consumer has been started
    private final AtomicBoolean masterConsumer = new AtomicBoolean();
    // the leadership events come from the group thread (CHANGED) and the ZooKeeper connection thread (DISCONNECTED): the
    // consumer is started and stopped under this lock, and a start checks again under it that this node is the master
    private final Lock leadershipLock = new ReentrantLock();
    private volatile ScheduledExecutorService retryExecutor;
    private ScheduledFuture<?> retryTask;
    private volatile long retryDelay = RETRY_DELAY_MILLIS;
    // failed starts in a row, for the log
    private int failedStarts;

    // read by the group, Curator connection and retry threads
    private volatile ZookeeperGroupListenerSupport<CamelNodeState> groupListener;
    private volatile CamelNodeState thisNodeState;
    private CamelContext camelContext;
    // the route of the policy: set when the route is initialized, cleared when it is removed (under the service lock)
    private volatile Route route;
    private RouteAddedNotifier routeAddedNotifier;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getZkRoot() {
        return zkRoot;
    }

    /**
     * The root path to use in zookeeper where information is stored which nodes are master/slave etc. Will by default
     * use: /camel/zookeepermaster/clusters/master
     */
    public void setZkRoot(String zkRoot) {
        this.zkRoot = zkRoot;
    }

    @ManagedAttribute(description = "The name of the cluster group to use")
    public String getGroupName() {
        return groupName;
    }

    /**
     * The name of the cluster group to use
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public ContainerIdFactory getContainerIdFactory() {
        return containerIdFactory;
    }

    /**
     * To use a custom ContainerIdFactory for creating container ids.
     */
    public void setContainerIdFactory(ContainerIdFactory containerIdFactory) {
        this.containerIdFactory = containerIdFactory;
    }

    public CuratorFramework getCurator() {
        return curator;
    }

    /**
     * To use a custom configured CuratorFramework as connection to zookeeper ensemble.
     */
    public void setCurator(CuratorFramework curator) {
        this.curator = curator;
    }

    @ManagedAttribute(description = "Timeout in millis to use when connecting to the zookeeper ensemble")
    public int getMaximumConnectionTimeout() {
        return maximumConnectionTimeout;
    }

    /**
     * Timeout in millis to use when connecting to the zookeeper ensemble
     */
    public void setMaximumConnectionTimeout(int maximumConnectionTimeout) {
        this.maximumConnectionTimeout = maximumConnectionTimeout;
    }

    @ManagedAttribute(description = "The url for the zookeeper ensemble")
    public String getZooKeeperUrl() {
        return zooKeeperUrl;
    }

    /**
     * The url for the zookeeper ensemble
     */
    public void setZooKeeperUrl(String zooKeeperUrl) {
        this.zooKeeperUrl = zooKeeperUrl;
    }

    public String getZooKeeperPassword() {
        return zooKeeperPassword;
    }

    /**
     * The password to use when connecting to the zookeeper ensemble
     */
    public void setZooKeeperPassword(String zooKeeperPassword) {
        this.zooKeeperPassword = zooKeeperPassword;
    }

    @ManagedAttribute(description = "Are we connected to ZooKeeper")
    public boolean isConnected() {
        Group<CamelNodeState> group = currentGroup();
        return group != null && group.isConnected();
    }

    @ManagedAttribute(description = "Are we the master")
    public boolean isMaster() {
        Group<CamelNodeState> group = currentGroup();
        return group != null && group.isMaster();
    }

    @ManagedOperation(description = "Information about all the slaves")
    public String slaves() {
        Group<CamelNodeState> group = currentGroup();
        if (group == null) {
            return null;
        }
        try {
            return JsonMapper.builderWithJackson2Defaults()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .build()
                    .writeValueAsString(group.slaves());
        } catch (Exception e) {
            return null;
        }
    }

    @ManagedOperation(description = "Information about the last event in the cluster group")
    public String lastEvent() {
        Group<CamelNodeState> group = currentGroup();
        if (group == null) {
            return null;
        }
        Object event = group.getLastState();
        return event != null ? event.toString() : null;
    }

    @ManagedOperation(description = "Information about this node")
    public String thisNode() {
        return thisNodeState != null ? thisNodeState.toString() : null;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        lock.lock();
        try {
            // a route that is added to a running CamelContext (a route reload with the same or a new policy) joins the
            // group when it is added (routeAdded), as its consumer is not created yet
            this.route = route;
        } finally {
            lock.unlock();
        }
    }

    // the route was added to the running CamelContext, after its consumer was created
    private void routeAdded(Route added) {
        lock.lock();
        try {
            if (added == this.route && isRunAllowed() && groupListener == null) {
                joinGroup();
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Failed to join the group of: " + added.getEndpoint(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onRemove(Route route) {
        lock.lock();
        try {
            if (route != this.route) {
                return;
            }
            // the route is removed: leave the group, so that another node becomes the master and runs the route. The
            // policy is a service of the CamelContext and keeps running, so it joins again when the route is added again
            leaveGroup();
            this.route = null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onStart(Route route) {
        keepConsumerStoppedUnlessMaster(route);
    }

    @Override
    public void onResume(Route route) {
        keepConsumerStoppedUnlessMaster(route);
    }

    // the route was started or resumed (from JMX, the controlbus, a scheduled route policy, or as it is not set to
    // autoStartup=false), which started or resumed its consumer: a node that is not the master keeps it stopped, and
    // starts it when it is elected (a resumed consumer that is marked as started would not be started again then)
    private void keepConsumerStoppedUnlessMaster(Route route) {
        leadershipLock.lock();
        try {
            // a master that has not started its consumer yet starts it with its pending start (or retry)
            if (route != this.route || masterConsumer.get() || isLeader()) {
                return;
            }
            LOG.debug("Not the master. Keeping the consumer stopped: {}", route.getEndpoint());
            stopConsumer(route.getConsumer());
        } catch (Exception e) {
            getExceptionHandler().handleException("Failed to stop the consumer of: " + route.getEndpoint()
                                                  + " which only the master runs",
                    e);
        } finally {
            leadershipLock.unlock();
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(camelContext, "CamelContext");
        StringHelper.notEmpty("groupName", groupName);

        this.retryExecutor = camelContext.getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "ZooKeeperMasterRoutePolicyRetry");
        this.routeAddedNotifier = new RouteAddedNotifier();
        camelContext.getManagementStrategy().addEventNotifier(routeAddedNotifier);
        // a policy added to a running CamelContext is started before its route is initialized: it joins the group
        // when the route is added
        if (route != null) {
            joinGroup();
        }
    }

    // under the service lock: joins the group of the nodes that run the route
    private void joinGroup() throws Exception {
        String path = getCamelClusterPath(groupName);
        ZookeeperGroupListenerSupport<CamelNodeState> listener
                = new ZookeeperGroupListenerSupport<>(path, route.getEndpoint(), onLockOwned(), onDisconnected());
        listener.setCamelContext(camelContext);
        listener.setCurator(curator);
        listener.setMaximumConnectionTimeout(maximumConnectionTimeout);
        listener.setZooKeeperUrl(zooKeeperUrl);
        listener.setZooKeeperPassword(zooKeeperPassword);
        // set before it starts, as the group may tell it that this node is the master while it starts
        this.groupListener = listener;
        ServiceHelper.startService(listener);

        LOG.info("Attempting to become master for endpoint: {} in {} with singletonID: {}", route.getEndpoint(),
                getCamelContext(), getGroupName());
        thisNodeState = createNodeState(route);
        listener.updateState(thisNodeState);
    }

    // under the service lock: leaves the group, and this node is no longer the master
    private void leaveGroup() {
        ZookeeperGroupListenerSupport<CamelNodeState> listener = groupListener;
        if (listener == null) {
            return;
        }
        // the client the listener registered itself on (the curator of the policy, or one from the registry)
        CuratorFramework client = null;
        try {
            client = listener.getCurator();
        } catch (RuntimeException e) {
            // the listener did not start: it registered itself on the curator of the policy, if any
            client = curator;
        }
        // a start (or retry) that checks the leadership from now on does not start the consumer
        groupListener = null;
        // the group thread is interrupted before the leadership lock is taken, as in doStop: a consumer start in flight
        // on it holds the lock. Closing the group tells the listeners that it is disconnected, which stops the consumer
        try {
            ServiceHelper.stopAndShutdownServices(listener);
        } catch (Exception e) {
            LOG.warn("Failed to leave the group of: {}. This exception is ignored.", route.getEndpoint(), e);
        }
        // the group listener registered itself on a CuratorFramework of the user or the registry, which outlives it
        if (client != null) {
            client.getConnectionStateListenable().removeListener(listener);
        }
        leadershipLock.lock();
        try {
            thisNodeState = null;
            failedStarts = 0;
            cancelRetry();
            if (masterConsumer.getAndSet(false)) {
                // a start that completed while the group was closed
                stopConsumer(route.getConsumer());
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop master consumer: {}. This exception is ignored.", route.getEndpoint(), e);
        } finally {
            leadershipLock.unlock();
        }
        if (camelContext.isStopping()) {
            LOG.debug("Left the group as the route was removed: {}", route.getEndpoint());
        } else {
            LOG.info("Left the group as the route was removed: {}", route.getEndpoint());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (routeAddedNotifier != null) {
            camelContext.getManagementStrategy().removeEventNotifier(routeAddedNotifier);
            routeAddedNotifier = null;
        }

        // the retry thread and the group thread are interrupted first (as the group did before): a consumer start in
        // flight on them holds the leadership lock, and the stop must not wait for it to complete
        if (retryExecutor != null) {
            camelContext.getExecutorServiceManager().shutdownNow(retryExecutor);
            retryExecutor = null;
        }
        ServiceHelper.stopAndShutdownServices(groupListener);
        leadershipLock.lock();
        try {
            failedStarts = 0;
            cancelRetry();
            masterConsumer.set(false);
        } finally {
            leadershipLock.unlock();
        }
    }

    protected Runnable onLockOwned() {
        return () -> startMasterConsumer(false);
    }

    // starts the consumer, as this node holds the leadership (retry: the retry of a start that failed)
    private void startMasterConsumer(boolean retry) {
        leadershipLock.lock();
        try {
            if (retry) {
                retryTask = null;
            }
            // the event may be stale: the group sets connected to false before it tells the listeners that it is
            // disconnected, and the disconnect may have been handled since the event checked it
            Route current = this.route;
            if (!isRunAllowed() || masterConsumer.get() || !isLeader() || current == null
                    || current.getConsumer() == null) {
                return;
            }
            try {
                // ensure endpoint is also started
                LOG.info("Elected as master. Starting consumer: {}", current.getEndpoint());
                startConsumer(current.getConsumer());
            } catch (Exception e) {
                if (!isRunAllowed()) {
                    LOG.debug("Stopping while starting master consumer for: {}", current.getEndpoint(), e);
                    return;
                }
                // try again later, as long as this node is the master (no later event may come)
                failedStarts++;
                getExceptionHandler().handleException("Failed to start master consumer for: " + current.getEndpoint()
                                                      + " (attempt #" + failedStarts + "). Trying again in " + retryDelay
                                                      + " millis.",
                        e);
                // after the exception handler, as in MasterConsumer, so the retry cannot start while it still logs
                scheduleRetry();
                return;
            }
            failedStarts = 0;
            cancelRetry();
            masterConsumer.set(true);

            try {
                // Lets show we are starting the consumer.
                ZookeeperGroupListenerSupport<CamelNodeState> listener = groupListener;
                if (listener != null) {
                    thisNodeState = createNodeState(current);
                    thisNodeState.setStarted(true);
                    listener.updateState(thisNodeState);
                }
            } catch (Exception e) {
                getExceptionHandler().handleException("Failed to publish the started state of: " + current.getEndpoint(), e);
            }

            LOG.info("Elected as master. Consumer started: {}", current.getEndpoint());
        } finally {
            leadershipLock.unlock();
        }
    }

    private boolean isLeader() {
        Group<CamelNodeState> group = currentGroup();
        return group != null && group.isConnected() && group.isMaster();
    }

    // the group this node is in, or null: the listener is cleared when the route is removed, and has no group before it
    // is started
    private Group<CamelNodeState> currentGroup() {
        ZookeeperGroupListenerSupport<CamelNodeState> listener = groupListener;
        return listener != null ? listener.getGroup() : null;
    }

    // under the lock
    private void scheduleRetry() {
        ScheduledExecutorService executor = retryExecutor;
        if (retryTask == null && executor != null && isRunAllowed()) {
            try {
                retryTask = executor.schedule(() -> startMasterConsumer(true), retryDelay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // stopping
                LOG.debug("Cannot schedule a retry of the start of the master consumer, as the policy is stopping", e);
            }
        }
    }

    // under the lock
    private void cancelRetry() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }

    /**
     * The delay before the master tries again to start a consumer whose start failed (5 seconds). For tests.
     */
    void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    protected Runnable onDisconnected() {
        return () -> {
            leadershipLock.lock();
            try {
                masterConsumer.set(false);
                failedStarts = 0;
                cancelRetry();
                Route current = this.route;
                // the route was removed
                if (current == null) {
                    return;
                }
                try {
                    stopConsumer(current.getConsumer());
                } catch (Exception e) {
                    LOG.warn("Failed to stop master consumer: {}", current.getEndpoint(), e);
                }
            } finally {
                leadershipLock.unlock();
            }
        };
    }

    protected String getCamelClusterPath(String name) {
        String path = name;
        if (ObjectHelper.isNotEmpty(zkRoot)) {
            path = zkRoot + "/" + name;
        }
        return path;
    }

    private CamelNodeState createNodeState(Route route) {
        String containerId = getContainerIdFactory().newContainerId();
        CamelNodeState state = new CamelNodeState(getGroupName(), containerId);
        state.setConsumer(route.getEndpoint().getEndpointUri());
        return state;
    }

    // joins the group when the route of the policy is added to the running CamelContext
    private final class RouteAddedNotifier extends SimpleEventNotifierSupport implements NonManagedService {

        RouteAddedNotifier() {
            // only route events, so the notifier adds no cost to the exchanges
            setIgnoreCamelContextInitEvents(true);
            setIgnoreCamelContextEvents(true);
            setIgnoreServiceEvents(true);
            setIgnoreExchangeEvents(true);
            setIgnoreStepEvents(true);
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof RouteAddedEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            routeAdded(((RouteAddedEvent) event).getRoute());
        }
    }
}
