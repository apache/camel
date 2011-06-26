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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Service;

/**
 * Camel JMX service agent
 */
public interface ManagementAgent extends Service {

    /**
     * Registers object with management infrastructure with a specific name. Object must be annotated or 
     * implement standard MBean interface.
     *
     * @param obj  the object to register
     * @param name the name
     * @throws JMException is thrown if the registration failed
     */
    void register(Object obj, ObjectName name) throws JMException;
    
    /**
     * Registers object with management infrastructure with a specific name. Object must be annotated or 
     * implement standard MBean interface.
     *
     * @param obj  the object to register
     * @param name the name
     * @param forceRegistration if set to <tt>true</tt>, then object will be registered despite
     * existing object is already registered with the name.
     * @throws JMException is thrown if the registration failed
     */
    void register(Object obj, ObjectName name, boolean forceRegistration) throws JMException;
    
    /**
     * Unregisters object based upon registered name
     *
     * @param name the name
     * @throws JMException is thrown if the unregistration failed
     */
    void unregister(ObjectName name) throws JMException;

    /**
     * Is the given object registered
     *
     * @param name the name
     * @return <tt>true</tt> if registered
     */
    boolean isRegistered(ObjectName name);

    /**
     * Get the MBeanServer which hosts managed objects.
     * <p/>
     * <b>Notice:</b> If the JMXEnabled configuration is not set to <tt>true</tt>,
     * this method will return <tt>null</tt>.
     * 
     * @return the MBeanServer
     */
    MBeanServer getMBeanServer();

    /**
     * Sets a custom mbean server to use
     *
     * @param mbeanServer the custom mbean server
     */
    void setMBeanServer(MBeanServer mbeanServer);

    /**
     * Get domain name for Camel MBeans.
     * <p/>
     * <b>Notice:</b> That this can be different that the default domain name of the MBean Server.
     * 
     * @return domain name
     */
    String getMBeanObjectDomainName();

    /**
     * Sets the port used by {@link java.rmi.registry.LocateRegistry}.
     *
     * @param port the port
     */
    void setRegistryPort(Integer port);

    /**
     * Gets the port used by {@link java.rmi.registry.LocateRegistry}.
     *
     * @return the port
     */
    Integer getRegistryPort();

    /**
     * Sets the port clients must use to connect
     *
     * @param port the port
     */
    void setConnectorPort(Integer port);

    /**
     * Gets the port clients must use to connect
     *
     * @return the port
     */
    Integer getConnectorPort();

    /**
     * Sets the default domain on the MBean server
     *
     * @param domain the domain
     */
    void setMBeanServerDefaultDomain(String domain);

    /**
     * Gets the default domain on the MBean server
     *
     * @return the domain
     */
    String getMBeanServerDefaultDomain();

    /**
     * Sets the object domain name
     *
     * @param domainName the object domain name
     */
    void setMBeanObjectDomainName(String domainName);

    /**
     * Sets the service url
     *
     * @param url the service url
     */
    void setServiceUrlPath(String url);

    /**
     * Gets the service url
     *
     * @return the url
     */
    String getServiceUrlPath();

    /**
     * Whether connector should be created, allowing clients to connect remotely
     *
     * @param createConnector <tt>true</tt> to create connector
     */
    void setCreateConnector(Boolean createConnector);

    /**
     * Whether connector is created, allowing clients to connect remotely
     *
     * @return <tt>true</tt> if connector is created
     */
    Boolean getCreateConnector();

    /**
     * Whether to use the platform MBean Server.
     *
     * @param usePlatformMBeanServer <tt>true</tt> to use platform MBean server
     */
    void setUsePlatformMBeanServer(Boolean usePlatformMBeanServer);

    /**
     * Whether to use the platform MBean Server.
     *
     * @return <tt>true</tt> if platform MBean server is to be used
     */
    Boolean getUsePlatformMBeanServer();

    /**
     * Whether to only register processors which has a custom id assigned.
     * <p/>
     * This allows you to filter unwanted processors.
     *
     * @return <tt>true</tt> if only processors with custom id is registered
     */
    Boolean getOnlyRegisterProcessorWithCustomId();

    /**
     * Whether to only register processors which has a custom id assigned.
     * <p/>
     * This allows you to filter unwanted processors.
     *
     * @param onlyRegisterProcessorWithCustomId <tt>true</tt> to only register if custom id has been assigned
     */
    void setOnlyRegisterProcessorWithCustomId(Boolean onlyRegisterProcessorWithCustomId);

    /**
     * Whether to always register mbeans.
     * <p/>
     * This option is default <tt>false</tt>.
     * <p/>
     * <b>Important:</b> If this option is enabled then any service is registered as mbean. When using
     * dynamic EIP patterns using unique endpoint urls, you may create excessive mbeans in the registry.
     * This could lead to degraded performance as memory consumption will rise due the rising number
     * of mbeans.
     *
     * @return <tt>true</tt> if always registering
     */
    Boolean getRegisterAlways();

    /**
     * Whether to always register mbeans.
     * <p/>
     * This option is default <tt>false</tt>.
     * <p/>
     * <b>Important:</b> If this option is enabled then any service is registered as mbean. When using
     * dynamic EIP patterns using unique endpoint urls, you may create excessive mbeans in the registry.
     * This could lead to degraded performance as memory consumption will rise due the rising number
     * of mbeans.
     *
     * @param registerAlways <tt>true</tt> to always register
     */
    void setRegisterAlways(Boolean registerAlways);

    /**
     * Whether to register mbeans when starting a new route
     * <p/>
     * This option is default <tt>true</tt>.
     *
     * @return <tt>true</tt> to register when starting a new route
     */
    Boolean getRegisterNewRoutes();

    /**
     * Whether to register mbeans when starting a new route
     * <p/>
     * This option is default <tt>true</tt>.
     *
     * @param registerNewRoutes <tt>true</tt> to register when starting a new route
     */
    void setRegisterNewRoutes(Boolean registerNewRoutes);

}
