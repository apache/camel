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
package org.apache.camel.routepolicy.quartz;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ScheduledRoutePolicy extends RoutePolicySupport implements ScheduledRoutePolicyConstants, NonManagedService {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledRoutePolicy.class);
    protected Map<String, ScheduledRouteDetails> scheduledRouteDetailsMap = new LinkedHashMap<String, ScheduledRouteDetails>();
    private Scheduler scheduler;
    private int routeStopGracePeriod;
    private TimeUnit timeUnit;

    protected abstract Trigger createTrigger(Action action, Route route) throws Exception;

    protected void onJobExecute(Action action, Route route) throws Exception {
        LOG.debug("Scheduled Event notification received. Performing action: {} on route: {}", action, route.getId());

        ServiceStatus routeStatus = route.getRouteContext().getCamelContext().getRouteStatus(route.getId());
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
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void scheduleRoute(Action action, Route route) throws Exception {
        JobDetail jobDetail = createJobDetail(action, route);
        Trigger trigger = createTrigger(action, route);
        updateScheduledRouteDetails(action, jobDetail, trigger, route);
        
        loadCallbackDataIntoSchedulerContext(jobDetail, action, route);

        boolean isClustered = route.getRouteContext().getCamelContext().getComponent("quartz", QuartzComponent.class).isClustered();
        if (isClustered) {
            // check to see if the same job has already been setup through another node of the cluster
            JobDetail existingJobDetail = getScheduler().getJobDetail(jobDetail.getName(), jobDetail.getGroup());
            if (jobDetail.equals(existingJobDetail)) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Skipping to schedule the job: {} for action: {} on route {} as the job: {} already existing inside the cluster",
                             new Object[] {jobDetail.getFullName(), action, route.getId(), existingJobDetail.getFullName()});
                }

                // skip scheduling the same job again as one is already existing for the same routeId and action
                return;
            }
        }

        getScheduler().scheduleJob(jobDetail, trigger);

        if (LOG.isInfoEnabled()) {
            LOG.info("Scheduled trigger: {} for action: {} on route {}", new Object[]{trigger.getFullName(), action, route.getId()});
        }
    }

    public void pauseRouteTrigger(Action action, String routeId) throws SchedulerException {
        String triggerName = retrieveTriggerName(action, routeId);
        String triggerGroup = retrieveTriggerGroup(action, routeId);
        
        getScheduler().pauseTrigger(triggerName, triggerGroup);

        LOG.debug("Scheduled trigger: {}.{} is paused", triggerGroup, triggerName);
    }
    
    public void resumeRouteTrigger(Action action, String routeId) throws SchedulerException {
        String triggerName = retrieveTriggerName(action, routeId);
        String triggerGroup = retrieveTriggerGroup(action, routeId);
        
        getScheduler().resumeTrigger(triggerName, triggerGroup);

        LOG.debug("Scheduled trigger: {}.{} is resumed", triggerGroup, triggerName);
    }

    @Override
    protected void doStop() throws Exception {
        for (ScheduledRouteDetails scheduledRouteDetails : scheduledRouteDetailsMap.values()) {
            if (scheduledRouteDetails.getStartJobDetail() != null) {
                deleteRouteJob(Action.START, scheduledRouteDetails);
            }
            if (scheduledRouteDetails.getStopJobDetail() != null) {
                deleteRouteJob(Action.STOP, scheduledRouteDetails);
            }
            if (scheduledRouteDetails.getSuspendJobDetail() != null) {
                deleteRouteJob(Action.SUSPEND, scheduledRouteDetails);
            }
            if (scheduledRouteDetails.getResumeJobDetail() != null) {
                deleteRouteJob(Action.RESUME, scheduledRouteDetails);
            }
        }
    }

    public void deleteRouteJob(Action action, ScheduledRouteDetails scheduledRouteDetails) throws SchedulerException {
        String jobDetailName = retrieveJobDetailName(action, scheduledRouteDetails);
        String jobDetailGroup = retrieveJobDetailGroup(action, scheduledRouteDetails);
        
        if (!getScheduler().isShutdown()) {
            getScheduler().deleteJob(jobDetailName, jobDetailGroup);
        }

        LOG.debug("Scheduled job: {}.{} is deleted", jobDetailGroup, jobDetailName);
    }
    
    protected JobDetail createJobDetail(Action action, Route route) throws Exception {
        JobDetail jobDetail = null;
        
        if (action == Action.START) {
            jobDetail = new JobDetail(JOB_START + route.getId(), JOB_GROUP + route.getId(), ScheduledJob.class);
        } else if (action == Action.STOP) {
            jobDetail = new JobDetail(JOB_STOP + route.getId(), JOB_GROUP + route.getId(), ScheduledJob.class);
        } else if (action == Action.SUSPEND) {
            jobDetail = new JobDetail(JOB_SUSPEND + route.getId(), JOB_GROUP + route.getId(), ScheduledJob.class);
        } else if (action == Action.RESUME) {
            jobDetail = new JobDetail(JOB_RESUME + route.getId(), JOB_GROUP + route.getId(), ScheduledJob.class);
        }
        
        return jobDetail;
    }
        
    protected void updateScheduledRouteDetails(Action action, JobDetail jobDetail, Trigger trigger, Route route) throws Exception {
        ScheduledRouteDetails scheduledRouteDetails = getScheduledRouteDetails(route.getId());
        if (action == Action.START) {
            scheduledRouteDetails.setStartJobDetail(jobDetail);
            scheduledRouteDetails.setStartTrigger(trigger);
        } else if (action == Action.STOP) {
            scheduledRouteDetails.setStopJobDetail(jobDetail);
            scheduledRouteDetails.setStopTrigger(trigger);
        } else if (action == Action.SUSPEND) {
            scheduledRouteDetails.setSuspendJobDetail(jobDetail);
            scheduledRouteDetails.setSuspendTrigger(trigger);
        } else if (action == Action.RESUME) {
            scheduledRouteDetails.setResumeJobDetail(jobDetail);
            scheduledRouteDetails.setResumeTrigger(trigger);
        }
    }

    protected void loadCallbackDataIntoSchedulerContext(JobDetail jobDetail, Action action, Route route) throws SchedulerException {
        getScheduler().getContext().put(jobDetail.getName(), new ScheduledJobState(action, route));
    }    
        
    public String retrieveTriggerName(Action action, String routeId) {
        ScheduledRouteDetails scheduledRouteDetails = getScheduledRouteDetails(routeId);
        String triggerName = null;

        if (action == Action.START) {
            triggerName = scheduledRouteDetails.getStartTrigger().getName();
        } else if (action == Action.STOP) {
            triggerName = scheduledRouteDetails.getStopTrigger().getName();
        } else if (action == Action.SUSPEND) {
            triggerName = scheduledRouteDetails.getSuspendTrigger().getName();
        } else if (action == Action.RESUME) {
            triggerName = scheduledRouteDetails.getResumeTrigger().getName();
        }
        
        return triggerName;
    }

    public String retrieveTriggerGroup(Action action, String routeId) {
        ScheduledRouteDetails scheduledRouteDetails = getScheduledRouteDetails(routeId);
        String triggerGroup = null;

        if (action == Action.START) {
            triggerGroup = scheduledRouteDetails.getStartTrigger().getGroup();
        } else if (action == Action.STOP) {
            triggerGroup = scheduledRouteDetails.getStopTrigger().getGroup();
        } else if (action == Action.SUSPEND) {
            triggerGroup = scheduledRouteDetails.getSuspendTrigger().getGroup();
        } else if (action == Action.RESUME) {
            triggerGroup = scheduledRouteDetails.getResumeTrigger().getGroup();
        }
        
        return triggerGroup;
    }
    
    public String retrieveJobDetailName(Action action, ScheduledRouteDetails scheduledRouteDetails) {
        String jobDetailName = null;

        if (action == Action.START) {
            jobDetailName = scheduledRouteDetails.getStartJobDetail().getName();
        } else if (action == Action.STOP) {
            jobDetailName = scheduledRouteDetails.getStopJobDetail().getName();
        } else if (action == Action.SUSPEND) {
            jobDetailName = scheduledRouteDetails.getSuspendJobDetail().getName();
        } else if (action == Action.RESUME) {
            jobDetailName = scheduledRouteDetails.getResumeJobDetail().getName();
        }
        
        return jobDetailName;
    }

    public String retrieveJobDetailGroup(Action action, ScheduledRouteDetails scheduledRouteDetails) {
        String jobDetailGroup = null;

        if (action == Action.START) {
            jobDetailGroup = scheduledRouteDetails.getStartJobDetail().getGroup();
        } else if (action == Action.STOP) {
            jobDetailGroup = scheduledRouteDetails.getStopJobDetail().getGroup();
        } else if (action == Action.SUSPEND) {
            jobDetailGroup = scheduledRouteDetails.getSuspendJobDetail().getGroup();
        } else if (action == Action.RESUME) {
            jobDetailGroup = scheduledRouteDetails.getResumeJobDetail().getGroup();
        }
        
        return jobDetailGroup;
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
