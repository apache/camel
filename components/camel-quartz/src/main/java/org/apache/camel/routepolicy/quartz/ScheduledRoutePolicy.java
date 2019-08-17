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
import java.util.concurrent.TimeUnit;

import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.quartz.QuartzComponent;
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
 * The following has been updated:
 *  - Changed and used Quartz 2.x API call on all the area affected.
 *  - Stored JobKey and TriggerKey instead of JobDetail and Trigger objects in ScheduledRouteDetails.
 *  - ScheduledJobState is stored using full JobKey.toString() instead of just jobName.
 *
 * See org.apache.camel.component.quartz.QuartzComponent
 *
 */
public abstract class ScheduledRoutePolicy extends RoutePolicySupport implements ScheduledRoutePolicyConstants, NonManagedService {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledRoutePolicy.class);
    protected Map<String, ScheduledRouteDetails> scheduledRouteDetailsMap = new LinkedHashMap<>();
    private Scheduler scheduler;
    private int routeStopGracePeriod;
    private TimeUnit timeUnit;

    protected abstract Trigger createTrigger(Action action, Route route) throws Exception;

    protected void onJobExecute(Action action, Route route) throws Exception {
        LOG.debug("Scheduled Event notification received. Performing action: {} on route: {}", action, route.getId());

        ServiceStatus routeStatus = route.getCamelContext().getRouteController().getRouteStatus(route.getId());
        if (action == Action.START) {
            if (routeStatus == ServiceStatus.Stopped) {
                startRoute(route);
                // here we just check the states of the Consumer
            } else if (ServiceHelper.isSuspended(route.getConsumer())) {
                resumeOrStartConsumer(route.getConsumer());
            }
        } else if (action == Action.STOP) {
            if ((routeStatus == ServiceStatus.Started) || (routeStatus == ServiceStatus.Suspended)) {
                stopRoute(route, getRouteStopGracePeriod(), getTimeUnit());
            } else {
                LOG.warn("Route is not in a started/suspended state and cannot be stopped. The current route state is {}", routeStatus);
            }
        } else if (action == Action.SUSPEND) {
            if (routeStatus == ServiceStatus.Started) {
                suspendOrStopConsumer(route.getConsumer());
            } else {
                LOG.warn("Route is not in a started state and cannot be suspended. The current route state is {}", routeStatus);
            }
        } else if (action == Action.RESUME) {
            if (routeStatus == ServiceStatus.Started) {
                if (ServiceHelper.isSuspended(route.getConsumer())) {
                    resumeOrStartConsumer(route.getConsumer());
                } else {
                    LOG.warn("The Consumer {} is not suspended and cannot be resumed.", route.getConsumer());
                }
            } else {
                LOG.warn("Route is not in a started state and cannot be resumed. The current route state is {}", routeStatus);
            }
        }
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
                if (LOG.isInfoEnabled()) {
                    LOG.info("Skipping to schedule the job: {} for action: {} on route {} as the job: {} already existing inside the cluster",
                             new Object[] {jobDetail.getKey(), action, route.getId(), existingJobDetail.getKey()});
                }

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

    protected JobDetail createJobDetail(Action action, Route route) throws Exception {
        JobDetail jobDetail = null;

        if (action == Action.START) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class).withIdentity(JOB_START + route.getId(), JOB_GROUP + route.getId()).build();
        } else if (action == Action.STOP) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class).withIdentity(JOB_STOP + route.getId(), JOB_GROUP + route.getId()).build();
        } else if (action == Action.SUSPEND) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class).withIdentity(JOB_SUSPEND + route.getId(), JOB_GROUP + route.getId()).build();
        } else if (action == Action.RESUME) {
            jobDetail = JobBuilder.newJob(ScheduledJob.class).withIdentity(JOB_RESUME + route.getId(), JOB_GROUP + route.getId()).build();
        }

        return jobDetail;
    }

    protected void updateScheduledRouteDetails(Action action, JobDetail jobDetail, Trigger trigger, Route route) throws Exception {
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

    protected void loadCallbackDataIntoSchedulerContext(JobDetail jobDetail, Action action, Route route) throws SchedulerException {
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
