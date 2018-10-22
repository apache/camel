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

import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.impl.DefaultManagementStrategy;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementObjectNameStrategy;
import org.apache.camel.spi.ManagementObjectStrategy;

/**
 * A JMX capable {@link org.apache.camel.spi.ManagementStrategy} that Camel by default uses if possible.
 * <p/>
 * Camel detects whether its possible to use this JMX capable strategy and if <b>not</b> then Camel
 * will fallback to the {@link DefaultManagementStrategy} instead.
 *
 * @see org.apache.camel.spi.ManagementStrategy
 * @see DefaultManagementStrategy
 */
public class ManagedManagementStrategy extends DefaultManagementStrategy {

    public ManagedManagementStrategy() {
    }

    public ManagedManagementStrategy(CamelContext context, ManagementAgent managementAgent) {
        super(context, managementAgent);
        context.setExtension(
                ManagedCamelContext.class,
                new ManagedCamelContextImpl(context));
        // must add management lifecycle strategy
        context.getLifecycleStrategies().add(0, new DefaultManagementLifecycleStrategy(context));
    }

    public void manageObject(Object managedObject) throws Exception {
        ObjectName objectName = getManagementObjectNameStrategy().getObjectName(managedObject);
        if (objectName != null) {
            getManagementAgent().register(managedObject, objectName);
        }
    }

    public void unmanageObject(Object managedObject) throws Exception {
        ObjectName objectName = getManagementObjectNameStrategy().getObjectName(managedObject);
        if (objectName != null) {
            getManagementAgent().unregister(objectName);
        }
    }

    public boolean isManaged(Object managedObject) {
        try {
            ObjectName name = getManagementObjectNameStrategy().getObjectName(managedObject);
            if (name != null) {
                return getManagementAgent().isRegistered(name);
            }
        } catch (Exception e) {
            log.warn("Cannot check whether the managed object is registered. This exception will be ignored.", e);
        }
        return false;
    }

    public boolean isManagedName(Object name) {
        try {
            if (name instanceof ObjectName) {
                return getManagementAgent().isRegistered((ObjectName) name);
            }
        } catch (Exception e) {
            log.warn("Cannot check whether the managed object is registered. This exception will be ignored.", e);
        }
        return false;
    }

    @Override
    public boolean manageProcessor(NamedNode definition) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        log.info("JMX is enabled");
        doStartManagementStrategy();
    }

    protected ManagementObjectNameStrategy createManagementObjectNameStrategy(String domain) {
        return new DefaultManagementObjectNameStrategy(domain);
    }

    protected ManagementObjectStrategy createManagementObjectStrategy() {
        return new DefaultManagementObjectStrategy();
    }

}
