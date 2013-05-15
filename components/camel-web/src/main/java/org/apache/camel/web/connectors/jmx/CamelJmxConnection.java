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
package org.apache.camel.web.connectors.jmx;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.web.connectors.CamelConnection;
import org.apache.camel.web.connectors.CamelDataBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class CamelJmxConnection implements CamelConnection {

    private final MBeanServerConnection connection;

    public CamelJmxConnection(String jmxServiceUrl) throws IOException {
        JMXServiceURL url = new JMXServiceURL(jmxServiceUrl);
        //TODO Handle credentials
        JMXConnector connector = JMXConnectorFactory.connect(url);
        connection = connector.getMBeanServerConnection();
    }

    protected MBeanInfo getMBeanInfo(ObjectInstance objInstance) throws IntrospectionException, InstanceNotFoundException, IOException, ReflectionException {
        //TODO Caching MBeanInfo
    	MBeanInfo mBeanInfo = connection.getMBeanInfo(objInstance.getObjectName());
        return mBeanInfo;
    }

    protected Set<ObjectInstance> getObjectInstances(String type) throws IOException, MalformedObjectNameException {
        Set<ObjectInstance> beans = connection.queryMBeans(new ObjectName("org.apache.camel:type=" + type + ",*"), null);
        return beans;
    }

    ObjectInstance getObjectInstance(String type, String name) throws MalformedObjectNameException,
            NullPointerException, IOException {
        Set<ObjectInstance> beans = connection.queryMBeans(new ObjectName("org.apache.camel:type=" + type + ",name="
                + name + ",*"), null);
        return beans.isEmpty() ? null : beans.iterator().next();
    }

    public CamelDataBean getCamelBean(String type, String name) {
        CamelDataBean bean;
        try {
            ObjectInstance instance = getObjectInstance(type, name);
            MBeanInfo info = getMBeanInfo(instance);
            bean = new CamelBeanFactory().build(instance, connection, info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return bean;
    }

    public List<CamelDataBean> getCamelBeans(String type) {
        List<CamelDataBean> endpoints = new ArrayList<CamelDataBean>();
        try {
            Set<ObjectInstance> beans = getObjectInstances(type);
            for (ObjectInstance instance : beans) {
                MBeanInfo info = getMBeanInfo(instance);
                CamelDataBean endpoint = new CamelBeanFactory().build(instance, connection, info);
                endpoints.add(endpoint);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return endpoints;
    }

    public Object invokeOperation(String type, String name, String operationName, Object[] params, String[] signature) {
        Object result;
        try {
            ObjectInstance instance = getObjectInstance(type, name);
            result = connection.invoke(instance.getObjectName(), operationName, params, signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private class CamelBeanFactory {

        public CamelDataBean build(ObjectInstance instance, MBeanServerConnection connection, MBeanInfo info)
                throws InstanceNotFoundException, IOException, ReflectionException {

            CamelDataBean c = new CamelDataBean();
            String name = instance.getObjectName().getKeyProperty("name");
            if (name.endsWith("\""))
                name = name.substring(0, name.length() - 1);
            if (name.startsWith("\""))
                name = name.substring(1);

            c.setName(name);
            c.setDescription(info.getDescription());

            MBeanAttributeInfo[] attributes = info.getAttributes();
            List<String> attributeNames = new ArrayList<String>();
            for (MBeanAttributeInfo attribute : attributes) {
                attributeNames.add(attribute.getName());
            }

            List attributeList = connection.getAttributes(instance.getObjectName(), attributeNames.toArray(new String[0]));
            List<Attribute> list = attributeList;
            for (Attribute attribute : list) {
                c.getProperties().put(attribute.getName(), attribute.getValue());
            }

            return c;
        }

    }
}