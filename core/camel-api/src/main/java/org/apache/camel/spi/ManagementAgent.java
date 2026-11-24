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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ManagementMBeansLevel;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Service;
import org.jspecify.annotations.Nullable;

/**
 * Low-level JMX bridge that registers and unregisters Camel managed objects in a {@link javax.management.MBeanServer}.
 * <p>
 * {@link ManagementStrategy} is the high-level coordinator; it delegates all MBean lifecycle operations (register,
 * unregister, lookup) to this agent. The agent wraps an {@link javax.management.MBeanServer} — by default the platform
 * MBean server — and exposes a simplified API that hides the JMX plumbing from the rest of Camel.
 * <p>
 * The agent is created by {@link ManagementStrategyFactory} and is available from a running
 * {@link org.apache.camel.CamelContext} via {@link ManagementStrategy#getManagementAgent()}. Its configuration
 * properties (domain name, statistics level, MBeans level, naming pattern, masking, etc.) map directly to the
 * {@code camel.main.jmx-*} family of properties. The {@link ManagementObjectNameStrategy} and
 * {@link ManagementObjectStrategy} work alongside the agent to determine the {@link javax.management.ObjectName} and
 * managed-object wrapper for each Camel artifact before the agent registers it.
 * <p>
 * See <a href="https://camel.apache.org/manual/jmx.html">JMX</a> in the Camel user manual.
 *
 * @see ManagementStrategy
 * @see ManagementObjectNameStrategy
 * @see ManagementObjectStrategy
 */
public interface ManagementAgent extends Service {

    /**
     * Registers object with management infrastructure with a specific name. Object must be annotated or implement
     * standard MBean interface.
     *
     * @param  obj         the object to register
     * @param  name        the name
     * @throws JMException is thrown if the registration failed
     */
    void register(Object obj, ObjectName name) throws JMException;

    /**
     * Registers object with management infrastructure with a specific name. Object must be annotated or implement
     * standard MBean interface.
     *
     * @param  obj               the object to register
     * @param  name              the name
     * @param  forceRegistration if set to <code>true</code>, then object will be registered despite existing object is
     *                           already registered with the name.
     * @throws JMException       is thrown if the registration failed
     */
    void register(Object obj, ObjectName name, boolean forceRegistration) throws JMException;

    /**
     * Unregisters object based upon registered name
     *
     * @param  name        the name
     * @throws JMException is thrown if the unregistration failed
     */
    void unregister(ObjectName name) throws JMException;

    /**
     * Is the given object registered
     *
     * @param  name the name
     * @return      <code>true</code> if registered
     */
    boolean isRegistered(ObjectName name);

    /**
     * Creates a new proxy client
     *
     * @param  name  the mbean name
     * @param  mbean the client interface, such as from the {@code org.apache.camel.api.management.mbean} package.
     * @return       the client or <code>null</code> if mbean does not exist
     */
    <T> @Nullable T newProxyClient(ObjectName name, Class<T> mbean);

    /**
     * Get the MBeanServer which hosts managed objects.
     * <p>
     * <b>Notice:</b> If the JMXEnabled configuration is not set to <code>true</code>, this method will return
     * <code>null</code>.
     *
     * @return the MBeanServer
     */
    @Nullable
    MBeanServer getMBeanServer();

    /**
     * Sets a custom mbean server to use
     *
     * @param mbeanServer the custom mbean server
     */
    void setMBeanServer(MBeanServer mbeanServer);

    /**
     * Get domain name for Camel MBeans.
     * <p>
     * <b>Notice:</b> That this can be different from the default domain name of the MBean Server.
     *
     * @return domain name
     */
    String getMBeanObjectDomainName();

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
     * Whether to use the platform MBean Server.
     *
     * @param usePlatformMBeanServer <code>true</code> to use platform MBean server
     */
    void setUsePlatformMBeanServer(Boolean usePlatformMBeanServer);

    /**
     * Whether to use the platform MBean Server.
     *
     * @return <code>true</code> if platform MBean server is to be used
     */
    Boolean getUsePlatformMBeanServer();

    /**
     * Whether to only register processors which have a custom id assigned.
     * <p>
     * This allows you to filter unwanted processors.
     *
     * @return <code>true</code> if only processors with custom id are registered
     */
    Boolean getOnlyRegisterProcessorWithCustomId();

    /**
     * Whether to only register processors which have a custom id assigned.
     * <p>
     * This allows you to filter unwanted processors.
     *
     * @param onlyRegisterProcessorWithCustomId <code>true</code> to only register if custom id has been assigned
     */
    void setOnlyRegisterProcessorWithCustomId(Boolean onlyRegisterProcessorWithCustomId);

    /**
     * Whether to always register mbeans.
     * <p>
     * This option is default <code>false</code>.
     * <p>
     * <b>Important:</b> If this option is enabled then any service is registered as mbean. When using dynamic EIP
     * patterns using unique endpoint urls, you may create excessive mbeans in the registry. This could lead to degraded
     * performance as memory consumption will rise due to the rising number of mbeans.
     *
     * @return <code>true</code> if always registering
     */
    Boolean getRegisterAlways();

    /**
     * Whether to always register mbeans.
     * <p>
     * This option is default <code>false</code>.
     * <p>
     * <b>Important:</b> If this option is enabled then any service is registered as mbean. When using dynamic EIP
     * patterns using unique endpoint urls, you may create excessive mbeans in the registry. This could lead to degraded
     * performance as memory consumption will rise due to the rising number of mbeans.
     *
     * @param registerAlways <code>true</code> to always register
     */
    void setRegisterAlways(Boolean registerAlways);

    /**
     * Whether to register mbeans when starting a new route
     * <p>
     * This option is default <code>true</code>.
     *
     * @return <code>true</code> to register when starting a new route
     */
    Boolean getRegisterNewRoutes();

    /**
     * Whether to register mbeans when starting a new route
     * <p>
     * This option is default <code>true</code>.
     *
     * @param registerNewRoutes <code>true</code> to register when starting a new route
     */
    void setRegisterNewRoutes(Boolean registerNewRoutes);

    /**
     * Whether to register mbeans for routes created by a Kamelet
     * <p>
     * This option is default <code>false</code>.
     */
    Boolean getRegisterRoutesCreateByKamelet();

    /**
     * Whether to register mbeans for routes created by a Kamelet
     * <p>
     * This option is default <code>false</code>.
     */
    void setRegisterRoutesCreateByKamelet(Boolean registerRoutesCreateByKamelet);

    /**
     * Whether to register mbeans for routes created by route templates (not Kamelets)
     * <p>
     * This option is default <code>true</code>.
     */
    Boolean getRegisterRoutesCreateByTemplate();

    /**
     * Whether to register mbeans for routes created by route templates (not Kamelets)
     * <p>
     * This option is default <code>true</code>.
     */
    void setRegisterRoutesCreateByTemplate(Boolean registerRoutesCreateByTemplate);

    /**
     * Whether to remove detected sensitive information (such as passwords) from MBean names and attributes.
     * <p>
     * This option is default <code>true</code>.
     */
    Boolean getMask();

    /**
     * Whether to remove detected sensitive information (such as passwords) from MBean names and attributes.
     * <p>
     * This option is default <code>true</code>.
     */
    void setMask(Boolean sanitize);

    /**
     * Gets whether host name is included in MBean names.
     *
     * @return <code>true</code> if included
     */
    Boolean getIncludeHostName();

    /**
     * Sets whether to include host name in the {@link ManagementObjectNameStrategy}.
     * <p>
     * By default this is turned off from Camel 2.13 onwards, but this option can be set to <code>true</code> to include
     * the hostname as Camel 2.12 or older releases do.
     *
     * @param includeHostName <code>true</code> to include host name in the MBean names.
     */
    void setIncludeHostName(Boolean includeHostName);

    /**
     * The naming pattern for creating the CamelContext management name.
     * <p>
     * The default pattern is <code>#name#</code>
     */
    String getManagementNamePattern();

    /**
     * The naming pattern for creating the CamelContext management name.
     * <p>
     * The default pattern is <code>#name#</code>
     */
    void setManagementNamePattern(String managementNamePattern);

    /**
     * Sets whether Camel load (inflight messages, not cpu) statistics is enabled (something like the unix load
     * average). The statistics requires to have camel-management on the classpath as JMX is required.
     * <p>
     * The default value is <code>false</code>
     *
     * @param flag <code>true</code> to enable load statistics
     */
    void setLoadStatisticsEnabled(Boolean flag);

    /**
     * Gets whether load (inflight messages, not cpu) statistics is enabled
     *
     * @return <code>true</code> if enabled
     */
    Boolean getLoadStatisticsEnabled();

    /**
     * Sets whether endpoint runtime statistics is enabled (gathers runtime usage of each incoming and outgoing
     * endpoints).
     * <p>
     * The default value is <code>false</code>
     *
     * @param flag <code>true</code> to enable endpoint runtime statistics
     */
    void setEndpointRuntimeStatisticsEnabled(Boolean flag);

    /**
     * Gets whether endpoint runtime statistics is enabled
     *
     * @return <code>true</code> if enabled
     */
    Boolean getEndpointRuntimeStatisticsEnabled();

    /**
     * Sets the statistics level
     * <p>
     * Default is {@link org.apache.camel.ManagementStatisticsLevel#Default}
     * <p>
     * The level can be set to <code>Extended</code> to gather additional information
     *
     * @param level the new level
     */
    void setStatisticsLevel(ManagementStatisticsLevel level);

    /**
     * § Gets the statistics level
     *
     * @return the level
     */
    ManagementStatisticsLevel getStatisticsLevel();

    /**
     * Sets the mbeans registration level
     * <p>
     * Default is {@link org.apache.camel.ManagementMBeansLevel#Default}
     *
     * @param level the new level
     */
    void setMBeansLevel(ManagementMBeansLevel level);

    /**
     * § Gets the mbeans registration level
     *
     * @return the level
     */
    ManagementMBeansLevel getMBeansLevel();

    /**
     * Gets whether host IP Address to be used instead of host name.
     *
     * @return <code>true</code> if included
     */
    Boolean getUseHostIPAddress();

    /**
     * Sets whether to use host IP Address
     *
     * @param useHostIPAddress <code>true</code> to use IP Address.
     */
    void setUseHostIPAddress(Boolean useHostIPAddress);

    /**
     * Gets whether updating routes via JMX is allowed (is default disabled).
     */
    Boolean getUpdateRouteEnabled();

    /**
     * Sets whether updating routes via JMX is allowed (is default disabled).
     */
    void setUpdateRouteEnabled(Boolean updateRouteEnabled);

}
