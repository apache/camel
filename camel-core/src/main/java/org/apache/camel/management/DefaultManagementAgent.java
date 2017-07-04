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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
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
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.InetAddressUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the Camel JMX service agent
 */
public class DefaultManagementAgent extends ServiceSupport implements ManagementAgent, CamelContextAware {

    public static final String DEFAULT_DOMAIN = "org.apache.camel";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_REGISTRY_PORT = 1099;
    public static final int DEFAULT_CONNECTION_PORT = -1;
    public static final String DEFAULT_SERVICE_URL_PATH = "/jmxrmi/camel";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagementAgent.class);

    private CamelContext camelContext;
    private MBeanServer server;
    private ManagementMBeanAssembler assembler;

    // need a name -> actual name mapping as some servers changes the names (such as WebSphere)
    private final ConcurrentMap<ObjectName, ObjectName> mbeansRegistered = new ConcurrentHashMap<ObjectName, ObjectName>();
    private JMXConnectorServer cs;
    private Registry registry;

    private Integer registryPort = DEFAULT_REGISTRY_PORT;
    private Integer connectorPort = DEFAULT_CONNECTION_PORT;
    private String mBeanServerDefaultDomain = DEFAULT_DOMAIN;
    private String mBeanObjectDomainName = DEFAULT_DOMAIN;
    private String serviceUrlPath = DEFAULT_SERVICE_URL_PATH;
    private Boolean usePlatformMBeanServer = true;
    private Boolean createConnector = false;
    private Boolean onlyRegisterProcessorWithCustomId = false;
    private Boolean loadStatisticsEnabled = false;
    private Boolean endpointRuntimeStatisticsEnabled;
    private Boolean registerAlways = false;
    private Boolean registerNewRoutes = true;
    private Boolean mask = true;
    private Boolean includeHostName = false;
    private Boolean useHostIPAddress = false;
    private String managementNamePattern = "#name#";
    private ManagementStatisticsLevel statisticsLevel = ManagementStatisticsLevel.Default;

    public DefaultManagementAgent() {
    }

    public DefaultManagementAgent(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    protected void finalizeSettings() throws Exception {
        // JVM system properties take precedence over any configuration
        Map<String, Object> values = new LinkedHashMap<String, Object>();

        if (System.getProperty(JmxSystemPropertyKeys.REGISTRY_PORT) != null) {
            registryPort = Integer.getInteger(JmxSystemPropertyKeys.REGISTRY_PORT);
            values.put(JmxSystemPropertyKeys.REGISTRY_PORT, registryPort);
        }
        if (System.getProperty(JmxSystemPropertyKeys.CONNECTOR_PORT) != null) {
            connectorPort = Integer.getInteger(JmxSystemPropertyKeys.CONNECTOR_PORT);
            values.put(JmxSystemPropertyKeys.CONNECTOR_PORT, connectorPort);
        }
        if (System.getProperty(JmxSystemPropertyKeys.DOMAIN) != null) {
            mBeanServerDefaultDomain = System.getProperty(JmxSystemPropertyKeys.DOMAIN);
            values.put(JmxSystemPropertyKeys.DOMAIN, mBeanServerDefaultDomain);
        }
        if (System.getProperty(JmxSystemPropertyKeys.MBEAN_DOMAIN) != null) {
            mBeanObjectDomainName = System.getProperty(JmxSystemPropertyKeys.MBEAN_DOMAIN);
            values.put(JmxSystemPropertyKeys.MBEAN_DOMAIN, mBeanObjectDomainName);
        }
        if (System.getProperty(JmxSystemPropertyKeys.SERVICE_URL_PATH) != null) {
            serviceUrlPath = System.getProperty(JmxSystemPropertyKeys.SERVICE_URL_PATH);
            values.put(JmxSystemPropertyKeys.SERVICE_URL_PATH, serviceUrlPath);
        }
        if (System.getProperty(JmxSystemPropertyKeys.CREATE_CONNECTOR) != null) {
            createConnector = Boolean.getBoolean(JmxSystemPropertyKeys.CREATE_CONNECTOR);
            values.put(JmxSystemPropertyKeys.CREATE_CONNECTOR, createConnector);
        }
        if (System.getProperty(JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID) != null) {
            onlyRegisterProcessorWithCustomId = Boolean.getBoolean(JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID);
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
        if (System.getProperty(JmxSystemPropertyKeys.MASK) != null) {
            mask = Boolean.getBoolean(JmxSystemPropertyKeys.MASK);
            values.put(JmxSystemPropertyKeys.MASK, mask);
        }
        if (System.getProperty(JmxSystemPropertyKeys.INCLUDE_HOST_NAME) != null) {
            includeHostName = Boolean.getBoolean(JmxSystemPropertyKeys.INCLUDE_HOST_NAME);
            values.put(JmxSystemPropertyKeys.INCLUDE_HOST_NAME, includeHostName);
        }
        if (System.getProperty(JmxSystemPropertyKeys.CREATE_CONNECTOR) != null) {
            createConnector = Boolean.getBoolean(JmxSystemPropertyKeys.CREATE_CONNECTOR);
            values.put(JmxSystemPropertyKeys.CREATE_CONNECTOR, createConnector);
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
            statisticsLevel = camelContext.getTypeConverter().mandatoryConvertTo(ManagementStatisticsLevel.class, System.getProperty(JmxSystemPropertyKeys.STATISTICS_LEVEL));
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

        if (!values.isEmpty()) {
            LOG.info("ManagementAgent detected JVM system properties: {}", values);
        }
    }

    public void setRegistryPort(Integer port) {
        registryPort = port;
    }

    public Integer getRegistryPort() {
        return registryPort;
    }

    public void setConnectorPort(Integer port) {
        connectorPort = port;
    }

    public Integer getConnectorPort() {
        return connectorPort;
    }

    public void setMBeanServerDefaultDomain(String domain) {
        mBeanServerDefaultDomain = domain;
    }

    public String getMBeanServerDefaultDomain() {
        return mBeanServerDefaultDomain;
    }

    public void setMBeanObjectDomainName(String domainName) {
        mBeanObjectDomainName = domainName;
    }

    public String getMBeanObjectDomainName() {
        return mBeanObjectDomainName;
    }

    public void setServiceUrlPath(String url) {
        serviceUrlPath = url;
    }

    public String getServiceUrlPath() {
        return serviceUrlPath;
    }

    public void setCreateConnector(Boolean flag) {
        createConnector = flag;
    }

    public Boolean getCreateConnector() {
        return createConnector;
    }

    public void setUsePlatformMBeanServer(Boolean flag) {
        usePlatformMBeanServer = flag;
    }

    public Boolean getUsePlatformMBeanServer() {
        return usePlatformMBeanServer;
    }

    public Boolean getOnlyRegisterProcessorWithCustomId() {
        return onlyRegisterProcessorWithCustomId;
    }

    public void setOnlyRegisterProcessorWithCustomId(Boolean onlyRegisterProcessorWithCustomId) {
        this.onlyRegisterProcessorWithCustomId = onlyRegisterProcessorWithCustomId;
    }

    public void setMBeanServer(MBeanServer mbeanServer) {
        server = mbeanServer;
    }

    public MBeanServer getMBeanServer() {
        return server;
    }

    public Boolean getRegisterAlways() {
        return registerAlways != null && registerAlways;
    }

    public void setRegisterAlways(Boolean registerAlways) {
        this.registerAlways = registerAlways;
    }

    public Boolean getRegisterNewRoutes() {
        return registerNewRoutes != null && registerNewRoutes;
    }

    public void setRegisterNewRoutes(Boolean registerNewRoutes) {
        this.registerNewRoutes = registerNewRoutes;
    }

    public Boolean getMask() {
        return mask != null && mask;
    }

    public void setMask(Boolean mask) {
        this.mask = mask;
    }

    public Boolean getIncludeHostName() {
        return includeHostName != null && includeHostName;
    }

    public void setIncludeHostName(Boolean includeHostName) {
        this.includeHostName = includeHostName;
    }

    public Boolean getUseHostIPAddress() {
        return useHostIPAddress != null && useHostIPAddress;
    }

    public void setUseHostIPAddress(Boolean useHostIPAddress) {
        this.useHostIPAddress = useHostIPAddress;
    }

    public String getManagementNamePattern() {
        return managementNamePattern;
    }

    public void setManagementNamePattern(String managementNamePattern) {
        this.managementNamePattern = managementNamePattern;
    }

    public Boolean getLoadStatisticsEnabled() {
        return loadStatisticsEnabled;
    }

    public void setLoadStatisticsEnabled(Boolean loadStatisticsEnabled) {
        this.loadStatisticsEnabled = loadStatisticsEnabled;
    }

    public Boolean getEndpointRuntimeStatisticsEnabled() {
        return endpointRuntimeStatisticsEnabled;
    }

    public void setEndpointRuntimeStatisticsEnabled(Boolean endpointRuntimeStatisticsEnabled) {
        this.endpointRuntimeStatisticsEnabled = endpointRuntimeStatisticsEnabled;
    }

    public ManagementStatisticsLevel getStatisticsLevel() {
        return statisticsLevel;
    }

    public void setStatisticsLevel(ManagementStatisticsLevel statisticsLevel) {
        this.statisticsLevel = statisticsLevel;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void register(Object obj, ObjectName name) throws JMException {
        register(obj, name, false);
    }

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

    public void unregister(ObjectName name) throws JMException {
        if (isRegistered(name)) {
            ObjectName on = mbeansRegistered.remove(name);
            server.unregisterMBean(on);
            LOG.debug("Unregistered MBean with ObjectName: {}", name);
        } else {
            mbeansRegistered.remove(name);
        }
    }

    public boolean isRegistered(ObjectName name) {
        if (server == null) {
            return false;
        }
        ObjectName on = mbeansRegistered.get(name);
        return (on != null && server.isRegistered(on))
                || server.isRegistered(name);
    }

    public <T> T newProxyClient(ObjectName name, Class<T> mbean) {
        if (isRegistered(name)) {
            ObjectName on = mbeansRegistered.get(name);
            return MBeanServerInvocationHandler.newProxyInstance(server, on != null ? on : name, mbean, false);
        } else {
            return null;
        }
    }

    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        // create mbean server if is has not be injected.
        if (server == null) {
            finalizeSettings();
            createMBeanServer();
        }

        // ensure assembler is started
        assembler = camelContext.getManagementMBeanAssembler();
        ServiceHelper.startService(assembler);

        LOG.debug("Starting JMX agent on server: {}", getMBeanServer());
    }

    protected void doStop() throws Exception {
        // close JMX Connector, if it was created
        if (cs != null) {
            try {
                cs.stop();
                LOG.debug("Stopped JMX Connector");
            } catch (IOException e) {
                LOG.debug("Error occurred during stopping JMXConnectorService: "
                        + cs + ". This exception will be ignored.");
            }
            cs = null;
        }

        // Unexport JMX RMI registry, if it was created
        if (registry != null) {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
                LOG.debug("Unexported JMX RMI Registry");
            } catch (NoSuchObjectException e) {
                LOG.debug("Error occurred while unexporting JMX RMI registry. This exception will be ignored.");
            }
        }

        if (mbeansRegistered.isEmpty()) {
            return;
        }

        // Using the array to hold the busMBeans to avoid the CurrentModificationException
        ObjectName[] mBeans = mbeansRegistered.keySet().toArray(new ObjectName[mbeansRegistered.size()]);
        int caught = 0;
        for (ObjectName name : mBeans) {
            try {
                unregister(name);
            } catch (Exception e) {
                LOG.info("Exception unregistering MBean with name " + name, e);
                caught++;
            }
        }
        if (caught > 0) {
            LOG.warn("A number of " + caught
                     + " exceptions caught while unregistering MBeans during stop operation."
                     + " See INFO log for details.");
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
        String hostName;
        boolean canAccessSystemProps = true;
        try {
            // we'll do it this way mostly to determine if we should lookup the hostName
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPropertiesAccess();
            }
        } catch (SecurityException se) {
            canAccessSystemProps = false;
        }

        if (canAccessSystemProps) {
            try {
                if (useHostIPAddress) {
                    hostName = InetAddress.getLocalHost().getHostAddress();
                } else {
                    hostName = InetAddressUtil.getLocalHostName();
                }
            } catch (UnknownHostException uhe) {
                LOG.info("Cannot determine localhost name or address. Using default: " + DEFAULT_REGISTRY_PORT, uhe);
                hostName = DEFAULT_HOST;
            }
        } else {
            hostName = DEFAULT_HOST;
        }

        server = findOrCreateMBeanServer();

        try {
            // Create the connector if we need
            if (createConnector) {
                createJmxConnector(hostName);
            }
        } catch (IOException ioe) {
            LOG.warn("Could not create and start JMX connector.", ioe);
        }
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

    protected void createJmxConnector(String host) throws IOException {
        ObjectHelper.notEmpty(serviceUrlPath, "serviceUrlPath");
        ObjectHelper.notNull(registryPort, "registryPort");

        try {
            registry = LocateRegistry.createRegistry(registryPort);
            LOG.debug("Created JMXConnector RMI registry on port {}", registryPort);
        } catch (RemoteException ex) {
            // The registry may had been created, we could get the registry instead
        }

        // must start with leading slash
        String path = serviceUrlPath.startsWith("/") ? serviceUrlPath : "/" + serviceUrlPath;
        // Create an RMI connector and start it
        final JMXServiceURL url;
        if (connectorPort > 0) {
            url = new JMXServiceURL("service:jmx:rmi://" + host + ":" + connectorPort + "/jndi/rmi://" + host
                                    + ":" + registryPort + path);
        } else {
            url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + registryPort + path);
        }

        cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);

        // use async thread for starting the JMX Connector
        // (no need to use a thread pool or enlist in JMX as this thread is terminated when the JMX connector has been started)
        String threadName = camelContext.getExecutorServiceManager().resolveThreadName("JMXConnector: " + url);
        Thread thread = getCamelContext().getExecutorServiceManager().newThread(threadName, new Runnable() {
            public void run() {
                try {
                    LOG.debug("Staring JMX Connector thread to listen at: {}", url);
                    cs.start();
                    LOG.info("JMX Connector thread started and listening at: {}", url);
                } catch (IOException ioe) {
                    LOG.warn("Could not start JMXConnector thread at: " + url + ". JMX Connector not in use.", ioe);
                }
            }
        });
        thread.start();
    }

}
