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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NamedNode;
import org.apache.camel.impl.event.DefaultEventFactory;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementObjectNameStrategy;
import org.apache.camel.spi.ManagementObjectStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.ServiceHelper;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class DefaultManagementStrategy extends ServiceSupport implements ManagementStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagementStrategy.class);
    private List<EventNotifier> eventNotifiers = new CopyOnWriteArrayList<>();
    private EventFactory eventFactory = new DefaultEventFactory();
    private ManagementObjectNameStrategy managementObjectNameStrategy;
    private ManagementObjectStrategy managementObjectStrategy;
    private ManagementAgent managementAgent;
    private CamelContext camelContext;

    public DefaultManagementStrategy() {
    }

    public DefaultManagementStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public List<EventNotifier> getEventNotifiers() {
        return eventNotifiers;
    }

    public void addEventNotifier(EventNotifier eventNotifier) {
        this.eventNotifiers.add(eventNotifier);
    }

    public boolean removeEventNotifier(EventNotifier eventNotifier) {
        return eventNotifiers.remove(eventNotifier);
    }

    public void setEventNotifiers(List<EventNotifier> eventNotifiers) {
        this.eventNotifiers = eventNotifiers;
    }

    public EventFactory getEventFactory() {
        return eventFactory;
    }

    public void setEventFactory(EventFactory eventFactory) {
        this.eventFactory = eventFactory;
    }

    public ManagementObjectNameStrategy getManagementObjectNameStrategy() {
        if (managementObjectNameStrategy == null) {
            managementObjectNameStrategy = new DefaultManagementObjectNameStrategy();
        }
        return managementObjectNameStrategy;
    }

    public void setManagementObjectNameStrategy(ManagementObjectNameStrategy managementObjectNameStrategy) {
        this.managementObjectNameStrategy = managementObjectNameStrategy;
    }

    public ManagementObjectStrategy getManagementObjectStrategy() {
        if (managementObjectStrategy == null) {
            managementObjectStrategy = new DefaultManagementObjectStrategy();
        }
        return managementObjectStrategy;
    }

    public void setManagementObjectStrategy(ManagementObjectStrategy managementObjectStrategy) {
        this.managementObjectStrategy = managementObjectStrategy;
    }

    public ManagementAgent getManagementAgent() {
        return managementAgent;
    }

    public void setManagementAgent(ManagementAgent managementAgent) {
        this.managementAgent = managementAgent;
    }

    public boolean manageProcessor(NamedNode definition) {
        return false;
    }

    public void manageObject(Object managedObject) throws Exception {
        // noop
    }

    public void manageNamedObject(Object managedObject, Object preferredName) throws Exception {
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

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void notify(CamelEvent event) throws Exception {
        if (eventNotifiers != null && !eventNotifiers.isEmpty()) {
            for (EventNotifier notifier : eventNotifiers) {
                if (notifier.isEnabled(event)) {
                    notifier.notify(event);
                }
            }
        }
    }

    protected void doStart() throws Exception {
        LOG.info("JMX is disabled");
        doStartManagementStrategy();
    }

    protected void doStartManagementStrategy() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        if (eventNotifiers != null) {
            for (EventNotifier notifier : eventNotifiers) {

                // inject CamelContext if the service is aware
                if (notifier instanceof CamelContextAware) {
                    CamelContextAware aware = (CamelContextAware) notifier;
                    aware.setCamelContext(camelContext);
                }

                ServiceHelper.startService(notifier);
            }
        }

        if (managementAgent != null) {
            ServiceHelper.startService(managementAgent);
            // set the naming strategy using the domain name from the agent
            if (managementObjectNameStrategy == null) {
                setManagementObjectNameStrategy(new DefaultManagementObjectNameStrategy(managementAgent.getMBeanObjectDomainName()));
            }
        }
        if (managementObjectNameStrategy instanceof CamelContextAware) {
            ((CamelContextAware) managementObjectNameStrategy).setCamelContext(getCamelContext());
        }
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(managementAgent, eventNotifiers);
    }

}
