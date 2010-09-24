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

import java.util.Date;
import java.util.Properties;

import org.apache.camel.Route;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

public class SimpleScheduledRoutePolicy extends ScheduledRoutePolicy {
    private static final transient Log LOG = LogFactory.getLog(CronScheduledRoutePolicy.class);
    private Date routeStartDate;
    private int routeStartRepeatCount;
    private long routeStartRepeatInterval;
    private Date routeStopDate;
    private int routeStopRepeatCount;
    private long routeStopRepeatInterval;
    private Date routeSuspendDate; 
    private int routeSuspendRepeatCount;
    private long routeSuspendRepeatInterval;
    private Date routeResumeDate; 
    private int routeResumeRepeatCount;
    private long routeResumeRepeatInterval;    

    public SimpleScheduledRoutePolicy() {
        super();
    }

    public SimpleScheduledRoutePolicy(String propertiesFile) {
        super(propertiesFile);
    }
    
    public SimpleScheduledRoutePolicy(Properties properties) {
        super(properties);
    }
    
    public void onInit(Route route) {   
        try {       
            if ((getRouteStartDate() == null) && (getRouteStopDate() == null) && (getRouteSuspendDate() == null) && (getRouteResumeDate() == null)) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Scheduled Route Policy for route " + route.getId() + " is not set since the no start, stop and/or suspend times are specified");
                }
                return;
            }
        
            if (scheduledRouteDetails == null) {
                scheduledRouteDetails = new ScheduledRouteDetails();
                scheduledRouteDetails.setRoute(route);
                
                if (getRouteStartDate() != null) {
                    scheduleRoute(Action.START); 
                }

                if (getRouteStopDate() != null) {
                    scheduleRoute(Action.STOP);
                }
                
                if (getRouteSuspendDate() != null) {
                    scheduleRoute(Action.SUSPEND);
                }
                if (getRouteResumeDate() != null) {
                    scheduleRoute(Action.RESUME);
                }
            }

            getScheduler().start();
        } catch (Exception e) {
            handleException(e);
        }        
    }   

    @Override
    protected void doStop() throws Exception {
        if (scheduledRouteDetails.getStartJobDetail() != null) {
            deleteRouteJob(Action.START);
        }
        if (scheduledRouteDetails.getStopJobDetail() != null) {
            deleteRouteJob(Action.STOP);
        }
        if (scheduledRouteDetails.getSuspendJobDetail() != null) {
            deleteRouteJob(Action.SUSPEND);
        }
        if (scheduledRouteDetails.getResumeJobDetail() != null) {
            deleteRouteJob(Action.RESUME);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.camel.routepolicy.quartz.ScheduledRoutePolicy#createTrigger(org.apache.camel.routepolicy.quartz.ScheduledRoutePolicyConstants.Action)
     */
    @Override
    protected Trigger createTrigger(Action action, Route route) throws Exception {
        SimpleTrigger trigger = null;
        
        if (action == Action.START) {
            trigger = new SimpleTrigger(TRIGGER_START + route.getId(), TRIGGER_GROUP + route.getId(), 
                getRouteStartDate(), null, getRouteStartRepeatCount(), getRouteStartRepeatInterval());
        } else if (action == Action.STOP) {
            trigger = new SimpleTrigger(TRIGGER_STOP + route.getId(), TRIGGER_GROUP + route.getId(), 
                getRouteStopDate(), null, getRouteStopRepeatCount(), getRouteStopRepeatInterval());
        } else if (action == Action.SUSPEND) {
            trigger = new SimpleTrigger(TRIGGER_SUSPEND + route.getId(), TRIGGER_GROUP + route.getId(), 
                    getRouteSuspendDate(), null, getRouteSuspendRepeatCount(), getRouteSuspendRepeatInterval());
        } else if (action == Action.RESUME) {
            trigger = new SimpleTrigger(TRIGGER_RESUME + route.getId(), TRIGGER_GROUP + route.getId(), 
                    getRouteResumeDate(), null, getRouteResumeRepeatCount(), getRouteResumeRepeatInterval());
        }
        
        return trigger;
    }

    
    
    public Date getRouteStartDate() {
        return routeStartDate;
    }

    public void setRouteStartDate(Date routeStartDate) {
        this.routeStartDate = routeStartDate;
    }

    public Date getRouteStopDate() {
        return routeStopDate;
    }

    public void setRouteStopDate(Date routeStopDate) {
        this.routeStopDate = routeStopDate;
    }

    public Date getRouteSuspendDate() {
        return routeSuspendDate;
    }

    public void setRouteSuspendDate(Date routeSuspendDate) {
        this.routeSuspendDate = routeSuspendDate;
    }

    public int getRouteStartRepeatCount() {
        return routeStartRepeatCount;
    }

    public void setRouteStartRepeatCount(int routeStartRepeatCount) {
        this.routeStartRepeatCount = routeStartRepeatCount;
    }

    public long getRouteStartRepeatInterval() {
        return routeStartRepeatInterval;
    }

    public void setRouteStartRepeatInterval(long routeStartRepeatInterval) {
        this.routeStartRepeatInterval = routeStartRepeatInterval;
    }

    public int getRouteStopRepeatCount() {
        return routeStopRepeatCount;
    }

    public void setRouteStopRepeatCount(int routeStopRepeatCount) {
        this.routeStopRepeatCount = routeStopRepeatCount;
    }

    public long getRouteStopRepeatInterval() {
        return routeStopRepeatInterval;
    }

    public void setRouteStopRepeatInterval(long routeStopRepeatInterval) {
        this.routeStopRepeatInterval = routeStopRepeatInterval;
    }

    public int getRouteSuspendRepeatCount() {
        return routeSuspendRepeatCount;
    }

    public void setRouteSuspendRepeatCount(int routeSuspendRepeatCount) {
        this.routeSuspendRepeatCount = routeSuspendRepeatCount;
    }

    public long getRouteSuspendRepeatInterval() {
        return routeSuspendRepeatInterval;
    }

    public void setRouteSuspendRepeatInterval(long routeSuspendRepeatInterval) {
        this.routeSuspendRepeatInterval = routeSuspendRepeatInterval;
    }

    public void setRouteResumeDate(Date routeResumeDate) {
        this.routeResumeDate = routeResumeDate;
    }

    public Date getRouteResumeDate() {
        return routeResumeDate;
    }

    public void setRouteResumeRepeatCount(int routeResumeRepeatCount) {
        this.routeResumeRepeatCount = routeResumeRepeatCount;
    }

    public int getRouteResumeRepeatCount() {
        return routeResumeRepeatCount;
    }

    public void setRouteResumeRepeatInterval(long routeResumeRepeatInterval) {
        this.routeResumeRepeatInterval = routeResumeRepeatInterval;
    }

    public long getRouteResumeRepeatInterval() {
        return routeResumeRepeatInterval;
    }
    
}
