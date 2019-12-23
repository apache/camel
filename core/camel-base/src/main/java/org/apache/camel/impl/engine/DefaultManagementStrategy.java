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
package org.apache.camel.impl.engine;

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
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A default management strategy that does <b>not</b> manage.
 * <p/>
 * This is default only used if Camel detects that it cannot use the JMX capable
 * {@link org.apache.camel.management.JmxManagementStrategy} strategy. Then Camel will
 * fallback to use this instead that is basically a simple and <tt>noop</tt> strategy.
 * <p/>
 * This class can also be used to extend your custom management implement. In fact the JMX capable
 * provided by Camel extends this class as well.
 *
 * @see org.apache.camel.management.JmxManagementStrategy
 */
public class DefaultManagementStrategy extends ServiceSupport implements ManagementStrategy, CamelContextAware {

    private final List<EventNotifier> eventNotifiers = new CopyOnWriteArrayList<>();
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

    public DefaultManagementStrategy(CamelContext camelContext, ManagementAgent managementAgent) {
        this.camelContext = camelContext;
        this.managementAgent = managementAgent;
    }

    @Override
    public List<EventNotifier> getEventNotifiers() {
        return eventNotifiers;
    }

    @Override
    public void addEventNotifier(EventNotifier eventNotifier) {
        this.eventNotifiers.add(eventNotifier);
    }

    @Override
    public boolean removeEventNotifier(EventNotifier eventNotifier) {
        return eventNotifiers.remove(eventNotifier);
    }

    @Override
    public EventFactory getEventFactory() {
        return eventFactory;
    }

    @Override
    public void setEventFactory(EventFactory eventFactory) {
        this.eventFactory = eventFactory;
    }

    @Override
    public ManagementObjectNameStrategy getManagementObjectNameStrategy() {
        if (managementObjectNameStrategy == null) {
            managementObjectNameStrategy = createManagementObjectNameStrategy(null);
        }
        return managementObjectNameStrategy;
    }

    @Override
    public void setManagementObjectNameStrategy(ManagementObjectNameStrategy managementObjectNameStrategy) {
        this.managementObjectNameStrategy = managementObjectNameStrategy;
    }

    @Override
    public ManagementObjectStrategy getManagementObjectStrategy() {
        if (managementObjectStrategy == null) {
            managementObjectStrategy = createManagementObjectStrategy();
        }
        return managementObjectStrategy;
    }

    @Override
    public void setManagementObjectStrategy(ManagementObjectStrategy managementObjectStrategy) {
        this.managementObjectStrategy = managementObjectStrategy;
    }

    @Override
    public ManagementAgent getManagementAgent() {
        return managementAgent;
    }

    @Override
    public void setManagementAgent(ManagementAgent managementAgent) {
        this.managementAgent = managementAgent;
    }

    @Override
    public boolean manageProcessor(NamedNode definition) {
        return false;
    }

    @Override
    public void manageObject(Object managedObject) throws Exception {
        // noop
    }

    @Override
    public void unmanageObject(Object managedObject) throws Exception {
        // noop
    }

    @Override
    public boolean isManaged(Object managedObject) {
        // noop
        return false;
    }

    @Override
    public boolean isManagedName(Object name) {
        // noop
        return false;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (!eventNotifiers.isEmpty()) {
            for (EventNotifier notifier : eventNotifiers) {
                if (notifier.isEnabled(event)) {
                    notifier.notify(event);
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        log.info("JMX is disabled");
        doStartManagementStrategy();
    }

    protected void doStartManagementStrategy() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        for (EventNotifier notifier : eventNotifiers) {

            // inject CamelContext if the service is aware
            if (notifier instanceof CamelContextAware) {
                CamelContextAware aware = (CamelContextAware) notifier;
                aware.setCamelContext(camelContext);
            }

            ServiceHelper.startService(notifier);
        }

        if (managementAgent != null) {
            ServiceHelper.startService(managementAgent);
            // set the naming strategy using the domain name from the agent
            if (managementObjectNameStrategy == null) {
                String domain = managementAgent.getMBeanObjectDomainName();
                managementObjectNameStrategy = createManagementObjectNameStrategy(domain);
            }
        }
        if (managementObjectNameStrategy instanceof CamelContextAware) {
            ((CamelContextAware) managementObjectNameStrategy).setCamelContext(getCamelContext());
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(managementAgent, eventNotifiers);
    }

    protected ManagementObjectNameStrategy createManagementObjectNameStrategy(String domain) {
        return null;
    }

    protected ManagementObjectStrategy createManagementObjectStrategy() {
        return null;
    }

}
