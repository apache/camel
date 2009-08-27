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
package org.apache.camel.management;

import java.util.EventObject;

import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementNamingStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.fusesource.commons.management.Statistic;

/**
 * A default management strategy that does <b>not</b> manage.
 * <p/>
 * This is default only used if Camel detects that it cannot use the JMX capable
 * {@link org.apache.camel.management.ManagedManagementStrategy} strategy. Then Camel will
 * fallback to use this instead that is basically a simple and <tt>noop</tt> strategy.
 * <p/>
 * This class can also be used to extend your custom management implement. In fact the JMX capable
 * provided by Camel extends this class as well.
 *
 * @see ManagedManagementStrategy
 * @version $Revision$
 */
public class DefaultManagementStrategy implements ManagementStrategy {

    private EventNotifier eventNotifier = new DefaultEventNotifier();
    private EventFactory eventFactory = new DefaultEventFactory();
    private ManagementNamingStrategy managementNamingStrategy;
    private boolean onlyManageProcessorWithCustomId;
    private ManagementAgent managementAgent;

    public EventNotifier getEventNotifier() {
        return eventNotifier;
    }

    public void setEventNotifier(EventNotifier eventNotifier) {
        this.eventNotifier = eventNotifier;
    }

    public EventFactory getEventFactory() {
        return eventFactory;
    }

    public void setEventFactory(EventFactory eventFactory) {
        this.eventFactory = eventFactory;
    }

    public ManagementNamingStrategy getManagementNamingStrategy() {
        if (managementNamingStrategy == null) {
            managementNamingStrategy = new DefaultManagementNamingStrategy();
        }
        return managementNamingStrategy;
    }

    public void setManagementNamingStrategy(ManagementNamingStrategy managementNamingStrategy) {
        this.managementNamingStrategy = managementNamingStrategy;
    }

    public ManagementAgent getManagementAgent() {
        return managementAgent;
    }

    public void setManagementAgent(ManagementAgent managementAgent) {
        this.managementAgent = managementAgent;
    }

    public void onlyManageProcessorWithCustomId(boolean flag) {
        onlyManageProcessorWithCustomId = flag;
    }

    public boolean isOnlyManageProcessorWithCustomId() {
        return onlyManageProcessorWithCustomId;
    }

    public boolean manageProcessor(ProcessorDefinition definition) {
        return false;
    }

    public void manageObject(Object managedObject) throws Exception {
        // noop
    }

    public void manageNamedObject(Object managedObject, Object preferedName) throws Exception {
        // noop
    }

    public <T> T getManagedObjectName(Object managedObject, String customName, Class<T> nameType) throws Exception {
        // noop
        return null;
    }

    public void unmanageObject(Object managedObject) throws Exception {
        // noop
    }

    public void unmanageNamedObject(Object name) throws Exception {
        // noop
    }

    public boolean isManaged(Object managedObject, Object name) {
        // noop
        return false;
    }

    public void notify(EventObject event) throws Exception {
        if (eventNotifier != null && eventNotifier.isEnabled(event)) {
            eventNotifier.notify(event);
        }
    }

    public Statistic createStatistic(String name, Object owner, Statistic.UpdateMode updateMode) {
        // noop
        return null;
    }

    public void start() throws Exception {
        if (managementAgent != null) {
            managementAgent.start();
            // set the naming strategy using the domain name from the agent
            if (managementNamingStrategy == null) {
                setManagementNamingStrategy(new DefaultManagementNamingStrategy(managementAgent.getMBeanObjectDomainName()));
            }
        }
    }

    public void stop() throws Exception {
        if (managementAgent != null) {
            managementAgent.stop();
        }
    }
}
