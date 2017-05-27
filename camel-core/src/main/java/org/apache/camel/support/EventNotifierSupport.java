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
package org.apache.camel.support;

import org.apache.camel.spi.EventNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class to extend for custom {@link EventNotifier} implementations.
 *
 * @version 
 */
public abstract class EventNotifierSupport extends ServiceSupport implements EventNotifier {
    protected Logger log = LoggerFactory.getLogger(getClass());
    private boolean ignoreCamelContextEvents;
    private boolean ignoreRouteEvents;
    private boolean ignoreServiceEvents;
    private boolean ignoreExchangeEvents;
    private boolean ignoreExchangeCreatedEvent;
    private boolean ignoreExchangeCompletedEvent;
    private boolean ignoreExchangeFailedEvents;
    private boolean ignoreExchangeRedeliveryEvents;
    private boolean ignoreExchangeSendingEvents;
    private boolean ignoreExchangeSentEvents;

    public boolean isIgnoreCamelContextEvents() {
        return ignoreCamelContextEvents;
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    public void setIgnoreCamelContextEvents(boolean ignoreCamelContextEvents) {
        this.ignoreCamelContextEvents = ignoreCamelContextEvents;
    }

    public boolean isIgnoreRouteEvents() {
        return ignoreRouteEvents;
    }

    public void setIgnoreRouteEvents(boolean ignoreRouteEvents) {
        this.ignoreRouteEvents = ignoreRouteEvents;
    }

    public boolean isIgnoreServiceEvents() {
        return ignoreServiceEvents;
    }

    public void setIgnoreServiceEvents(boolean ignoreServiceEvents) {
        this.ignoreServiceEvents = ignoreServiceEvents;
    }

    public boolean isIgnoreExchangeEvents() {
        return ignoreExchangeEvents;
    }

    public void setIgnoreExchangeEvents(boolean ignoreExchangeEvents) {
        this.ignoreExchangeEvents = ignoreExchangeEvents;
    }

    public boolean isIgnoreExchangeCreatedEvent() {
        return ignoreExchangeCreatedEvent;
    }

    public void setIgnoreExchangeCreatedEvent(boolean ignoreExchangeCreatedEvent) {
        this.ignoreExchangeCreatedEvent = ignoreExchangeCreatedEvent;
    }

    public boolean isIgnoreExchangeCompletedEvent() {
        return ignoreExchangeCompletedEvent;
    }

    public void setIgnoreExchangeCompletedEvent(boolean ignoreExchangeCompletedEvent) {
        this.ignoreExchangeCompletedEvent = ignoreExchangeCompletedEvent;
    }

    public boolean isIgnoreExchangeFailedEvents() {
        return ignoreExchangeFailedEvents;
    }

    public void setIgnoreExchangeFailedEvents(boolean ignoreExchangeFailedEvents) {
        this.ignoreExchangeFailedEvents = ignoreExchangeFailedEvents;
    }

    public boolean isIgnoreExchangeRedeliveryEvents() {
        return ignoreExchangeRedeliveryEvents;
    }

    public void setIgnoreExchangeRedeliveryEvents(boolean ignoreExchangeRedeliveryEvents) {
        this.ignoreExchangeRedeliveryEvents = ignoreExchangeRedeliveryEvents;
    }

    public boolean isIgnoreExchangeSentEvents() {
        return ignoreExchangeSentEvents;
    }

    public void setIgnoreExchangeSentEvents(boolean ignoreExchangeSentEvents) {
        this.ignoreExchangeSentEvents = ignoreExchangeSentEvents;
    }

    public boolean isIgnoreExchangeSendingEvents() {
        return ignoreExchangeSendingEvents;
    }

    public void setIgnoreExchangeSendingEvents(boolean ignoreExchangeSendingEvents) {
        this.ignoreExchangeSendingEvents = ignoreExchangeSendingEvents;
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        // noop
    }
}
