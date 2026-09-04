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
package org.apache.camel.component.master;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.StartupListener;
import org.apache.camel.SuspendableService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.resume.AdapterHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.TaskRunFailureException;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consumer which is only really active when the {@link CamelClusterView} has the leadership.
 */
@ManagedResource(description = "Managed Master Consumer")
public class MasterConsumer extends DefaultConsumer implements ResumeAware<ResumeStrategy> {
    private static final Logger LOG = LoggerFactory.getLogger(MasterConsumer.class);

    private final CamelClusterService clusterService;
    private final MasterEndpoint masterEndpoint;
    private final Endpoint delegatedEndpoint;
    private final Processor processor;
    private final CamelClusterEventListener.Leadership leadershipListener;
    private volatile Consumer delegatedConsumer;
    private volatile CamelClusterView view;
    private ResumeStrategy resumeStrategy;
    private ScheduledExecutorService leaderPool;
    // leadership state and the pending start task are guarded by lock, which is also held by the
    // service lifecycle methods, so a leadership event cannot interleave with start/stop of this consumer
    private boolean leadershipTaken;
    private Future<?> leaderTaskFuture;

    public MasterConsumer(MasterEndpoint masterEndpoint, Processor processor, CamelClusterService clusterService) {
        super(masterEndpoint, processor);
        this.clusterService = clusterService;
        this.masterEndpoint = masterEndpoint;
        this.delegatedEndpoint = masterEndpoint.getEndpoint();
        this.processor = processor;
        this.leadershipListener = new LeadershipListener();
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // used for re-connecting to the database
        leaderPool = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "Leadership");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Using ClusterService instance {} (id={}, type={})", clusterService, clusterService.getId(),
                clusterService.getClass().getName());

        view = clusterService.getView(masterEndpoint.getNamespace());
        view.addEventListener(leadershipListener);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // a start can still be pending, cancel it first so it cannot start the delegated consumer
        // after this consumer has been stopped
        leadershipTaken = false;
        cancelLeaderTask(true);

        // note: removeEventListener below needs the cluster view lock while this thread holds the lock of
        // this service, which is the opposite order of an event dispatch. Nothing that runs under this lock
        // may wait for the view, and the listener bails out before locking once this consumer is stopping

        if (view != null) {
            view.removeEventListener(leadershipListener);
            clusterService.releaseView(view);
            view = null;
        }

        getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(leaderPool);
        ServiceHelper.stopAndShutdownServices(delegatedConsumer, delegatedEndpoint);
        delegatedConsumer = null;
    }

    @Override
    protected void doResume() throws Exception {
        if (delegatedConsumer instanceof SuspendableService) {
            ((SuspendableService) delegatedConsumer).resume();
        }
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (delegatedConsumer instanceof SuspendableService) {
            ((SuspendableService) delegatedConsumer).suspend();
        }
        super.doSuspend();
    }

    @ManagedAttribute(description = "Are we the master")
    public boolean isMaster() {
        return view != null && view.getLocalMember().isLeader();
    }

    // **************************************
    // Helpers
    // **************************************

    private BackgroundTask createTask() {
        return Tasks.backgroundTask()
                .withScheduledExecutor(leaderPool)
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(masterEndpoint.getComponent().getBackOffDelay()))
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withMaxIterations(masterEndpoint.getComponent().getBackOffMaxAttempts())
                        // the attempts are bounded by backOffMaxAttempts, not by the 5s default duration of
                        // the builder, which would otherwise end the task before the second attempt
                        .withUnlimitedDuration()
                        .build())
                .withName("Leadership")
                .build();
    }

    private void onLeadershipTaken() {
        lock.lock();
        try {
            if (!isRunAllowed()) {
                return;
            }

            leadershipTaken = true;

            if (delegatedConsumer != null || isStartPending()) {
                return;
            }

            // a task from a previous leadership term may still be scheduled, drop it
            cancelLeaderTask(false);

            final BackgroundTask task = createTask();
            // the consumer is created once and re-used by the start attempts of this task
            final AtomicReference<Consumer> attempt = new AtomicReference<>();
            leaderTaskFuture = task.schedule(getEndpoint().getCamelContext(), () -> startDelegatedConsumer(task, attempt));
        } finally {
            lock.unlock();
        }
    }

    private boolean startDelegatedConsumer(BackgroundTask task, AtomicReference<Consumer> attempt) {
        lock.lock();
        try {
            if (!isRunAllowed()) {
                return false;
            }

            if (!leadershipTaken) {
                // leadership was lost while this start was pending. Starting now would run the consumer on a
                // node that is not the leader, and no further leadership event is coming to stop it again
                LOG.debug("Leadership lost while the start was pending. Not starting consumer: {}", delegatedEndpoint);
                return true; // no more attempts
            }

            if (delegatedConsumer != null) {
                return true; // no more attempts
            }
        } finally {
            lock.unlock();
        }

        LOG.info("Leadership taken. Attempt #{} to start consumer: {}", task.iteration(), delegatedEndpoint);

        // the delegate is created and started without holding the lock. It can block for a long time, and the
        // lock is taken by the service lifecycle and by the cluster view event dispatch, which must not wait
        // for a broker connect. The leadership is re-checked below before the consumer is published
        Consumer consumer = attempt.get();
        Exception cause = null;
        try {
            if (consumer == null) {
                consumer = delegatedEndpoint.createConsumer(processor);
                // held for the attempts of this task, so the startup listener and the resume strategy are
                // wired once and a retry only starts the consumer again
                attempt.set(consumer);
                if (consumer instanceof StartupListener startupListener) {
                    getEndpoint().getCamelContext().addStartupListener(startupListener);
                }
                if (consumer instanceof ResumeAware resumeAwareConsumer && resumeStrategy != null) {
                    LOG.debug("Setting up the resume adapter for the resume strategy in consumer");
                    ResumeAdapter resumeAdapter
                            = AdapterHelper.eval(clusterService.getCamelContext(), resumeAwareConsumer,
                                    resumeStrategy);
                    resumeStrategy.setAdapter(resumeAdapter);

                    LOG.debug("Setting up the resume strategy for consumer");
                    resumeAwareConsumer.setResumeStrategy(resumeStrategy);
                }
            }
            ServiceHelper.startService(delegatedEndpoint, consumer);
        } catch (Exception e) {
            cause = e;
        }

        lock.lock();
        try {
            if (cause != null) {
                // the consumer is kept for the next attempt. It is not stopped here: a consumer that failed to
                // start was already stopped by its own start(), and shutting it down would also shut down the
                // processor of the route, which the next attempt and this consumer still need
                String message = "Leadership taken. Attempt #" + task.iteration()
                                 + " failed to start consumer due to: " + cause.getMessage();
                getExceptionHandler().handleException(message, cause);
                int maxAttempts = masterEndpoint.getComponent().getBackOffMaxAttempts();
                if (maxAttempts > 0 && task.iteration() >= maxAttempts) {
                    LOG.error("Leadership taken. Giving up after {} attempts to start consumer: {}."
                              + " This node holds the leadership but is not consuming, until the leadership changes again.",
                            task.iteration(), delegatedEndpoint);
                }
                // make the task runner aware of the exception (will retry)
                throw new TaskRunFailureException(message, cause);
            }

            if (!leadershipTaken || !isRunAllowed()) {
                // the leadership went away while the consumer was starting, so stop what was just started
                // instead of publishing it. No leadership event is going to do it, delegatedConsumer is unset
                LOG.info("Leadership lost while the consumer was starting. Stopping consumer: {}", delegatedEndpoint);
                ServiceHelper.stopAndShutdownServices(consumer, delegatedEndpoint);
                attempt.set(null);
                return true; // no more attempts
            }

            delegatedConsumer = consumer;
            LOG.info("Leadership taken. Attempt #{} success. Consumer started: {}", task.iteration(),
                    delegatedEndpoint);
            // release the task, a later leadership term schedules a new one
            cancelLeaderTask(false);
            return true; // no more attempts
        } finally {
            lock.unlock();
        }
    }

    private void onLeadershipLost() {
        lock.lock();
        try {
            leadershipTaken = false;
            // a start scheduled by the leadership taken event may not have run yet, cancel it so it
            // cannot start the consumer on a node that is no longer the leader
            cancelLeaderTask(false);

            if (delegatedConsumer == null) {
                return;
            }

            LOG.debug("Leadership lost. Stopping consumer: {}", delegatedEndpoint);
            try {
                ServiceHelper.stopAndShutdownServices(delegatedConsumer, delegatedEndpoint);
            } finally {
                delegatedConsumer = null;
            }
            LOG.info("Leadership lost. Consumer stopped: {}", delegatedEndpoint);
        } finally {
            lock.unlock();
        }
    }

    private boolean isStartPending() {
        return leaderTaskFuture != null && !leaderTaskFuture.isDone();
    }

    private void cancelLeaderTask(boolean mayInterruptIfRunning) {
        if (leaderTaskFuture != null) {
            leaderTaskFuture.cancel(mayInterruptIfRunning);
            leaderTaskFuture = null;
        }
    }

    // **************************************
    // Listener
    // **************************************

    private final class LeadershipListener implements CamelClusterEventListener.Leadership {
        @Override
        public void leadershipChanged(CamelClusterView view, CamelClusterMember leader) {
            if (!isRunAllowed()) {
                // checked before taking the lock: this runs on the cluster view dispatch thread while that
                // view holds its own lock, and a consumer that is stopping holds this lock while it removes
                // this listener from the view
                return;
            }

            lock.lock();
            try {
                if (!isRunAllowed()) {
                    return;
                }

                // the leadership is read under the same lock that applies it, so that two events
                // dispatched concurrently cannot be applied in the wrong order
                if (view.getLocalMember().isLeader()) {
                    try {
                        onLeadershipTaken();
                    } catch (Exception e) {
                        getExceptionHandler().handleException("Error starting consumer while taking leadership", e);
                    }
                } else {
                    // dispatched even when there is no consumer yet, as a start may be pending
                    try {
                        onLeadershipLost();
                    } catch (Exception e) {
                        getExceptionHandler()
                                .handleException("Error stopping consumer while loosing leadership. This exception is ignored.",
                                        e);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
