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

import java.util.List;

import org.apache.camel.NamedNode;
import org.apache.camel.Service;
import org.apache.camel.StaticService;

/**
 * Top-level SPI for Camel's management and observability layer, coordinating JMX registration, lifecycle events, and
 * statistics collection across all Camel artifacts.
 * <p/>
 * When the {@code camel-management} JAR is on the classpath, {@code JmxManagementStrategy} is activated and delegates
 * MBean registration to a {@link ManagementAgent}. Without that JAR, {@code DefaultManagementStrategy} is used, which
 * still dispatches lifecycle events to registered {@link EventNotifier}s but does not expose JMX MBeans.
 * <p/>
 * The strategy is the central hub for two concerns: management objects (registering and unregistering Camel components,
 * routes, processors, etc. as MBeans via {@link ManagementObjectStrategy} and {@link ManagementObjectNameStrategy}) and
 * events (delivering {@link CamelEvent} notifications to all registered {@link EventNotifier}s via
 * {@link #notify(CamelEvent)}). A third-party management backend can be plugged in by implementing this interface and
 * providing a {@link ManagementStrategyFactory}.
 * <p/>
 * See <a href="https://camel.apache.org/manual/jmx.html">JMX</a> in the Camel user manual.
 *
 * @see ManagementAgent
 * @see EventNotifier
 * @see EventFactory
 * @see ManagementStrategyFactory
 */
public interface ManagementStrategy extends StaticService {

    /**
     * Adds a managed object allowing the ManagementStrategy implementation to record or expose the object as it sees
     * fit.
     *
     * @param  managedObject the managed object
     * @throws Exception     can be thrown if the object could not be added
     */
    void manageObject(Object managedObject) throws Exception;

    /**
     * Removes the managed object.
     *
     * @param  managedObject the managed object
     * @throws Exception     can be thrown if the object could not be removed
     */
    void unmanageObject(Object managedObject) throws Exception;

    /**
     * Determines if an object or name is managed.
     *
     * @param  managedObject the object to consider
     * @return               <tt>true</tt> if the given object is managed
     */
    boolean isManaged(Object managedObject);

    /**
     * Determines if an object or name is managed.
     *
     * @param  name the name to consider
     * @return      <tt>true</tt> if the given name is managed
     */
    boolean isManagedName(Object name);

    /**
     * Management events provide a single model for capturing information about execution points in the application
     * code. Management strategy implementations decide if and where to record these events. Applications communicate
     * events to management strategy implementations via the notify(EventObject) method.
     *
     * @param  event     the event
     * @throws Exception can be thrown if the notification failed
     */
    void notify(CamelEvent event) throws Exception;

    /**
     * Gets the event notifiers.
     *
     * @return event notifiers
     */
    List<EventNotifier> getEventNotifiers();

    /**
     * Gets all the started event notifiers, which is ready to be used.
     *
     * @return started event notifiers
     */
    List<EventNotifier> getStartedEventNotifiers();

    /**
     * Adds the event notifier to use.
     * <p/>
     * Ensure the event notifier has been started if its a {@link Service}, as otherwise it would not be used.
     *
     * @param eventNotifier event notifier
     */
    void addEventNotifier(EventNotifier eventNotifier);

    /**
     * Removes the event notifier
     *
     * @param  eventNotifier event notifier to remove
     * @return               <tt>true</tt> if removed, <tt>false</tt> if already removed
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
    ManagementObjectNameStrategy getManagementObjectNameStrategy();

    /**
     * Sets the naming strategy to use
     *
     * @param strategy naming strategy
     */
    void setManagementObjectNameStrategy(ManagementObjectNameStrategy strategy);

    /**
     * Gets the object strategy to use
     *
     * @return object strategy
     */
    ManagementObjectStrategy getManagementObjectStrategy();

    /**
     * Sets the object strategy to use
     *
     * @param strategy object strategy
     */
    void setManagementObjectStrategy(ManagementObjectStrategy strategy);

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
     * @param  definition definition of the processor
     * @return            <tt>true</tt> to manage it
     */
    boolean manageProcessor(NamedNode definition);

}
