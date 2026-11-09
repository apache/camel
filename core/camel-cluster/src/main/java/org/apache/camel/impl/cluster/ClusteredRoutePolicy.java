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
package org.apache.camel.impl.cluster;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.CamelContextResumedEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;
import org.apache.camel.spi.CamelEvent.RouteAddedEvent;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.cluster.ClusterServiceHelper;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReferenceCount;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Clustered Route policy")
public final class ClusteredRoutePolicy extends RoutePolicySupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredRoutePolicy.class);
    private static final String THREAD_NAME = "ClusteredRoutePolicy";
    // how long the policy thread waits for another leadership change before it exits
    private static final long LEADERSHIP_THREAD_KEEP_ALIVE_MILLIS = 1000;
    // the delay before the leader tries again to start a route whose start failed
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);

    private final AtomicBoolean leader;
    private final Set<Route> autoStartupRoutes;
    private final Set<Route> startedRoutes;
    private final Set<Route> stoppedRoutes;
    private final ReferenceCount refCount;
    private final CamelClusterEventListener.Leadership leadershipEventListener;
    private final CamelContextStartupListener listener;
    private final AtomicBoolean contextStarted;

    private final String namespace;
    private final CamelClusterService.Selector clusterServiceSelector;
    private final Lock lock;
    private final Lock retainLock;
    private final AtomicReference<CamelClusterView> clusterView;
    private final AtomicBoolean leadershipChangePending;
    // the leadership is applied again to the routes (and not only on a change): a route of this policy was added to
    // the CamelContext, a route failed to start or stop, or the CamelContext resumed
    private final AtomicBoolean reapply;
    private final AtomicBoolean retryScheduled;
    private CamelClusterService clusterService;
    private volatile boolean startManagedRoutesEarly;
    private volatile boolean initialDelayElapsed;
    // the CamelContext was resumed once (a policy attached after the start does not get the started event)
    private volatile boolean contextResumed;

    private Duration initialDelay;
    private volatile ExecutorService leadershipExecutor;
    private volatile ScheduledExecutorService initialDelayExecutor;
    private volatile ScheduledExecutorService retryExecutor;
    private volatile Duration retryDelay = DEFAULT_RETRY_DELAY;
    // failed starts in a row per route id, for the log
    private final Map<String, Integer> failedStarts = new ConcurrentHashMap<>();

    private CamelContext camelContext;

    private ClusteredRoutePolicy(CamelClusterService clusterService, CamelClusterService.Selector clusterServiceSelector,
                                 String namespace) {
        this.namespace = namespace;
        this.clusterService = clusterService;
        this.clusterServiceSelector = clusterServiceSelector;

        ObjectHelper.notNull(namespace, "Namespace");

        this.leadershipEventListener = new CamelClusterLeadershipListener();

        this.lock = new ReentrantLock();
        this.retainLock = new ReentrantLock();
        this.leadershipChangePending = new AtomicBoolean();
        this.reapply = new AtomicBoolean();
        this.retryScheduled = new AtomicBoolean();
        // the routes are started and stopped on the policy thread, while routes are added and removed by the caller
        this.stoppedRoutes = ConcurrentHashMap.newKeySet();
        this.startedRoutes = ConcurrentHashMap.newKeySet();
        this.autoStartupRoutes = ConcurrentHashMap.newKeySet();
        this.clusterView = new AtomicReference<>();
        this.leader = new AtomicBoolean();
        this.contextStarted = new AtomicBoolean();
        this.initialDelay = Duration.ofMillis(0);

        try {
            this.listener = new CamelContextStartupListener();
            this.listener.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Cleanup the policy when all the routes it manages have been removed
        // so a single policy instance can be shared among routes.
        // Acquire cluster view once a route is added to the policy
        this.refCount = ReferenceCount.on(this::retainClusterView, this::releaseClusterView);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        if (this.camelContext == camelContext) {
            return;
        }

        if (this.camelContext != null) {
            throw new IllegalStateException(
                    "CamelContext should not be changed: current=" + this.camelContext + ", new=" + camelContext);
        }

        try {
            this.camelContext = camelContext;
            this.camelContext.addStartupListener(this.listener);
            this.camelContext.getManagementStrategy().addEventNotifier(this.listener);
            this.leadershipExecutor = camelContext.getExecutorServiceManager()
                    .newThreadPool(this, THREAD_NAME, newLeadershipThreadPoolProfile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * The delay before this node, while it is the leader, tries again to start a route whose start failed (5 seconds by
     * default). It must be positive.
     */
    public void setRetryDelay(Duration retryDelay) {
        ObjectHelper.notNull(retryDelay, "retryDelay");
        if (retryDelay.toMillis() < 1) {
            throw new IllegalArgumentException("retryDelay must be positive: " + retryDelay);
        }
        this.retryDelay = retryDelay;
    }

    // ****************************************************
    // life-cycle
    // ****************************************************

    private ServiceStatus getStatus(Route route) {
        if (camelContext != null) {
            ServiceStatus answer = camelContext.getRouteController().getRouteStatus(route.getId());
            if (answer == null) {
                answer = ServiceStatus.Stopped;
            }
            return answer;
        }
        return null;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        // Increase number of managed routes by this policy, acquire policy view on first run
        retainLock.lock();
        try {
            this.refCount.retain();
        } finally {
            retainLock.unlock();
        }

        if (route.isAutoStartup()) {
            autoStartupRoutes.add(route);
        }

        if (camelContext.isStarted() && isLeader()) {
            // when camel context is already started, and we add new routes
            // then let the route controller start the route as usual (no need to mark as auto startup false)
            startedRoutes.add(route);
        } else {
            LOG.info("Route managed by {}. Setting route {} AutoStartup flag to false.", getClass(), route.getId());
            route.setAutoStartup(false);
            this.stoppedRoutes.add(route);
        }

        if (camelContext.isStarted() && initialDelay != null && initialDelay.toMillis() > 0) {
            // A policy attached to a CamelContext that has already started gets neither the started event nor the
            // fully started callback, so its initial delay starts now (it applies only once). This is done here and
            // not when the CamelContext is set, as the initial delay may be set after that. Only the timer is
            // scheduled here: the policy lock is not taken, as the caller may hold the CamelContext route lock.
            listener.onCamelContextStarted();
        }

        startManagedRoutes();
    }

    @Override
    protected void doInit() throws Exception {
        if (clusterService == null) {
            clusterService = ClusterServiceHelper.lookupService(camelContext, clusterServiceSelector)
                    .orElseThrow(() -> new IllegalStateException("CamelCluster service not found"));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("ClusteredRoutePolicy {} is using ClusterService instance {} (id={}, type={})", this, clusterService,
                    clusterService.getId(),
                    clusterService.getClass().getName());
        }
    }

    @Override
    public void onRemove(Route route) {
        // Decrease number of managed routes, release view once there are no route left
        retainLock.lock();
        try {
            refCount.release();
        } finally {
            retainLock.unlock();
        }
        autoStartupRoutes.remove(route);
        failedStarts.remove(route.getId());
        // a route added later with the same id must not be started or stopped by this policy
        startedRoutes.remove(route);
        stoppedRoutes.remove(route);
    }

    @Override
    protected void doShutdown() throws Exception {
        retainLock.lock();
        try {
            releaseClusterView();
        } finally {
            retainLock.unlock();
        }
        removeCamelEventListeners();
    }

    // ****************************************************
    // Management
    // ****************************************************

    private void removeCamelEventListeners() {
        if (camelContext != null) {
            camelContext.getManagementStrategy().removeEventNotifier(listener);
            ExecutorService executor = leadershipExecutor;
            if (executor != null) {
                camelContext.getExecutorServiceManager().shutdownNow(executor);
            }
            ScheduledExecutorService scheduler = initialDelayExecutor;
            if (scheduler != null) {
                initialDelayExecutor = null;
                camelContext.getExecutorServiceManager().shutdownNow(scheduler);
            }
            // no retry is scheduled from now on
            retryScheduled.set(true);
            ScheduledExecutorService retry = retryExecutor;
            if (retry != null) {
                retryExecutor = null;
                camelContext.getExecutorServiceManager().shutdownNow(retry);
            }
        }
    }

    // The view and the cluster service are called without holding the policy lock. The view holds its own lock while
    // it notifies the listeners, and the policy thread holds the policy lock while it starts or stops routes, which
    // needs the CamelContext route lock. onRemove (and so releaseClusterView) can run while the caller holds that route
    // lock, for example CamelContext.removeRoute, so taking the policy lock here could deadlock (CAMEL-24545).
    //
    // Retain and release are serialized by the retain lock instead, so a route added and a route removed at the same
    // time on a shared policy cannot release the view that has just been retained. Only the threads adding and
    // removing routes (and the shutdown) take the retain lock, and they never wait for the policy lock or the policy
    // thread while they hold it.

    private void retainClusterView() {
        try {
            CamelClusterView view = clusterService.getView(namespace);
            clusterView.set(view);
            // Take the current leadership right away, as the listener applies it asynchronously: onInit uses it to
            // decide whether the route controller can start the route. No route is managed yet, so there is nothing
            // to start or stop here.
            leader.set(view.getLocalMember().isLeader());
            view.addEventListener(leadershipEventListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void releaseClusterView() {
        CamelClusterView view = clusterView.getAndSet(null);
        try {
            if (view != null) {
                // Remove event listener
                view.removeEventListener(leadershipEventListener);

                // If all the routes have been removed then the view and its
                // resources can eventually be released.
                view.getClusterService().releaseView(view);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // the routes managed by this policy have been removed or are being shut down, so there is nothing to stop
            leader.set(false);
        }
    }

    @ManagedAttribute(description = "Is this route the master or a slave")
    public boolean isLeader() {
        return leader.get();
    }

    // ****************************************************
    // Route managements
    // ****************************************************

    private void setLeader() {
        lock.lock();
        try {
            // the leadership is read when the change is applied, not when it was handed over, so the last change wins
            CamelClusterView view = clusterView.get();
            if (view == null) {
                // the view has been released in the meantime
                return;
            }

            if (camelContext.isStopping()) {
                // The CamelContext stops all its routes, starting with their consumers, so neither a leadership taken
                // nor a leadership lost is applied: a route must not be started now, and stopping it here would run a
                // second shutdown of the route next to the one of the CamelContext.
                LOG.debug("Ignoring leadership change as CamelContext is stopping");
                return;
            }

            // a route of this policy was added to the CamelContext since the last change
            boolean added = reapply.getAndSet(false);
            boolean isLeader = view.getLocalMember().isLeader();
            if (isLeader && leader.compareAndSet(false, isLeader)) {
                LOG.debug("Leadership taken");
                startManagedRoutes();
            } else if (!isLeader && leader.getAndSet(isLeader)) {
                LOG.debug("Leadership lost");
                stopManagedRoutes();
            } else if (added) {
                // a route that could not be started when the leadership was taken (not added to the CamelContext yet,
                // or its start failed), or that failed to stop when it was lost: doStartManagedRoutes only starts the
                // routes that are still stopped, doStopManagedRoutes only stops the routes that are still started
                if (isLeader) {
                    startManagedRoutes();
                } else {
                    stopManagedRoutes();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void startManagedRoutes() {
        if (isLeader()) {
            doStartManagedRoutes();
        } else {
            // If the leadership has been lost in the meanwhile, stop any
            // eventually started route
            doStopManagedRoutes();
        }
    }

    private void doStartManagedRoutes() {
        // if we are currently starting up Camel context then defer starting routes till its fully started
        if (camelContext.isStarting()) {
            if (contextStarted.get() || contextResumed) {
                // the CamelContext is resuming (its status is starting until the resume completed, and the resumed
                // event comes before that): the started event does not come again, so try again later
                LOG.debug("Will start managed routes once camel context has resumed");
                scheduleRetry();
                return;
            }
            LOG.debug("Will defer starting managed routes until camel context is fully started");
            startManagedRoutesEarly = true;
            return;
        }
        // a route cannot be started while the CamelContext is suspended (startRoute does nothing then): the leadership is
        // applied again when the CamelContext resumes (CamelContextResumedEvent)
        if (camelContext.isSuspending() || camelContext.isSuspended()) {
            LOG.debug("Will defer starting managed routes until camel context is resumed");
            return;
        }
        // and if the initial delay has not elapsed yet, then defer starting routes till it has
        if (!initialDelayElapsed && initialDelay != null && initialDelay.toMillis() > 0) {
            LOG.debug("Will defer starting managed routes until the initial delay {} has elapsed", initialDelay);
            startManagedRoutesEarly = true;
            return;
        }

        if (!isRunAllowed()) {
            if (isNew() || isBuild() || isInit()) {
                // the leadership was taken before this policy started (a cluster service started on its own, with a
                // view that is already the leader): start the routes once the CamelContext has started. A policy that
                // is stopping or stopped does not start them
                LOG.debug("Will defer starting managed routes until the policy and the camel context are started");
                startManagedRoutesEarly = true;
            }
            return;
        }

        boolean failed = false;
        // each route on its own: a route that fails to start stays with the stopped routes, and is tried again while
        // this node is the leader
        for (Route route : stoppedRoutes) {
            ServiceStatus status = camelContext.getRouteController().getRouteStatus(route.getId());
            if (status == null) {
                // the route is not added to the CamelContext yet: starting it now would do nothing, so it is
                // started when it is added (RouteAddedEvent)
                LOG.debug("Will defer starting route '{}' until it is added to the CamelContext", route.getId());
                continue;
            }
            boolean autostart = autoStartupRoutes.contains(route);

            if (status.isStartable() && autostart) {
                LOG.debug("Starting route '{}'", route.getId());
                try {
                    camelContext.getRouteController().startRoute(route.getId());
                } catch (Exception e) {
                    failed = true;
                    int attempt = failedStarts.merge(route.getId(), 1, Integer::sum);
                    handleFailure("Failed to start route " + route.getId() + " (attempt #" + attempt + "). Trying again in "
                                  + retryDelay.toMillis() + " millis while this node is the leader.",
                            e);
                    continue;
                }
                Integer failures = failedStarts.remove(route.getId());
                if (failures != null) {
                    LOG.info("Route {} started after {} failed attempt(s)", route.getId(), failures);
                }

                startedRoutes.add(route);
                if (camelContext.getRoute(route.getId()) != route
                        || route instanceof BaseService service && service.isShutdown()) {
                    // the route was removed while it was started (startRoute waited for the removal, and did
                    // nothing): a route added later with the same id must not be stopped by this policy. Checked
                    // after the add, as a removed route is shut down before onRemove forgets it
                    LOG.debug("Route '{}' was removed while it was being started", route.getId());
                    startedRoutes.remove(route);
                }
            }
        }

        stoppedRoutes.removeAll(startedRoutes);
        if (failed) {
            scheduleRetry();
        }
    }

    // the policy thread applies the leadership to the routes again after the retry delay (starts the stopped routes if
    // this node is still the leader, stops the started ones if not). A timer thread per retry, which ends once it fired,
    // so a policy does not keep a thread (a policy per route with ClusteredRoutePolicyFactory)
    private void scheduleRetry() {
        if (!isRunAllowed() || retryScheduled.getAndSet(true)) {
            return;
        }
        ScheduledExecutorService scheduler
                = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, THREAD_NAME + "Retry");
        retryExecutor = scheduler;
        try {
            scheduler.schedule(() -> {
                try {
                    retryExecutor = null;
                    camelContext.getExecutorServiceManager().shutdown(scheduler);
                } finally {
                    retryScheduled.set(false);
                    reapply.set(true);
                    handOverLeadershipChange();
                }
            }, retryDelay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // the policy is being shut down
            LOG.debug("Not trying again as ClusteredRoutePolicy for namespace {} is shut down", namespace);
        }
    }

    private void handleFailure(String message, Exception e) {
        if (getExceptionHandler() != null) {
            getExceptionHandler().handleException(message, e);
        } else {
            LOG.warn(message, e);
        }
    }

    private void stopManagedRoutes() {
        if (isLeader()) {
            // If became a leader in the meanwhile, start any eventually stopped
            // route
            doStartManagedRoutes();
        } else {
            doStopManagedRoutes();
        }
    }

    private void doStopManagedRoutes() {
        if (!isRunAllowed()) {
            return;
        }

        boolean stopFailed = false;
        // each route on its own, so a route that fails to stop does not keep the others running
        for (Route route : startedRoutes) {
            ServiceStatus status = getStatus(route);
            if (status != null && status.isStoppable()) {
                LOG.debug("Stopping route '{}'", route.getId());
                try {
                    stopRoute(route);
                } catch (Exception e) {
                    handleFailure("Failed to stop route " + route.getId(), e);
                    if (getStatus(route).isStoppable()) {
                        // still running: it stays with the started routes, and the stop is tried again
                        stopFailed = true;
                        continue;
                    }
                }

                stoppedRoutes.add(route);
            }
        }

        startedRoutes.removeAll(stoppedRoutes);
        if (stopFailed) {
            scheduleRetry();
        }
    }

    private void onCamelContextStarted() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Apply cluster policy (stopped-routes='{}', started-routes='{}')",
                    stoppedRoutes.stream().map(Route::getId).collect(Collectors.joining(",")),
                    startedRoutes.stream().map(Route::getId).collect(Collectors.joining(",")));
        }

        // under the policy lock, as the policy thread may be handling a leadership change and deferring the start of
        // the routes while the CamelContext is starting
        lock.lock();
        try {
            if (startManagedRoutesEarly) {
                LOG.debug(
                        "CamelContext is now fully started, can now start managed routes eager as we were appointed leader during early startup");
                startManagedRoutesEarly = false;
                startManagedRoutes();
            }
        } finally {
            lock.unlock();
        }
    }

    // ****************************************************
    // Event handling
    // ****************************************************

    private class CamelClusterLeadershipListener implements CamelClusterEventListener.Leadership {
        @Override
        public void leadershipChanged(CamelClusterView view, CamelClusterMember leader) {
            if (clusterView.get() == null) {
                // the view has been released
                return;
            }

            // The view calls the listeners while it holds its lock, so the routes are started and stopped on the
            // policy thread instead. Starting a route can take a while, and it needs the CamelContext route lock,
            // which a thread removing a route or stopping the CamelContext may hold while it releases the view.
            handOverLeadershipChange();
        }
    }

    private void handOverLeadershipChange() {
        // The policy thread reads the leadership when it applies a change, so a change that is still queued covers
        // this one too, and at most one change is queued. getAndSet is used on both sides so the policy thread sees
        // the leadership that the view has set before it fired this event.
        if (leadershipChangePending.getAndSet(true)) {
            return;
        }

        final ExecutorService executor = leadershipExecutor;
        if (executor == null) {
            // Never apply the change on this thread, as it holds the lock of the view
            leadershipChangePending.set(false);
            LOG.warn("Ignoring leadership change as ClusteredRoutePolicy for namespace {} has no CamelContext", namespace);
            return;
        }

        try {
            executor.execute(this::applyLeadershipChange);
        } catch (RejectedExecutionException e) {
            leadershipChangePending.set(false);
            LOG.debug("Ignoring leadership change as ClusteredRoutePolicy for namespace {} has been shut down", namespace);
        }
    }

    private void applyLeadershipChange() {
        // from now on a leadership change queues a new task
        leadershipChangePending.getAndSet(false);
        try {
            setLeader();
        } catch (Exception e) {
            LOG.warn("Error applying leadership change of ClusteredRoutePolicy for namespace {}. This exception is ignored.",
                    namespace, e);
        }
    }

    private static ThreadPoolProfile newLeadershipThreadPoolProfile() {
        ThreadPoolProfile profile = new ThreadPoolProfile(THREAD_NAME);
        // A single thread, so the changes are applied one after the other, and no thread while the leadership does
        // not change, so a policy per route does not keep a thread per route.
        profile.setPoolSize(0);
        profile.setMaxPoolSize(1);
        profile.setKeepAliveTime(LEADERSHIP_THREAD_KEEP_ALIVE_MILLIS);
        profile.setTimeUnit(TimeUnit.MILLISECONDS);
        profile.setAllowCoreThreadTimeOut(true);
        // at most one change is queued (see handOverLeadershipChange)
        profile.setMaxQueueSize(1);
        // never run a change on the thread of the view: reject it once the policy has been shut down
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        return profile;
    }

    private class CamelContextStartupListener extends SimpleEventNotifierSupport
            implements ExtendedStartupListener, NonManagedService {
        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof CamelContextResumedEvent) {
                contextResumed = true;
                // a leadership taken while the CamelContext was suspended could not start the routes, and the resume
                // starts (or resumes) the routes that were running when it was suspended, also on a node that lost the
                // leadership meanwhile: the policy thread applies the leadership again
                reapply.set(true);
                handOverLeadershipChange();
            } else if (event instanceof RouteAddedEvent) {
                // on the thread that adds the route, which holds the CamelContext route lock: the policy thread starts
                // the route if this node is the leader (the route is known to the CamelContext from now on)
                reapply.set(true);
                handOverLeadershipChange();
            } else {
                onCamelContextStarted();
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            // the resumed event: see notify. A route added while the CamelContext starts is started (or deferred) with
            // the others. The added event also comes
            // when a stopped route is warmed up again; the extra pass then has nothing to do
            return event instanceof CamelContextStartedEvent || event instanceof CamelContextResumedEvent
                    || event instanceof RouteAddedEvent rae && camelContext.isStarted()
                            && stoppedRoutes.contains(rae.getRoute());
        }

        @Override
        public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            // noop
        }

        @Override
        public void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            if (alreadyStarted) {
                // Invoke it only if the context was already started as this
                // method is not invoked at last event as documented but after
                // routes warm-up so this is useful for routes deployed after
                // the camel context has been started-up. For standard routes
                // configuration the notification of the camel context started
                // is provided by EventNotifier.
                //
                // We should check why this callback is not invoked at latest
                // stage, or maybe rename it as it is misleading and provide a
                // better alternative for intercept camel events.
                onCamelContextStarted();
            }
        }

        private void onCamelContextStarted() {
            // Start managing the routes only when the camel context is started
            // so start/stop of managed routes do not clash with CamelContext
            // startup
            if (contextStarted.compareAndSet(false, true)) {

                // Eventually delay the startup of the routes a later time
                if (initialDelay.toMillis() > 0) {
                    LOG.debug("Policy will be effective in {}", initialDelay);
                    ScheduledExecutorService scheduler = camelContext.getExecutorServiceManager()
                            .newSingleThreadScheduledExecutor(ClusteredRoutePolicy.this, THREAD_NAME);
                    initialDelayExecutor = scheduler;
                    scheduler.schedule(() -> {
                        try {
                            initialDelayElapsed = true;
                            ClusteredRoutePolicy.this.onCamelContextStarted();
                        } finally {
                            // the delay applies once, so its thread is not needed anymore
                            initialDelayExecutor = null;
                            camelContext.getExecutorServiceManager().shutdown(scheduler);
                        }
                    }, initialDelay.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    ClusteredRoutePolicy.this.onCamelContextStarted();
                }
            }
        }
    }

    // ****************************************************
    // Static helpers
    // ****************************************************

    public static ClusteredRoutePolicy forNamespace(
            CamelContext camelContext, CamelClusterService.Selector selector, String namespace)
            throws Exception {
        ClusteredRoutePolicy policy = new ClusteredRoutePolicy(null, selector, namespace);
        policy.setCamelContext(camelContext);

        return policy;
    }

    public static ClusteredRoutePolicy forNamespace(CamelContext camelContext, String namespace) throws Exception {
        return forNamespace(camelContext, ClusterServiceSelectors.DEFAULT_SELECTOR, namespace);
    }

    public static ClusteredRoutePolicy forNamespace(CamelClusterService service, String namespace) throws Exception {
        return new ClusteredRoutePolicy(service, ClusterServiceSelectors.DEFAULT_SELECTOR, namespace);
    }

    public static ClusteredRoutePolicy forNamespace(CamelClusterService.Selector selector, String namespace) throws Exception {
        return new ClusteredRoutePolicy(null, selector, namespace);
    }

    public static ClusteredRoutePolicy forNamespace(String namespace) throws Exception {
        return forNamespace(ClusterServiceSelectors.DEFAULT_SELECTOR, namespace);
    }
}
