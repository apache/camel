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
package org.apache.camel.management.mbean;

import java.util.ArrayList;
import java.util.List;
import javax.management.MBeanNotificationInfo;
import javax.management.NotificationBroadcasterSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.JmxNotificationBroadcasterAware;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementStrategy;

/**
 * @version 
 */
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
    
    public boolean isIgnoreCamelContextEvents() {
        return getEventNotifier().isIgnoreCamelContextEvents();
    }
    
    public void setIgnoreCamelContextEvents(boolean ignoreCamelContextEvents) {
        getEventNotifier().setIgnoreCamelContextEvents(ignoreCamelContextEvents);
    }
    
    public boolean isIgnoreRouteEvents() {
        return getEventNotifier().isIgnoreRouteEvents();
    }
    
    public void setIgnoreRouteEvents(boolean ignoreRouteEvents) {
        getEventNotifier().setIgnoreRouteEvents(ignoreRouteEvents);
    }
    
    public boolean isIgnoreServiceEvents() {
        return getEventNotifier().isIgnoreServiceEvents();
    }
   
    public void setIgnoreServiceEvents(boolean ignoreServiceEvents) {
        getEventNotifier().setIgnoreServiceEvents(ignoreServiceEvents);
    }
   
    public boolean isIgnoreExchangeEvents() {
        return getEventNotifier().isIgnoreExchangeEvents();
    }
    
    public void setIgnoreExchangeEvents(boolean ignoreExchangeEvents) {
        getEventNotifier().setIgnoreExchangeEvents(ignoreExchangeEvents);
    }
    
    public boolean isIgnoreExchangeCreatedEvent() {
        return getEventNotifier().isIgnoreExchangeCreatedEvent();
    }
   
    public void setIgnoreExchangeCreatedEvent(boolean ignoreExchangeCreatedEvent) {
        getEventNotifier().setIgnoreExchangeCreatedEvent(ignoreExchangeCreatedEvent);
    }
    
    public boolean isIgnoreExchangeCompletedEvent() {
        return getEventNotifier().isIgnoreExchangeCompletedEvent();
    }
    
    public void setIgnoreExchangeCompletedEvent(boolean ignoreExchangeCompletedEvent) {
        getEventNotifier().setIgnoreExchangeCompletedEvent(ignoreExchangeCompletedEvent);
    }
    
    public boolean isIgnoreExchangeFailedEvents() {
        return getEventNotifier().isIgnoreExchangeFailedEvents();
    }
    
    public void setIgnoreExchangeFailedEvents(boolean ignoreExchangeFailedEvents) {
        getEventNotifier().setIgnoreExchangeFailedEvents(ignoreExchangeFailedEvents);
    }

    public boolean isIgnoreExchangeRedeliveryEvents() {
        return getEventNotifier().isIgnoreExchangeRedeliveryEvents();
    }

    public void setIgnoreExchangeRedeliveryEvents(boolean ignoreExchangeRedeliveryEvents) {
        getEventNotifier().setIgnoreExchangeRedeliveryEvents(ignoreExchangeRedeliveryEvents);
    }

    public boolean isIgnoreExchangeSentEvents() {
        return getEventNotifier().isIgnoreExchangeSentEvents();
    }
 
    public void setIgnoreExchangeSentEvents(boolean ignoreExchangeSentEvents) {
        getEventNotifier().setIgnoreExchangeSentEvents(ignoreExchangeSentEvents);
    }
    
    public boolean isIgnoreExchangeSendingEvents() {
        return getEventNotifier().isIgnoreExchangeSendingEvents();
    }

    public void setIgnoreExchangeSendingEvents(boolean ignoreExchangeSendingEvents) {
        getEventNotifier().setIgnoreExchangeSendingEvents(ignoreExchangeSendingEvents);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        // all the class names in the event package
        String[] names = {"CamelContextStartedEvent", "CamelContextStartingEvent", "CamelContextStartupFailureEvent",
                          "CamelContextStopFailureEvent", "CamelContextStoppedEvent", "CamelContextStoppingEvent",
                          "CamelContextSuspendingEvent", "CamelContextSuspendedEvent", "CamelContextResumingEvent", "CamelContextResumedEvent",
                          "CamelContextResumeFailureEvent", "ExchangeCompletedEvent", "ExchangeCreatedEvent", "ExchangeFailedEvent",
                          "ExchangeFailureHandledEvent", "ExchangeRedeliveryEvents", "ExchangeSendingEvent", "ExchangeSentEvent", "RouteStartedEvent",
                          "RouteStoppedEvent", "ServiceStartupFailureEvent", "ServiceStopFailureEvent"};

        List<MBeanNotificationInfo> infos = new ArrayList<MBeanNotificationInfo>();
        for (String name : names) {
            MBeanNotificationInfo info = new MBeanNotificationInfo(new String[]{"org.apache.camel.management.event"},
                    "org.apache.camel.management.event." + name, "The event " + name + " occurred");
            infos.add(info);
        }

        return infos.toArray(new MBeanNotificationInfo[infos.size()]);
    }

}
