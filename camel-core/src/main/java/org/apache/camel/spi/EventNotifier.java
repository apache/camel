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
package org.apache.camel.spi;

import java.util.EventObject;

/**
 * Notifier to send {@link java.util.EventObject events}.
 *
 * @see org.apache.camel.spi.EventFactory
 * @version 
 */
public interface EventNotifier {

    /**
     * Notifies the given event
     *
     * @param event the event
     * @throws Exception can be thrown if notification failed
     */
    void notify(EventObject event) throws Exception;

    /**
     * Checks whether notification for the given event is enabled.
     * <p/>
     * If disabled the event will not be sent and silently ignored instead.
     *
     * @param event the event
     * @return <tt>true</tt> if the event should be sent, <tt>false</tt> to silently ignore it
     */
    boolean isEnabled(EventObject event);

    /**
     * Checks whether notification is disabled for all events
     *
     * @return <tt>true</tt> if disabled and no events is being notified.
     */
    boolean isDisabled();

    boolean isIgnoreCamelContextEvents();

    void setIgnoreCamelContextEvents(boolean ignoreCamelContextEvents);

    boolean isIgnoreRouteEvents();

    void setIgnoreRouteEvents(boolean ignoreRouteEvents);

    boolean isIgnoreServiceEvents();

    void setIgnoreServiceEvents(boolean ignoreServiceEvents);

    boolean isIgnoreExchangeEvents();

    void setIgnoreExchangeEvents(boolean ignoreExchangeEvents);

    boolean isIgnoreExchangeCreatedEvent();

    void setIgnoreExchangeCreatedEvent(boolean ignoreExchangeCreatedEvent);

    boolean isIgnoreExchangeCompletedEvent();

    void setIgnoreExchangeCompletedEvent(boolean ignoreExchangeCompletedEvent);

    boolean isIgnoreExchangeFailedEvents();

    void setIgnoreExchangeFailedEvents(boolean ignoreExchangeFailureEvents);

    boolean isIgnoreExchangeRedeliveryEvents();

    void setIgnoreExchangeRedeliveryEvents(boolean ignoreExchangeRedeliveryEvents);

    boolean isIgnoreExchangeSentEvents();

    void setIgnoreExchangeSentEvents(boolean ignoreExchangeSentEvents);

    boolean isIgnoreExchangeSendingEvents();

    void setIgnoreExchangeSendingEvents(boolean ignoreExchangeSendingEvents);

}
