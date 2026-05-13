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
package org.apache.camel.spi;

/**
 * Notifier to send {@link CamelEvent} notifications.
 *
 * @see CamelEvent
 * @see EventFactory
 */
public interface EventNotifier {

    /**
     * Notifies the given event
     *
     * @param  event     the event
     * @throws Exception can be thrown if notification failed
     */
    void notify(CamelEvent event) throws Exception;

    /**
     * Checks whether notification for the given event is enabled.
     * <p/>
     * If disabled the event will not be sent and silently ignored instead.
     *
     * @param  event the event
     * @return       <tt>true</tt> if the event should be sent, <tt>false</tt> to silently ignore it
     */
    boolean isEnabled(CamelEvent event);

    /**
     * Checks whether notification is disabled for all events
     *
     * @return <tt>true</tt> if disabled and no events is being notified.
     */
    boolean isDisabled();

    /** Whether to ignore CamelContext initialization events. */
    boolean isIgnoreCamelContextInitEvents();

    /** Set whether to ignore CamelContext initialization events. */
    void setIgnoreCamelContextInitEvents(boolean ignoreCamelContextInitEvents);

    /** Whether to ignore CamelContext lifecycle events (start, stop, suspend, resume). */
    boolean isIgnoreCamelContextEvents();

    /** Set whether to ignore CamelContext lifecycle events (start, stop, suspend, resume). */
    void setIgnoreCamelContextEvents(boolean ignoreCamelContextEvents);

    /** Whether to ignore route lifecycle events (added, removed, started, stopped). */
    boolean isIgnoreRouteEvents();

    /** Set whether to ignore route lifecycle events (added, removed, started, stopped). */
    void setIgnoreRouteEvents(boolean ignoreRouteEvents);

    /** Whether to ignore service lifecycle events (start failure, stop failure). */
    boolean isIgnoreServiceEvents();

    /** Set whether to ignore service lifecycle events (start failure, stop failure). */
    void setIgnoreServiceEvents(boolean ignoreServiceEvents);

    /** Whether to ignore all exchange events. */
    boolean isIgnoreExchangeEvents();

    /** Set whether to ignore all exchange events. */
    void setIgnoreExchangeEvents(boolean ignoreExchangeEvents);

    /** Whether to ignore exchange created events. */
    boolean isIgnoreExchangeCreatedEvent();

    /** Set whether to ignore exchange created events. */
    void setIgnoreExchangeCreatedEvent(boolean ignoreExchangeCreatedEvent);

    /** Whether to ignore exchange completed events. */
    boolean isIgnoreExchangeCompletedEvent();

    /** Set whether to ignore exchange completed events. */
    void setIgnoreExchangeCompletedEvent(boolean ignoreExchangeCompletedEvent);

    /** Whether to ignore exchange failed events. */
    boolean isIgnoreExchangeFailedEvents();

    /** Set whether to ignore exchange failed events. */
    void setIgnoreExchangeFailedEvents(boolean ignoreExchangeFailureEvents);

    /** Whether to ignore exchange redelivery events. */
    boolean isIgnoreExchangeRedeliveryEvents();

    /** Set whether to ignore exchange redelivery events. */
    void setIgnoreExchangeRedeliveryEvents(boolean ignoreExchangeRedeliveryEvents);

    /** Whether to ignore exchange sent events (after sending to an endpoint). */
    boolean isIgnoreExchangeSentEvents();

    /** Set whether to ignore exchange sent events (after sending to an endpoint). */
    void setIgnoreExchangeSentEvents(boolean ignoreExchangeSentEvents);

    /** Whether to ignore exchange sending events (before sending to an endpoint). */
    boolean isIgnoreExchangeSendingEvents();

    /** Set whether to ignore exchange sending events (before sending to an endpoint). */
    void setIgnoreExchangeSendingEvents(boolean ignoreExchangeSendingEvents);

    /** Whether to ignore step events. */
    boolean isIgnoreStepEvents();

    /** Set whether to ignore step events. */
    void setIgnoreStepEvents(boolean ignoreStepEvents);

    /** @deprecated Set whether to ignore exchange async processing started events. */
    @Deprecated(since = "4.19.0")
    void setIgnoreExchangeAsyncProcessingStartedEvents(boolean ignoreExchangeAsyncProcessingStartedEvents);

    /** @deprecated Whether to ignore exchange async processing started events. */
    @Deprecated(since = "4.19.0")
    boolean isIgnoreExchangeAsyncProcessingStartedEvents();
}
