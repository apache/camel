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
package org.apache.camel.management.mbean;

import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationBroadcasterSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.JmxNotificationBroadcasterAware;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementStrategy;

public class ManagedEventNotifier extends NotificationBroadcasterSupport implements ManagedEventNotifierMBean {
    private final CamelContext context;
    private final EventNotifier eventNotifier;

    public ManagedEventNotifier(CamelContext context, EventNotifier eventNotifier) {
        this.context = context;
        this.eventNotifier = eventNotifier;
        if (eventNotifier instanceof JmxNotificationBroadcasterAware) {
            ((JmxNotificationBroadcasterAware)eventNotifier).setNotificationBroadcaster(this);
        }
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    public EventNotifier getEventNotifier() {
        return eventNotifier;
    }
    
    @Override
    public boolean isIgnoreCamelContextEvents() {
        return getEventNotifier().isIgnoreCamelContextEvents();
    }
    
    @Override
    public void setIgnoreCamelContextEvents(boolean ignoreCamelContextEvents) {
        getEventNotifier().setIgnoreCamelContextEvents(ignoreCamelContextEvents);
    }
    
    @Override
    public boolean isIgnoreRouteEvents() {
        return getEventNotifier().isIgnoreRouteEvents();
    }
    
    @Override
    public void setIgnoreRouteEvents(boolean ignoreRouteEvents) {
        getEventNotifier().setIgnoreRouteEvents(ignoreRouteEvents);
    }
    
    @Override
    public boolean isIgnoreServiceEvents() {
        return getEventNotifier().isIgnoreServiceEvents();
    }
   
    @Override
    public void setIgnoreServiceEvents(boolean ignoreServiceEvents) {
        getEventNotifier().setIgnoreServiceEvents(ignoreServiceEvents);
    }
   
    @Override
    public boolean isIgnoreExchangeEvents() {
        return getEventNotifier().isIgnoreExchangeEvents();
    }
    
    @Override
    public void setIgnoreExchangeEvents(boolean ignoreExchangeEvents) {
        getEventNotifier().setIgnoreExchangeEvents(ignoreExchangeEvents);
    }
    
    @Override
    public boolean isIgnoreExchangeCreatedEvent() {
        return getEventNotifier().isIgnoreExchangeCreatedEvent();
    }
   
    @Override
    public void setIgnoreExchangeCreatedEvent(boolean ignoreExchangeCreatedEvent) {
        getEventNotifier().setIgnoreExchangeCreatedEvent(ignoreExchangeCreatedEvent);
    }
    
    @Override
    public boolean isIgnoreExchangeCompletedEvent() {
        return getEventNotifier().isIgnoreExchangeCompletedEvent();
    }
    
    @Override
    public void setIgnoreExchangeCompletedEvent(boolean ignoreExchangeCompletedEvent) {
        getEventNotifier().setIgnoreExchangeCompletedEvent(ignoreExchangeCompletedEvent);
    }
    
    @Override
    public boolean isIgnoreExchangeFailedEvents() {
        return getEventNotifier().isIgnoreExchangeFailedEvents();
    }
    
    @Override
    public void setIgnoreExchangeFailedEvents(boolean ignoreExchangeFailedEvents) {
        getEventNotifier().setIgnoreExchangeFailedEvents(ignoreExchangeFailedEvents);
    }

    @Override
    public boolean isIgnoreExchangeRedeliveryEvents() {
        return getEventNotifier().isIgnoreExchangeRedeliveryEvents();
    }

    @Override
    public void setIgnoreExchangeRedeliveryEvents(boolean ignoreExchangeRedeliveryEvents) {
        getEventNotifier().setIgnoreExchangeRedeliveryEvents(ignoreExchangeRedeliveryEvents);
    }

    @Override
    public boolean isIgnoreExchangeSentEvents() {
        return getEventNotifier().isIgnoreExchangeSentEvents();
    }
 
    @Override
    public void setIgnoreExchangeSentEvents(boolean ignoreExchangeSentEvents) {
        getEventNotifier().setIgnoreExchangeSentEvents(ignoreExchangeSentEvents);
    }
    
    @Override
    public boolean isIgnoreExchangeSendingEvents() {
        return getEventNotifier().isIgnoreExchangeSendingEvents();
    }

    @Override
    public void setIgnoreExchangeSendingEvents(boolean ignoreExchangeSendingEvents) {
        getEventNotifier().setIgnoreExchangeSendingEvents(ignoreExchangeSendingEvents);
    }

    @Override
    public boolean isIgnoreStepEvents() {
        return getEventNotifier().isIgnoreStepEvents();
    }

    @Override
    public void setIgnoreStepEvents(boolean ignoreStepEvents) {
        getEventNotifier().setIgnoreStepEvents(ignoreStepEvents);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        // all the class names in the event package
        String[] names = {"CamelContextStartedEvent", "CamelContextStartingEvent", "CamelContextStartupFailureEvent",
                          "CamelContextStopFailureEvent", "CamelContextStoppedEvent", "CamelContextStoppingEvent",
                          "CamelContextSuspendingEvent", "CamelContextSuspendedEvent", "CamelContextResumingEvent", "CamelContextResumedEvent",
                          "CamelContextResumeFailureEvent", "ExchangeCompletedEvent", "ExchangeCreatedEvent", "ExchangeFailedEvent",
                          "ExchangeFailureHandledEvent", "ExchangeRedeliveryEvents", "ExchangeSendingEvent", "ExchangeSentEvent", "RouteStartedEvent",
                          "RouteStoppedEvent", "ServiceStartupFailureEvent", "ServiceStopFailureEvent",
                          "StepStartedEvent", "StepCompletedEvent", "StepFailedEvent"};

        List<MBeanNotificationInfo> infos = new ArrayList<>();
        for (String name : names) {
            MBeanNotificationInfo info = new MBeanNotificationInfo(new String[]{"org.apache.camel.management.event"},
                    "org.apache.camel.management.event." + name, "The event " + name + " occurred");
            infos.add(info);
        }

        return infos.toArray(new MBeanNotificationInfo[infos.size()]);
    }

}
