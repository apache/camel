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

public interface ManagedEventNotifierMBean {
    
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

    void setIgnoreExchangeFailedEvents(boolean ignoreExchangeFailedEvents);

    boolean isIgnoreExchangeRedeliveryEvents();

    void setIgnoreExchangeRedeliveryEvents(boolean ignoreExchangeRedeliveryEvents);

    boolean isIgnoreExchangeSentEvents();

    void setIgnoreExchangeSentEvents(boolean ignoreExchangeSentEvents);

    boolean isIgnoreExchangeSendingEvents();

    void setIgnoreExchangeSendingEvents(boolean ignoreExchangeSendingEvents);

    boolean isIgnoreStepEvents();

    void setIgnoreStepEvents(boolean ignoreStepEvents);

}
