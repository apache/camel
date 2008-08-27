/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jmxconnect;

import javax.management.*;
import java.io.IOException;
import java.util.Set;

/**
 * Acts as a delegate for the MBeanServerConnection
 *
 * @version $Revision$
 */
public class MBeanServerConnectionDelegate implements MBeanServerConnection {

    protected MBeanServerConnection connection;

    public MBeanServerConnectionDelegate(MBeanServerConnection connection) {
        this.connection = connection;

    }

    public ObjectInstance createMBean(String className, ObjectName name) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, IOException {
        return connection.createMBean(className, name);
    }

    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException, IOException {
        return connection.createMBean(className, name, loaderName);
    }

    public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, IOException {
        return connection.createMBean(className, name, params, signature);
    }


    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException, IOException {
        return connection.createMBean(className, name, loaderName, params, signature);
    }

    public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException, IOException {
        connection.unregisterMBean(name);

    }

    public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException, IOException {
        return connection.getObjectInstance(name);
    }

    public Set queryMBeans(ObjectName name, QueryExp query) throws IOException {
        return connection.queryMBeans(name, query);
    }

    public Set queryNames(ObjectName name, QueryExp query) throws IOException {
        return connection.queryNames(name, query);
    }

    public boolean isRegistered(ObjectName name) throws IOException {
        return connection.isRegistered(name);
    }

    public Integer getMBeanCount() throws IOException {
        return connection.getMBeanCount();
    }

    public Object getAttribute(ObjectName name, String attribute) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
        return connection.getAttribute(name, attribute);
    }

    public AttributeList getAttributes(ObjectName name, String[] attributes) throws InstanceNotFoundException, ReflectionException, IOException {
        return connection.getAttributes(name, attributes);
    }

    public void setAttribute(ObjectName name, Attribute attribute) throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException, IOException {
        connection.setAttribute(name, attribute);

    }

    public AttributeList setAttributes(ObjectName name, AttributeList attributes) throws InstanceNotFoundException, ReflectionException, IOException {
        return connection.setAttributes(name, attributes);
    }

    public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature) throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return connection.invoke(name, operationName, params, signature);
    }

    public String getDefaultDomain() throws IOException {
        return connection.getDefaultDomain();
    }

    public String[] getDomains() throws IOException {
        return connection.getDomains();
    }

    public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, IOException {
        connection.addNotificationListener(name, listener, filter, handback);

    }

    public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, IOException {
        connection.addNotificationListener(name, listener, filter, handback);

    }

    public void removeNotificationListener(ObjectName name, ObjectName listener) throws InstanceNotFoundException, ListenerNotFoundException, IOException {
        connection.removeNotificationListener(name, listener);

    }

    public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, ListenerNotFoundException, IOException {
        connection.removeNotificationListener(name, listener, filter, handback);

    }

    public void removeNotificationListener(ObjectName name, NotificationListener listener) throws InstanceNotFoundException, ListenerNotFoundException, IOException {
        connection.removeNotificationListener(name, listener);

    }

    public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, ListenerNotFoundException, IOException {
        connection.removeNotificationListener(name, listener, filter, handback);

    }

    public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        return connection.getMBeanInfo(name);
    }

    public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException, IOException {
        return connection.isInstanceOf(name, className);
    }


}