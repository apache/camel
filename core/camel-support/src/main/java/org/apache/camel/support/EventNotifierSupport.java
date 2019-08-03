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
package org.apache.camel.support;

import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Base class to extend for custom {@link EventNotifier} implementations.
 */
public abstract class EventNotifierSupport extends ServiceSupport implements EventNotifier {

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
    private boolean ignoreStepEvents;

    @Override
    public boolean isIgnoreCamelContextEvents() {
        return ignoreCamelContextEvents;
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return true;
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public void setIgnoreCamelContextEvents(boolean ignoreCamelContextEvents) {
        this.ignoreCamelContextEvents = ignoreCamelContextEvents;
    }

    @Override
    public boolean isIgnoreRouteEvents() {
        return ignoreRouteEvents;
    }

    @Override
    public void setIgnoreRouteEvents(boolean ignoreRouteEvents) {
        this.ignoreRouteEvents = ignoreRouteEvents;
    }

    @Override
    public boolean isIgnoreServiceEvents() {
        return ignoreServiceEvents;
    }

    @Override
    public void setIgnoreServiceEvents(boolean ignoreServiceEvents) {
        this.ignoreServiceEvents = ignoreServiceEvents;
    }

    @Override
    public boolean isIgnoreExchangeEvents() {
        return ignoreExchangeEvents;
    }

    @Override
    public void setIgnoreExchangeEvents(boolean ignoreExchangeEvents) {
        this.ignoreExchangeEvents = ignoreExchangeEvents;
    }

    @Override
    public boolean isIgnoreExchangeCreatedEvent() {
        return ignoreExchangeCreatedEvent;
    }

    @Override
    public void setIgnoreExchangeCreatedEvent(boolean ignoreExchangeCreatedEvent) {
        this.ignoreExchangeCreatedEvent = ignoreExchangeCreatedEvent;
    }

    @Override
    public boolean isIgnoreExchangeCompletedEvent() {
        return ignoreExchangeCompletedEvent;
    }

    @Override
    public void setIgnoreExchangeCompletedEvent(boolean ignoreExchangeCompletedEvent) {
        this.ignoreExchangeCompletedEvent = ignoreExchangeCompletedEvent;
    }

    @Override
    public boolean isIgnoreExchangeFailedEvents() {
        return ignoreExchangeFailedEvents;
    }

    @Override
    public void setIgnoreExchangeFailedEvents(boolean ignoreExchangeFailedEvents) {
        this.ignoreExchangeFailedEvents = ignoreExchangeFailedEvents;
    }

    @Override
    public boolean isIgnoreExchangeRedeliveryEvents() {
        return ignoreExchangeRedeliveryEvents;
    }

    @Override
    public void setIgnoreExchangeRedeliveryEvents(boolean ignoreExchangeRedeliveryEvents) {
        this.ignoreExchangeRedeliveryEvents = ignoreExchangeRedeliveryEvents;
    }

    @Override
    public boolean isIgnoreExchangeSentEvents() {
        return ignoreExchangeSentEvents;
    }

    @Override
    public void setIgnoreExchangeSentEvents(boolean ignoreExchangeSentEvents) {
        this.ignoreExchangeSentEvents = ignoreExchangeSentEvents;
    }

    @Override
    public boolean isIgnoreExchangeSendingEvents() {
        return ignoreExchangeSendingEvents;
    }

    @Override
    public void setIgnoreExchangeSendingEvents(boolean ignoreExchangeSendingEvents) {
        this.ignoreExchangeSendingEvents = ignoreExchangeSendingEvents;
    }

    @Override
    public boolean isIgnoreStepEvents() {
        return ignoreStepEvents;
    }

    @Override
    public void setIgnoreStepEvents(boolean ignoreStepEvents) {
        this.ignoreStepEvents = ignoreStepEvents;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
