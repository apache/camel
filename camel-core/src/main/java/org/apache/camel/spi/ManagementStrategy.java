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

import java.util.List;

import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorDefinition;

/**
 * Strategy for management.
 * <p/>
 * This is totally pluggable allowing to use a custom or 3rd party management implementation with Camel.
 *
 * @see org.apache.camel.spi.EventNotifier
 * @see org.apache.camel.spi.EventFactory
 * @see org.apache.camel.spi.ManagementNamingStrategy
 * @see org.apache.camel.spi.ManagementAgent
 * @version 
 */
public interface ManagementStrategy extends org.fusesource.commons.management.ManagementStrategy, Service {

    /**
     * Gets the event notifiers.
     *
     * @return event notifiers
     */
    List<EventNotifier> getEventNotifiers();

    /**
     * Sets the list of event notifier to use.
     *
     * @param eventNotifier list of event notifiers
     */
    void setEventNotifiers(List<EventNotifier> eventNotifier);

    /**
     * Adds the event notifier to use.
     * <p/>
     * Ensure the event notifier has been started if its a {@link Service}, as otherwise
     * it would not be used.
     *
     * @param eventNotifier event notifier
     */
    void addEventNotifier(EventNotifier eventNotifier);

    /**
     * Removes the event notifier
     *
     * @param eventNotifier event notifier to remove
     * @return <tt>true</tt> if removed, <tt>false</tt> if already removed
     */
    boolean removeEventNotifier(EventNotifier eventNotifier);

    /**
     * Gets the event factory
     *
     * @return event factory
     */
    EventFactory getEventFactory();

    /**
     * Sets the event factory to use
     *
     * @param eventFactory event factory
     */
    void setEventFactory(EventFactory eventFactory);

    /**
     * Gets the naming strategy to use
     *
     * @return naming strategy
     */
    ManagementNamingStrategy getManagementNamingStrategy();

    /**
     * Sets the naming strategy to use
     *
     * @param strategy naming strategy
     */
    void setManagementNamingStrategy(ManagementNamingStrategy strategy);

    /**
     * Gets the management agent
     *
     * @return management agent
     */
    ManagementAgent getManagementAgent();

    /**
     * Sets the management agent to use
     *
     * @param managementAgent management agent
     */
    void setManagementAgent(ManagementAgent managementAgent);

    /**
     * Filter whether the processor should be managed or not.
     * <p/>
     * Is used to filter out unwanted processors to avoid managing at too fine grained level.
     *
     * @param definition definition of the processor
     * @return <tt>true</tt> to manage it
     */
    boolean manageProcessor(ProcessorDefinition<?> definition);

    /**
     * Sets the whether only manage processors if they have been configured with a custom id
     * <p/>
     * Default is false.
     *
     * @param flag <tt>true</tt> will only manage if custom id was set.
     */
    void onlyManageProcessorWithCustomId(boolean flag);

    /**
     * Checks whether only to manage processors if they have been configured with a custom id
     *
     * @return true or false
     */
    boolean isOnlyManageProcessorWithCustomId();

    /**
     * Sets the statistics level
     * <p/>
     * Default is {@link org.apache.camel.ManagementStatisticsLevel#All}
     *
     * @param level the new level
     */
    void setStatisticsLevel(ManagementStatisticsLevel level);

    /**
     * Gets the statistics level
     *
     * @return the level
     */
    ManagementStatisticsLevel getStatisticsLevel();
}
