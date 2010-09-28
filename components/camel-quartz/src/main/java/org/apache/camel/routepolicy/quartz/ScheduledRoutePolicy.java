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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.RoutePolicySupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

public abstract class ScheduledRoutePolicy extends RoutePolicySupport implements ScheduledRoutePolicyConstants {
    private static final transient Log LOG = LogFactory.getLog(ScheduledRoutePolicy.class);
    protected ScheduledRouteDetails scheduledRouteDetails;
    private Scheduler scheduler;
    private int routeStopGracePeriod;
    private TimeUnit timeUnit; 

    protected abstract Trigger createTrigger(Action action, Route route) throws Exception;

    protected void onJobExecute(Action action, Route route) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Scheduled Event notification received. Performing requested operation " + action + " for route " + route.getId());
        }
        ServiceStatus routeStatus = route.getRouteContext().getCamelContext().getRouteStatus(route.getId());
        if (action == Action.START) {
            if (routeStatus == ServiceStatus.Stopped) {
                startRoute(route);
            } else if (routeStatus == ServiceStatus.Suspended) {
                startConsumer(route.getConsumer());
            }
        } else if (action == Action.STOP) {
            if ((routeStatus == ServiceStatus.Started) || (routeStatus == ServiceStatus.Suspended)) {
                stopRoute(route, getRouteStopGracePeriod(), getTimeUnit());
            }
        } else if (action == Action.SUSPEND) {
            if (routeStatus == ServiceStatus.Started) {
                stopConsumer(route.getConsumer());
            } else {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Route is not in a started state and cannot be suspended. The current route state is " + routeStatus);
                }
            }
        } else if (action == Action.RESUME) {
            if (routeStatus == ServiceStatus.Started) {
                startConsumer(route.getConsumer());
            } else {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Route is not in a started state and cannot be resumed. The current route state is " + routeStatus);
                }
            }
        }       
    }

    public void scheduleRoute(Action action) throws Exception {
        Route route = scheduledRouteDetails.getRoute();
        
        JobDetail jobDetail = createJobDetail(action, route);
        Trigger trigger = createTrigger(action, route);
        updateScheduledRouteDetails(action, jobDetail, trigger);
        
        loadCallbackDataIntoSchedulerContext(action, route);
        getScheduler().scheduleJob(jobDetail, trigger);
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Scheduled Trigger: " + trigger.getFullName()  + " is operational");
        }
    }    

    public void pauseRouteTrigger(Action action) throws SchedulerException {
        String triggerName = retrieveTriggerName(action);
        String triggerGroup = retrieveTriggerGroup(action);
        
        getScheduler().pauseTrigger(triggerName, triggerGroup);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Scheduled Trigger: " + triggerGroup + "." + triggerName + " is paused");
        }
    }
    
    public void resumeRouteTrigger(Action action) throws SchedulerException {
        String triggerName = retrieveTriggerName(action);
        String triggerGroup = retrieveTriggerGroup(action);
        
        getScheduler().resumeTrigger(triggerName, triggerGroup);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Scheduled Trigger: " + triggerGroup + "." + triggerName + " has been resumed");
        }
    }

    public void deleteRouteJob(Action action) throws SchedulerException {
        String jobDetailName = retrieveJobDetailName(action);
        String jobDetailGroup = retrieveJobDetailGroup(action);
        
        if (!getScheduler().isShutdown()) {
            getScheduler().deleteJob(jobDetailName, jobDetailGroup);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Scheduled Job: " + jobDetailGroup + "." + jobDetailName + " has been deleted");
        }
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
        
    protected void updateScheduledRouteDetails(Action action, JobDetail jobDetail, Trigger trigger) throws Exception {
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
    
    protected void loadCallbackDataIntoSchedulerContext(Action action, Route route) throws SchedulerException {
        getScheduler().getContext().put(SCHEDULED_ACTION, action);
        getScheduler().getContext().put(SCHEDULED_ROUTE, route);
    }    
        
    public String retrieveTriggerName(Action action) {
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

    public String retrieveTriggerGroup(Action action) {
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
    
    public String retrieveJobDetailName(Action action) {
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

    public String retrieveJobDetailGroup(Action action) {
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
    
    public ScheduledRouteDetails getScheduledRouteDetails() {
        return scheduledRouteDetails;
    }

    public void setScheduledRouteDetails(ScheduledRouteDetails scheduledRouteDetails) {
        this.scheduledRouteDetails = scheduledRouteDetails;
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
