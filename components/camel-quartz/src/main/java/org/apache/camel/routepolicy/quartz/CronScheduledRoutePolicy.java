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
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.CronTrigger;
import org.quartz.Trigger;

public class CronScheduledRoutePolicy extends ScheduledRoutePolicy implements ScheduledRoutePolicyConstants {
    private static final transient Log LOG = LogFactory.getLog(CronScheduledRoutePolicy.class);
    private String routeStartTime;
    private String routeStopTime;
    private String routeSuspendTime;
    private String routeResumeTime;
    
    public void onInit(Route route) {   
        try {       
            QuartzComponent quartz = route.getRouteContext().getCamelContext().getComponent("quartz", QuartzComponent.class);
            setScheduler(quartz.getScheduler());
            
            if (getRouteStopGracePeriod() == 0) {
                setRouteStopGracePeriod(10000);
            }
            
            if (getTimeUnit() == null) {
                setTimeUnit(TimeUnit.MILLISECONDS);
            }

            if ((getRouteStartTime() == null) && (getRouteStopTime() == null) && (getRouteSuspendTime() == null) && (getRouteResumeTime() == null)) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Scheduled Route Policy for route " + route.getId() + " is not set since the no start, stop and/or suspend times are specified");
                }
                return;
            }
        
            if (scheduledRouteDetails == null) {
                scheduledRouteDetails = new ScheduledRouteDetails();
                scheduledRouteDetails.setRoute(route);
                
                if (getRouteStartTime() != null) {
                    scheduleRoute(Action.START); 
                }

                if (getRouteStopTime() != null) {
                    scheduleRoute(Action.STOP);
                }
                
                if (getRouteSuspendTime() != null) {
                    scheduleRoute(Action.SUSPEND);
                }
                if (getRouteResumeTime() != null) {
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
        CronTrigger trigger = null;
        
        if (action == Action.START) {
            trigger = new CronTrigger(TRIGGER_START + route.getId(), TRIGGER_GROUP + route.getId(), getRouteStartTime());
        } else if (action == Action.STOP) {
            trigger = new CronTrigger(TRIGGER_STOP + route.getId(), TRIGGER_GROUP + route.getId(), getRouteStopTime());
        } else if (action == Action.SUSPEND) {
            trigger = new CronTrigger(TRIGGER_SUSPEND + route.getId(), TRIGGER_GROUP + route.getId(), getRouteSuspendTime());
        } else if (action == Action.RESUME) {
            trigger = new CronTrigger(TRIGGER_RESUME + route.getId(), TRIGGER_GROUP + route.getId(), getRouteResumeTime());
        }
        
        return trigger;
    }
    
    public void setRouteStartTime(String routeStartTime) {
        this.routeStartTime = routeStartTime;
    }

    public String getRouteStartTime() {
        return routeStartTime;
    }

    public void setRouteStopTime(String routeStopTime) {
        this.routeStopTime = routeStopTime;
    }

    public String getRouteStopTime() {
        return routeStopTime;
    }

    public void setRouteSuspendTime(String routeSuspendTime) {
        this.routeSuspendTime = routeSuspendTime;
    }

    public String getRouteSuspendTime() {
        return routeSuspendTime;
    }

    public void setRouteResumeTime(String routeResumeTime) {
        this.routeResumeTime = routeResumeTime;
    }

    public String getRouteResumeTime() {
        return routeResumeTime;
    }    

}
