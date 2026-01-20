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
import java.util.concurrent.ScheduledExecutorService;

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
                        .build())
                .withName("Leadership")
                .build();
    }

    private void onLeadershipTaken() throws Exception {
        lock.lock();
        try {
            if (!isRunAllowed()) {
                return;
            }

            if (delegatedConsumer != null) {
                return;
            }

            final BackgroundTask leaderTask = createTask();
            leaderTask.schedule(getEndpoint().getCamelContext(), () -> {
                if (!isRunAllowed()) {
                    return false;
                }
                LOG.info("Leadership taken. Attempt #{} to start consumer: {}", leaderTask.iteration(), delegatedEndpoint);

                Exception cause = null;
                try {
                    if (delegatedConsumer == null) {
                        delegatedConsumer = delegatedEndpoint.createConsumer(processor);
                        if (delegatedConsumer instanceof StartupListener) {
                            getEndpoint().getCamelContext().addStartupListener((StartupListener) delegatedConsumer);
                        }
                        if (delegatedConsumer instanceof ResumeAware resumeAwareConsumer && resumeStrategy != null) {
                            LOG.debug("Setting up the resume adapter for the resume strategy in consumer");
                            ResumeAdapter resumeAdapter
                                    = AdapterHelper.eval(clusterService.getCamelContext(), resumeAwareConsumer,
                                            resumeStrategy);
                            resumeStrategy.setAdapter(resumeAdapter);

                            LOG.debug("Setting up the resume strategy for consumer");
                            resumeAwareConsumer.setResumeStrategy(resumeStrategy);
                        }
                    }
                    ServiceHelper.startService(delegatedEndpoint, delegatedConsumer);

                } catch (Exception e) {
                    cause = e;
                }

                if (cause != null) {
                    String message = "Leadership taken. Attempt #" + leaderTask.iteration()
                                     + " failed to start consumer due to: " + cause.getMessage();
                    getExceptionHandler().handleException(message, cause);
                    // make the task runner aware of the exception (will retry)
                    throw new TaskRunFailureException(message, cause);
                }

                LOG.info("Leadership taken. Attempt #{} success. Consumer started: {}", leaderTask.iteration(),
                        delegatedEndpoint);
                return false; // no more attempts
            });
        } finally {
            lock.unlock();
        }
    }

    private void onLeadershipLost() {
        lock.lock();
        try {
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

    // **************************************
    // Listener
    // **************************************

    private final class LeadershipListener implements CamelClusterEventListener.Leadership {
        @Override
        public void leadershipChanged(CamelClusterView view, CamelClusterMember leader) {
            if (!isRunAllowed()) {
                return;
            }

            if (view.getLocalMember().isLeader()) {
                try {
                    onLeadershipTaken();
                } catch (Exception e) {
                    getExceptionHandler().handleException("Error starting consumer while taking leadership", e);
                }
            } else if (delegatedConsumer != null) {
                try {
                    onLeadershipLost();
                } catch (Exception e) {
                    getExceptionHandler()
                            .handleException("Error stopping consumer while loosing leadership. This exception is ignored.", e);
                }
            }
        }
    }
}
