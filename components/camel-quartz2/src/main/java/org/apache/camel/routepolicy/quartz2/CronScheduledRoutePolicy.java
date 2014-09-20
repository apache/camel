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
package org.apache.camel.routepolicy.quartz2;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Route;
import org.apache.camel.component.quartz2.QuartzComponent;
import org.apache.camel.util.ObjectHelper;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

public class CronScheduledRoutePolicy extends ScheduledRoutePolicy implements ScheduledRoutePolicyConstants {
    private String routeStartTime;
    private String routeStopTime;
    private String routeSuspendTime;
    private String routeResumeTime;
    
    public void onInit(Route route) {
        try {
            doOnInit(route);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected void doOnInit(Route route) throws Exception {
        QuartzComponent quartz = route.getRouteContext().getCamelContext().getComponent("quartz2", QuartzComponent.class);
        setScheduler(quartz.getScheduler());

        // Important: do not start scheduler as QuartzComponent does that automatic
        // when CamelContext has been fully initialized and started

        if (getRouteStopGracePeriod() == 0) {
            setRouteStopGracePeriod(10000);
        }

        if (getTimeUnit() == null) {
            setTimeUnit(TimeUnit.MILLISECONDS);
        }

        // validate time options has been configured
        if ((getRouteStartTime() == null) && (getRouteStopTime() == null) && (getRouteSuspendTime() == null) && (getRouteResumeTime() == null)) {
            throw new IllegalArgumentException("Scheduled Route Policy for route {} has no start/stop/suspend/resume times specified");
        }

        registerRouteToScheduledRouteDetails(route);
        if (getRouteStartTime() != null) {
            scheduleRoute(Action.START, route);
        }
        if (getRouteStopTime() != null) {
            scheduleRoute(Action.STOP, route);
        }

        if (getRouteSuspendTime() != null) {
            scheduleRoute(Action.SUSPEND, route);
        }
        if (getRouteResumeTime() != null) {
            scheduleRoute(Action.RESUME, route);
        }
    }

    @Override
    protected Trigger createTrigger(Action action, Route route) throws Exception {
        CronTrigger trigger = null;

        if (action == Action.START) {
            trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_START + route.getId(), TRIGGER_GROUP + route.getId())
                    .withSchedule(CronScheduleBuilder.cronSchedule(getRouteStartTime()))
                    .build();
        } else if (action == Action.STOP) {
            trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_STOP + route.getId(), TRIGGER_GROUP + route.getId())
                    .withSchedule(CronScheduleBuilder.cronSchedule(getRouteStopTime()))
                    .build();
        } else if (action == Action.SUSPEND) {
            trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_SUSPEND + route.getId(), TRIGGER_GROUP + route.getId())
                    .withSchedule(CronScheduleBuilder.cronSchedule(getRouteSuspendTime()))
                    .build();
        } else if (action == Action.RESUME) {
            trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_RESUME + route.getId(), TRIGGER_GROUP + route.getId())
                    .withSchedule(CronScheduleBuilder.cronSchedule(getRouteResumeTime()))
                    .build();
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
