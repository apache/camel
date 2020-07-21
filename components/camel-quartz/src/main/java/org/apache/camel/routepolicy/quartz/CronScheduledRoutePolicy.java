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

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.quartz.QuartzComponent;
import org.quartz.CronScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

public class CronScheduledRoutePolicy extends ScheduledRoutePolicy implements ScheduledRoutePolicyConstants {
    private String routeStartTime;
    private String routeStopTime;
    private String routeSuspendTime;
    private String routeResumeTime;
    private String timeZoneString;
    private TimeZone timeZone;

    @Override
    public void onInit(Route route) {
        try {
            doOnInit(route);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    protected void doOnInit(Route route) throws Exception {
        QuartzComponent quartz = route.getCamelContext().getComponent("quartz", QuartzComponent.class);
        quartz.addScheduleInitTask(scheduler -> {
            setScheduler(scheduler);

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
                throw new IllegalArgumentException("Scheduled Route Policy for route " + route.getId() + " has no start/stop/suspend/resume times specified");
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
        });
    }

    @Override
    protected Trigger createTrigger(Action action, Route route) throws Exception {
        Trigger trigger = null;

        CronScheduleBuilder scheduleBuilder = null;
        String triggerPrefix = null;
        if (action == Action.START) {
            scheduleBuilder = CronScheduleBuilder.cronSchedule(getRouteStartTime());
            triggerPrefix = TRIGGER_START;
        } else if (action == Action.STOP) {
            scheduleBuilder = CronScheduleBuilder.cronSchedule(getRouteStopTime());
            triggerPrefix = TRIGGER_STOP;
        } else if (action == Action.SUSPEND) {
            scheduleBuilder = CronScheduleBuilder.cronSchedule(getRouteSuspendTime());
            triggerPrefix = TRIGGER_SUSPEND;
        } else if (action == Action.RESUME) {
            scheduleBuilder = CronScheduleBuilder.cronSchedule(getRouteResumeTime());
            triggerPrefix = TRIGGER_RESUME;
        }

        if (scheduleBuilder != null) {
            if (timeZone != null) {
                scheduleBuilder.inTimeZone(timeZone);
            }

            TriggerKey triggerKey = new TriggerKey(triggerPrefix + route.getId(), TRIGGER_GROUP + route.getId());
            trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(scheduleBuilder)
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

    public String getTimeZone() {
        return timeZoneString;
    }

    public void setTimeZone(String timeZone) {
        this.timeZoneString = timeZone;
        this.timeZone = TimeZone.getTimeZone(timeZone);
    }

}
