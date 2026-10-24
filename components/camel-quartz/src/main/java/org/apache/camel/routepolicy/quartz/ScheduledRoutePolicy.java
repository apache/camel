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
package org.apache.camel.routepolicy.quartz;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is Quartz based RoutePolicy implementation that re-use almost identical to "camel-quartz" component.
 *
 * The following has been updated: - Changed and used Quartz 2.x API call on all the area affected. - Stored JobKey and
 * TriggerKey instead of JobDetail and Trigger objects in ScheduledRouteDetails. - ScheduledJobState is stored using
 * full JobKey.toString() instead of just jobName.
 *
 * See org.apache.camel.component.quartz.QuartzComponent
 *
 */
public abstract class ScheduledRoutePolicy extends RoutePolicySupport
        implements ScheduledRoutePolicyConstants, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledRoutePolicy.class);

    protected Map<String, ScheduledRouteDetails> scheduledRouteDetailsMap = new LinkedHashMap<>();
    private Scheduler scheduler;
    @Metadata(description = "Timeout (in millis) when stopping routes.", defaultValue = "10000")
    private int routeStopGracePeriod;
    private TimeUnit timeUnit;
    // the routes whose consumer the scheduled suspend stopped (it is not Suspendable): only such a consumer is started
    // again by a scheduled resume or start, not one that the shutdown of the route, or another policy, stopped
    private final Set<String> consumersStoppedBySuspend = ConcurrentHashMap.newKeySet();

    protected abstract Trigger createTrigger(Action action, Route route) throws Exception;

    public void onJobExecute(Action action, Route route) throws Exception {
        LOG.debug("Scheduled Event notification received. Performing action: {} on route: {}", action, route.getId());

        ServiceStatus routeStatus = route.getCamelContext().getRouteController().getRouteStatus(route.getId());
        if (action == Action.START) {
            if (routeStatus == ServiceStatus.Stopped) {
                startRoute(route);
                // here we just check the states of the Consumer
            } else if (isSuspendedOrStoppedBySuspend(route)) {
                resumeOrStartSuspendedConsumer(route);
            }
        } else if (action == Action.STOP) {
            if (routeStatus == ServiceStatus.Started || routeStatus == ServiceStatus.Suspended) {
                stopRoute(route, getRouteStopGracePeriod(), getTimeUnit());
            } else {
                LOG.warn("Route is not in a started/suspended state and cannot be stopped. The current route state is {}",
                        routeStatus);
            }
        } else if (action == Action.SUSPEND) {
            if (routeStatus == ServiceStatus.Started) {
                Consumer consumer = route.getConsumer();
                // only a consumer that this suspend stopped, not one that was stopped already (by another policy, or
                // by the shutdown strategy while the route is being stopped)
                boolean alreadyStopped = ServiceHelper.isStopped(consumer);
                suspendOrStopConsumer(consumer);
                if (!alreadyStopped && ServiceHelper.isStopped(consumer)) {
                    consumersStoppedBySuspend.add(route.getId());
                }
            } else {
                LOG.warn("Route is not in a started state and cannot be suspended. The current route state is {}", routeStatus);
            }
        } else if (action == Action.RESUME) {
            if (routeStatus == ServiceStatus.Started) {
                if (isSuspendedOrStoppedBySuspend(route)) {
                    resumeOrStartSuspendedConsumer(route);
                } else {
                    LOG.warn("The Consumer {} is not suspended and cannot be resumed.", route.getConsumer());
                }
            } else {
                LOG.warn("Route is not in a started state and cannot be resumed. The current route state is {}", routeStatus);
            }
        }
    }

    /**
     * Whether the consumer of a started route was suspended, or stopped by the scheduled suspend as it is not
     * {@link org.apache.camel.Suspendable} (see {@link #suspendOrStopConsumer(Consumer)}), so a resume or start must
     * resume or start it again. A consumer that something else stopped, such as the shutdown strategy while the route
     * is being stopped (the route status stays Started until the in-flight exchanges are done), is left stopped.
     */
    private boolean isSuspendedOrStoppedBySuspend(Route route) {
        Consumer consumer = route.getConsumer();
        if (ServiceHelper.isSuspended(consumer)) {
            return true;
        }
        if (ServiceHelper.isStopped(consumer)) {
            return consumersStoppedBySuspend.contains(route.getId());
        }
        return false;
    }

    /**
     * Resumes or starts the consumer of a started route, unless Camel is stopping: the shutdown suspends or stops the
     * consumers of the routes before it stops the routes, so a scheduled resume or start that fires meanwhile must not
     * start the consumer again (as the throttling route policies do, see
     * {@link #isResumeOrStartConsumerAllowed(Route)}).
     */
    private void resumeOrStartSuspendedConsumer(Route route) throws Exception {
        if (isResumeOrStartConsumerAllowed(route)) {
            consumersStoppedBySuspend.remove(route.getId());
            resumeOrStartConsumer(route.getConsumer());
        } else {
            LOG.debug("The consumer of route {} is not resumed or started as Camel or the route is stopping", route.getId());
        }
    }

    @Override
    public void onStart(Route route) {
        super.onStart(route);
        // the route (and its consumer) started again
        consumersStoppedBySuspend.remove(route.getId());
    }

    @Override
    public void onStop(Route route) {
        super.onStop(route);
        consumersStoppedBySuspend.remove(route.getId());
    }

    @Override
    public void onResume(Route route) {
        super.onResume(route);
        // the route (and its consumer) resumed; a route whose consumer is not Suspendable is stopped and started by the
        // route controller instead (onStop and onStart), so this only makes sure that no record outlives a resume
        consumersStoppedBySuspend.remove(route.getId());
    }

    @Override
    public void onRemove(Route route) {
        try {
            // stop and un-schedule jobs
            doStop();
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    public void scheduleRoute(Action action, Route route) throws Exception {
        JobDetail jobDetail = createJobDetail(action, route);
        Trigger trigger = createTrigger(action, route);
        updateScheduledRouteDetails(action, jobDetail, trigger, route);

        loadCallbackDataIntoSchedulerContext(jobDetail, action, route);

        boolean isClustered = route.getCamelContext().getComponent("quartz", QuartzComponent.class).isClustered();
        if (isClustered) {
            // check to see if the same job has already been setup through another node of the cluster
            JobDetail existingJobDetail = getScheduler().getJobDetail(jobDetail.getKey());
            if (jobDetail.equals(existingJobDetail)) {
                LOG.info(
                        "Skipping to schedule the job: {} for action: {} on route {} as the job: {} already existing inside the cluster",
                        jobDetail.getKey(), action, route.getId(), existingJobDetail.getKey());

                // skip scheduling the same job again as one is already existing for the same routeId and action
                return;
            }
        }

        getScheduler().scheduleJob(jobDetail, trigger);

        if (LOG.isInfoEnabled()) {
            LOG.info("Scheduled trigger: {} for action: {} on route {}", trigger.getKey(), action, route.getId());
        }
    }

    public void pauseRouteTrigger(Action action, String routeId) throws SchedulerException {
        TriggerKey triggerKey = retrieveTriggerKey(action, routeId);

        getScheduler().pauseTrigger(triggerKey);

        LOG.debug("Scheduled trigger: {} is paused", triggerKey);
    }

    public void resumeRouteTrigger(Action action, String routeId) throws SchedulerException {
        TriggerKey triggerKey = retrieveTriggerKey(action, routeId);

        getScheduler().resumeTrigger(triggerKey);

        LOG.debug("Scheduled trigger: {} is resumed", triggerKey);
    }

    @Override
    protected void doStop() throws Exception {
        for (ScheduledRouteDetails scheduledRouteDetails : scheduledRouteDetailsMap.values()) {
            if (scheduledRouteDetails.getStartJobKey() != null) {
                deleteRouteJob(Action.START, scheduledRouteDetails);
            }
            if (scheduledRouteDetails.getStopJobKey() != null) {
                deleteRouteJob(Action.STOP, scheduledRouteDetails);
            }
            if (scheduledRouteDetails.getSuspendJobKey() != null) {
                deleteRouteJob(Action.SUSPEND, scheduledRouteDetails);
            }
            if (scheduledRouteDetails.getResumeJobKey() != null) {
                deleteRouteJob(Action.RESUME, scheduledRouteDetails);
            }
        }
    }

    public void deleteRouteJob(Action action, ScheduledRouteDetails scheduledRouteDetails) throws SchedulerException {
        JobKey jobKey = retrieveJobKey(action, scheduledRouteDetails);

        if (!getScheduler().isShutdown()) {
            getScheduler().deleteJob(jobKey);
        }

        LOG.debug("Scheduled job: {} is deleted", jobKey);
    }

    protected JobDetail createJobDetail(Action action, Route route) {
        JobDetail jobDetail = null;

        if (action == Action.START) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class).withIdentity(JOB_START + route.getId(), JOB_GROUP + route.getId())
                    .build();
        } else if (action == Action.STOP) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class).withIdentity(JOB_STOP + route.getId(), JOB_GROUP + route.getId())
                    .build();
        } else if (action == Action.SUSPEND) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class)
                    .withIdentity(JOB_SUSPEND + route.getId(), JOB_GROUP + route.getId()).build();
        } else if (action == Action.RESUME) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class)
                    .withIdentity(JOB_RESUME + route.getId(), JOB_GROUP + route.getId()).build();
        }

        return jobDetail;
    }

    protected void updateScheduledRouteDetails(Action action, JobDetail jobDetail, Trigger trigger, Route route) {
        ScheduledRouteDetails scheduledRouteDetails = getScheduledRouteDetails(route.getId());
        if (action == Action.START) {
            scheduledRouteDetails.setStartJobKey(jobDetail.getKey());
            scheduledRouteDetails.setStartTriggerKey(trigger.getKey());
        } else if (action == Action.STOP) {
            scheduledRouteDetails.setStopJobKey(jobDetail.getKey());
            scheduledRouteDetails.setStopTriggerKey(trigger.getKey());
        } else if (action == Action.SUSPEND) {
            scheduledRouteDetails.setSuspendJobKey(jobDetail.getKey());
            scheduledRouteDetails.setSuspendTriggerKey(trigger.getKey());
        } else if (action == Action.RESUME) {
            scheduledRouteDetails.setResumeJobKey(jobDetail.getKey());
            scheduledRouteDetails.setResumeTriggerKey(trigger.getKey());
        }
    }

    protected void loadCallbackDataIntoSchedulerContext(JobDetail jobDetail, Action action, Route route)
            throws SchedulerException {
        getScheduler().getContext().put(jobDetail.getKey().toString(), new ScheduledJobState(action, route));
    }

    public TriggerKey retrieveTriggerKey(Action action, String routeId) {
        ScheduledRouteDetails scheduledRouteDetails = getScheduledRouteDetails(routeId);
        TriggerKey result = null;

        if (action == Action.START) {
            result = scheduledRouteDetails.getStartTriggerKey();
        } else if (action == Action.STOP) {
            result = scheduledRouteDetails.getStopTriggerKey();
        } else if (action == Action.SUSPEND) {
            result = scheduledRouteDetails.getSuspendTriggerKey();
        } else if (action == Action.RESUME) {
            result = scheduledRouteDetails.getResumeTriggerKey();
        }

        return result;
    }

    public JobKey retrieveJobKey(Action action, ScheduledRouteDetails scheduledRouteDetails) {
        JobKey result = null;

        if (action == Action.START) {
            result = scheduledRouteDetails.getStartJobKey();
        } else if (action == Action.STOP) {
            result = scheduledRouteDetails.getStopJobKey();
        } else if (action == Action.SUSPEND) {
            result = scheduledRouteDetails.getSuspendJobKey();
        } else if (action == Action.RESUME) {
            result = scheduledRouteDetails.getResumeJobKey();
        }

        return result;
    }

    protected void registerRouteToScheduledRouteDetails(Route route) {
        ScheduledRouteDetails scheduledRouteDetails = new ScheduledRouteDetails();
        scheduledRouteDetailsMap.put(route.getId(), scheduledRouteDetails);
    }

    protected ScheduledRouteDetails getScheduledRouteDetails(String routeId) {
        return scheduledRouteDetailsMap.get(routeId);
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setRouteStopGracePeriod(int routeStopGracePeriod) {
        this.routeStopGracePeriod = routeStopGracePeriod;
    }

    public int getRouteStopGracePeriod() {
        return routeStopGracePeriod;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

}
