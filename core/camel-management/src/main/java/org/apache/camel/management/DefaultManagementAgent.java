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
package org.apache.camel.management;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerInvocationHandler;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ManagementMBeansLevel;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.support.management.DefaultManagementMBeanAssembler;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the Camel JMX service agent
 */
public class DefaultManagementAgent extends ServiceSupport implements ManagementAgent, CamelContextAware {

    public static final String DEFAULT_DOMAIN = "org.apache.camel";
    public static final String DEFAULT_HOST = "localhost";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagementAgent.class);

    private CamelContext camelContext;
    private MBeanServer server;
    private ManagementMBeanAssembler assembler;

    // need a name -> actual name mapping as some servers changes the names (such as WebSphere)
    private final ConcurrentMap<ObjectName, ObjectName> mbeansRegistered = new ConcurrentHashMap<>();

    private String mBeanServerDefaultDomain = DEFAULT_DOMAIN;
    private String mBeanObjectDomainName = DEFAULT_DOMAIN;
    private Boolean usePlatformMBeanServer = true;
    private Boolean onlyRegisterProcessorWithCustomId = false;
    private Boolean loadStatisticsEnabled = false;
    private Boolean endpointRuntimeStatisticsEnabled;
    private Boolean registerAlways = false;
    private Boolean registerNewRoutes = true;
    private Boolean registerRoutesCreateByKamelet = false;
    private Boolean registerRoutesCreateByTemplate = true;
    private Boolean mask = true;
    private Boolean includeHostName = false;
    private Boolean useHostIPAddress = false;
    private Boolean updateRouteEnabled = false;
    private String managementNamePattern = "#name#";
    private ManagementStatisticsLevel statisticsLevel = ManagementStatisticsLevel.Default;
    private ManagementMBeansLevel mBeansLevel = ManagementMBeansLevel.Default;

    public DefaultManagementAgent() {
    }

    public DefaultManagementAgent(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    protected void finalizeSettings() throws Exception {
        // JVM system properties take precedence over any configuration
        Map<String, Object> values = new LinkedHashMap<>();

        if (System.getProperty(JmxSystemPropertyKeys.DOMAIN) != null) {
            mBeanServerDefaultDomain = System.getProperty(JmxSystemPropertyKeys.DOMAIN);
            values.put(JmxSystemPropertyKeys.DOMAIN, mBeanServerDefaultDomain);
        }
        if (System.getProperty(JmxSystemPropertyKeys.MBEAN_DOMAIN) != null) {
            mBeanObjectDomainName = System.getProperty(JmxSystemPropertyKeys.MBEAN_DOMAIN);
            values.put(JmxSystemPropertyKeys.MBEAN_DOMAIN, mBeanObjectDomainName);
        }
        if (System.getProperty(JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID) != null) {
            onlyRegisterProcessorWithCustomId
                    = Boolean.getBoolean(JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID);
            values.put(JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID, onlyRegisterProcessorWithCustomId);
        }
        if (System.getProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS) != null) {
            usePlatformMBeanServer = Boolean.getBoolean(JmxSystemPropertyKeys.USE_PLATFORM_MBS);
            values.put(JmxSystemPropertyKeys.USE_PLATFORM_MBS, usePlatformMBeanServer);
        }
        if (System.getProperty(JmxSystemPropertyKeys.REGISTER_ALWAYS) != null) {
            registerAlways = Boolean.getBoolean(JmxSystemPropertyKeys.REGISTER_ALWAYS);
            values.put(JmxSystemPropertyKeys.REGISTER_ALWAYS, registerAlways);
        }
        if (System.getProperty(JmxSystemPropertyKeys.REGISTER_NEW_ROUTES) != null) {
            registerNewRoutes = Boolean.getBoolean(JmxSystemPropertyKeys.REGISTER_NEW_ROUTES);
            values.put(JmxSystemPropertyKeys.REGISTER_NEW_ROUTES, registerNewRoutes);
        }
        if (System.getProperty(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_TEMPLATE) != null) {
            registerRoutesCreateByTemplate = Boolean.getBoolean(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_TEMPLATE);
            values.put(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_TEMPLATE, registerRoutesCreateByTemplate);
        }
        if (System.getProperty(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_KAMELET) != null) {
            registerRoutesCreateByKamelet = Boolean.getBoolean(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_KAMELET);
            values.put(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_KAMELET, registerRoutesCreateByKamelet);
        }
        if (System.getProperty(JmxSystemPropertyKeys.MASK) != null) {
            mask = Boolean.getBoolean(JmxSystemPropertyKeys.MASK);
            values.put(JmxSystemPropertyKeys.MASK, mask);
        }
        if (System.getProperty(JmxSystemPropertyKeys.INCLUDE_HOST_NAME) != null) {
            includeHostName = Boolean.getBoolean(JmxSystemPropertyKeys.INCLUDE_HOST_NAME);
            values.put(JmxSystemPropertyKeys.INCLUDE_HOST_NAME, includeHostName);
        }
        if (System.getProperty(JmxSystemPropertyKeys.LOAD_STATISTICS_ENABLED) != null) {
            loadStatisticsEnabled = Boolean.getBoolean(JmxSystemPropertyKeys.LOAD_STATISTICS_ENABLED);
            values.put(JmxSystemPropertyKeys.LOAD_STATISTICS_ENABLED, loadStatisticsEnabled);
        }
        if (System.getProperty(JmxSystemPropertyKeys.ENDPOINT_RUNTIME_STATISTICS_ENABLED) != null) {
            endpointRuntimeStatisticsEnabled = Boolean.getBoolean(JmxSystemPropertyKeys.ENDPOINT_RUNTIME_STATISTICS_ENABLED);
            values.put(JmxSystemPropertyKeys.ENDPOINT_RUNTIME_STATISTICS_ENABLED, endpointRuntimeStatisticsEnabled);
        }
        if (System.getProperty(JmxSystemPropertyKeys.STATISTICS_LEVEL) != null) {
            statisticsLevel = camelContext.getTypeConverter().mandatoryConvertTo(ManagementStatisticsLevel.class,
                    System.getProperty(JmxSystemPropertyKeys.STATISTICS_LEVEL));
            values.put(JmxSystemPropertyKeys.STATISTICS_LEVEL, statisticsLevel);
        }
        if (System.getProperty(JmxSystemPropertyKeys.MANAGEMENT_NAME_PATTERN) != null) {
            managementNamePattern = System.getProperty(JmxSystemPropertyKeys.MANAGEMENT_NAME_PATTERN);
            values.put(JmxSystemPropertyKeys.MANAGEMENT_NAME_PATTERN, managementNamePattern);
        }
        if (System.getProperty(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS) != null) {
            useHostIPAddress = Boolean.getBoolean(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS);
            values.put(JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS, useHostIPAddress);
        }
        if (System.getProperty(JmxSystemPropertyKeys.UPDATE_ROUTE_ENABLED) != null) {
            updateRouteEnabled = Boolean.getBoolean(JmxSystemPropertyKeys.UPDATE_ROUTE_ENABLED);
            values.put(JmxSystemPropertyKeys.UPDATE_ROUTE_ENABLED, updateRouteEnabled);
        }

        if (!values.isEmpty()) {
            LOG.info("ManagementAgent detected JVM system properties: {}", values);
        }
    }

    @Override
    public void setMBeanServerDefaultDomain(String domain) {
        mBeanServerDefaultDomain = domain;
    }

    @Override
    public String getMBeanServerDefaultDomain() {
        return mBeanServerDefaultDomain;
    }

    @Override
    public void setMBeanObjectDomainName(String domainName) {
        mBeanObjectDomainName = domainName;
    }

    @Override
    public String getMBeanObjectDomainName() {
        return mBeanObjectDomainName;
    }

    @Override
    public void setUsePlatformMBeanServer(Boolean flag) {
        usePlatformMBeanServer = flag;
    }

    @Override
    public Boolean getUsePlatformMBeanServer() {
        return usePlatformMBeanServer;
    }

    @Override
    public Boolean getOnlyRegisterProcessorWithCustomId() {
        return onlyRegisterProcessorWithCustomId;
    }

    @Override
    public void setOnlyRegisterProcessorWithCustomId(Boolean onlyRegisterProcessorWithCustomId) {
        this.onlyRegisterProcessorWithCustomId = onlyRegisterProcessorWithCustomId;
    }

    @Override
    public void setMBeanServer(MBeanServer mbeanServer) {
        server = mbeanServer;
    }

    @Override
    public MBeanServer getMBeanServer() {
        return server;
    }

    @Override
    public Boolean getRegisterAlways() {
        return registerAlways != null && registerAlways;
    }

    @Override
    public void setRegisterAlways(Boolean registerAlways) {
        this.registerAlways = registerAlways;
    }

    @Override
    public Boolean getRegisterNewRoutes() {
        return registerNewRoutes != null && registerNewRoutes;
    }

    @Override
    public void setRegisterNewRoutes(Boolean registerNewRoutes) {
        this.registerNewRoutes = registerNewRoutes;
    }

    public Boolean getRegisterRoutesCreateByKamelet() {
        return registerRoutesCreateByKamelet != null && registerRoutesCreateByKamelet;
    }

    public void setRegisterRoutesCreateByKamelet(Boolean registerRoutesCreateByKamelet) {
        this.registerRoutesCreateByKamelet = registerRoutesCreateByKamelet;
    }

    public Boolean getRegisterRoutesCreateByTemplate() {
        return registerRoutesCreateByTemplate != null && registerRoutesCreateByTemplate;
    }

    public void setRegisterRoutesCreateByTemplate(Boolean registerRoutesCreateByTemplate) {
        this.registerRoutesCreateByTemplate = registerRoutesCreateByTemplate;
    }

    @Override
    public Boolean getMask() {
        return mask != null && mask;
    }

    @Override
    public void setMask(Boolean mask) {
        this.mask = mask;
    }

    @Override
    public Boolean getIncludeHostName() {
        return includeHostName != null && includeHostName;
    }

    @Override
    public void setIncludeHostName(Boolean includeHostName) {
        this.includeHostName = includeHostName;
    }

    @Override
    public Boolean getUseHostIPAddress() {
        return useHostIPAddress != null && useHostIPAddress;
    }

    @Override
    public void setUseHostIPAddress(Boolean useHostIPAddress) {
        this.useHostIPAddress = useHostIPAddress;
    }

    @Override
    public String getManagementNamePattern() {
        return managementNamePattern;
    }

    @Override
    public void setManagementNamePattern(String managementNamePattern) {
        this.managementNamePattern = managementNamePattern;
    }

    @Override
    public Boolean getLoadStatisticsEnabled() {
        return loadStatisticsEnabled;
    }

    @Override
    public void setLoadStatisticsEnabled(Boolean loadStatisticsEnabled) {
        this.loadStatisticsEnabled = loadStatisticsEnabled;
    }

    @Override
    public Boolean getEndpointRuntimeStatisticsEnabled() {
        return endpointRuntimeStatisticsEnabled;
    }

    @Override
    public void setEndpointRuntimeStatisticsEnabled(Boolean endpointRuntimeStatisticsEnabled) {
        this.endpointRuntimeStatisticsEnabled = endpointRuntimeStatisticsEnabled;
    }

    @Override
    public ManagementStatisticsLevel getStatisticsLevel() {
        return statisticsLevel;
    }

    @Override
    public void setStatisticsLevel(ManagementStatisticsLevel statisticsLevel) {
        this.statisticsLevel = statisticsLevel;
    }

    @Override
    public ManagementMBeansLevel getMBeansLevel() {
        return mBeansLevel;
    }

    @Override
    public void setMBeansLevel(ManagementMBeansLevel mBeansLevel) {
        this.mBeansLevel = mBeansLevel;
    }

    @Override
    public Boolean getUpdateRouteEnabled() {
        return updateRouteEnabled != null && updateRouteEnabled;
    }

    @Override
    public void setUpdateRouteEnabled(Boolean updateRouteEnabled) {
        this.updateRouteEnabled = updateRouteEnabled;
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
    public void register(Object obj, ObjectName name) throws JMException {
        register(obj, name, false);
    }

    @Override
    public void register(Object obj, ObjectName name, boolean forceRegistration) throws JMException {
        try {
            registerMBeanWithServer(obj, name, forceRegistration);
        } catch (NotCompliantMBeanException e) {
            // If this is not a "normal" MBean, then try to deploy it using JMX annotations
            ObjectHelper.notNull(assembler, "ManagementMBeanAssembler", camelContext);
            Object mbean = assembler.assemble(server, obj, name);
            if (mbean != null) {
                // and register the mbean
                registerMBeanWithServer(mbean, name, forceRegistration);
            }
        }
    }

    @Override
    public void unregister(ObjectName name) throws JMException {
        if (isRegistered(name)) {
            ObjectName on = mbeansRegistered.remove(name);
            server.unregisterMBean(on);
            LOG.debug("Unregistered MBean with ObjectName: {}", name);
        } else {
            mbeansRegistered.remove(name);
        }
    }

    @Override
    public boolean isRegistered(ObjectName name) {
        if (server == null) {
            return false;
        }
        ObjectName on = mbeansRegistered.get(name);
        return on != null && server.isRegistered(on)
                || server.isRegistered(name);
    }

    @Override
    public <T> T newProxyClient(ObjectName name, Class<T> mbean) {
        if (isRegistered(name)) {
            ObjectName on = mbeansRegistered.get(name);
            return MBeanServerInvocationHandler.newProxyInstance(server, on != null ? on : name, mbean, false);
        } else {
            return null;
        }
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        finalizeSettings();

        assembler = camelContext.getCamelContextExtension().getManagementMBeanAssembler();
        if (assembler == null) {
            assembler = new DefaultManagementMBeanAssembler(camelContext);
        }
        ServiceHelper.initService(assembler);
    }

    @Override
    protected void doStart() throws Exception {
        // create mbean server if is has not be injected.
        if (server == null) {
            createMBeanServer();
        }

        // ensure assembler is started
        ServiceHelper.startService(assembler);

        LOG.debug("Starting JMX agent on server: {}", getMBeanServer());
    }

    @Override
    protected void doStop() throws Exception {
        if (mbeansRegistered.isEmpty()) {
            return;
        }

        // Using the array to hold the busMBeans to avoid the CurrentModificationException
        ObjectName[] mBeans = mbeansRegistered.keySet().toArray(new ObjectName[0]);
        int caught = 0;
        for (ObjectName name : mBeans) {
            try {
                unregister(name);
            } catch (Exception e) {
                LOG.info("Exception unregistering MBean with name {}", name, e);
                caught++;
            }
        }
        if (caught > 0) {
            LOG.warn("{} exceptions caught while unregistering MBeans during stop operation. See INFO log for details.",
                    caught);
        }

        ServiceHelper.stopService(assembler);
    }

    private void registerMBeanWithServer(Object obj, ObjectName name, boolean forceRegistration)
            throws JMException {

        // have we already registered the bean, there can be shared instances in the camel routes
        boolean exists = isRegistered(name);
        if (exists) {
            if (forceRegistration) {
                LOG.info("ForceRegistration enabled, unregistering existing MBean with ObjectName: {}", name);
                server.unregisterMBean(name);
            } else {
                // okay ignore we do not want to force it and it could be a shared instance
                LOG.debug("MBean already registered with ObjectName: {}", name);
            }
        }

        // register bean if by force or not exists
        ObjectInstance instance = null;
        if (forceRegistration || !exists) {
            LOG.trace("Registering MBean with ObjectName: {}", name);
            instance = server.registerMBean(obj, name);
        }

        // need to use the name returned from the server as some JEE servers may modify the name
        if (instance != null) {
            ObjectName registeredName = instance.getObjectName();
            LOG.debug("Registered MBean with ObjectName: {}", registeredName);
            mbeansRegistered.put(name, registeredName);
        }
    }

    protected void createMBeanServer() {
        server = findOrCreateMBeanServer();
    }

    protected MBeanServer findOrCreateMBeanServer() {

        // return platform mbean server if the option is specified.
        if (usePlatformMBeanServer) {
            return ManagementFactory.getPlatformMBeanServer();
        }

        // look for the first mbean server that has match default domain name
        List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);

        for (MBeanServer server : servers) {
            LOG.debug("Found MBeanServer with default domain {}", server.getDefaultDomain());

            if (mBeanServerDefaultDomain.equals(server.getDefaultDomain())) {
                return server;
            }
        }

        // create a mbean server with the given default domain name
        return MBeanServerFactory.createMBeanServer(mBeanServerDefaultDomain);
    }

}
