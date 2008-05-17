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
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.InstrumentationAgent;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;

public class InstrumentationAgentImpl extends ServiceSupport implements InstrumentationAgent,
    CamelContextAware {
    public static final String SYSTEM_PROPERTY_JMX = "org.apache.camel.jmx";
    public static final String SYSTEM_PROPERTY_JMX_USE_PLATFORM_MBS = SYSTEM_PROPERTY_JMX + ".usePlatformMBeanServer";
    public static final String DEFAULT_DOMAIN = "org.apache.camel";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 1099;
    public static final String DEFAULT_CONNECTOR_PATH = "/jmxrmi";
    private static final transient Log LOG = LogFactory.getLog(InstrumentationAgentImpl.class);


    private MBeanServer server;
    private CamelContext context;
    private Set<ObjectName> mbeans = new HashSet<ObjectName>();
    private MetadataMBeanInfoAssembler assembler;
    private JMXConnectorServer cs;
    private boolean jmxEnabled;
    private String jmxDomainName;
    private int jmxConnectorPort;
    private String jmxConnectorPath;
    private boolean createConnector = true;
    private boolean usePlatformMBeanServer;

    public InstrumentationAgentImpl() {
        assembler = new MetadataMBeanInfoAssembler();
        assembler.setAttributeSource(new AnnotationJmxAttributeSource());
    }

    public CamelContext getCamelContext() {
        return context;
    }

    public void setCamelContext(CamelContext camelContext) {
        context = camelContext;
    }

    public void setCreateConnector(boolean flag) {
        createConnector = flag;
    }

    public void setUsePlatformMBeanServer(boolean flag) {
        usePlatformMBeanServer = flag;
    }

    public void setMBeanServer(MBeanServer server) {
        this.server = server;
        jmxEnabled = true;
    }

    public MBeanServer getMBeanServer() {
        if (server == null) {
            // The MBeanServer was not injected
            createMBeanServer();
        }
        return server;
    }

    public void register(Object obj, ObjectName name) throws JMException {
        register(obj, name, false);
    }

    public void register(Object obj, ObjectName name, boolean forceRegistration) throws JMException {
        try {
            registerMBeanWithServer(obj, name, forceRegistration);
        } catch (NotCompliantMBeanException e) {
            // If this is not a "normal" MBean, then try to deploy it using JMX
            // annotations
            ModelMBeanInfo mbi = null;
            mbi = assembler.getMBeanInfo(obj, name.toString());
            RequiredModelMBean mbean = (RequiredModelMBean)server.instantiate(RequiredModelMBean.class
                .getName());
            mbean.setModelMBeanInfo(mbi);
            try {
                mbean.setManagedResource(obj, "ObjectReference");
            } catch (InvalidTargetObjectTypeException itotex) {
                throw new JMException(itotex.getMessage());
            }
            registerMBeanWithServer(mbean, name, forceRegistration);
        }
    }

    public void unregister(ObjectName name) throws JMException {
        server.unregisterMBean(name);
    }

    protected void doStart() throws Exception {
        ObjectHelper.notNull(context, "camelContext");

        if (getMBeanServer() == null) {
            // No mbean server or jmx not enabled
            return;
        }

        if (jmxDomainName == null) {
            jmxDomainName = System.getProperty(SYSTEM_PROPERTY_JMX + ".domain");
            if (jmxDomainName == null || jmxDomainName.length() == 0) {
                jmxDomainName = DEFAULT_DOMAIN;
            }
        }

        LOG.debug("Starting JMX agent on server: " + getMBeanServer());

        if (context instanceof DefaultCamelContext) {
            DefaultCamelContext dc = (DefaultCamelContext)context;
            InstrumentationLifecycleStrategy ls = new InstrumentationLifecycleStrategy(
                    this, new CamelNamingStrategy(jmxDomainName));
            dc.setLifecycleStrategy(ls);
            ls.onContextCreate(context);
        }
    }

    protected void doStop() throws Exception {
        // close JMX Connector
        if (cs != null) {
            try {
                cs.stop();
            } catch (IOException e) {
                // ignore
            }
            cs = null;
        }

        // Using the array to hold the busMBeans to avoid the
        // CurrentModificationException
        Object[] mBeans = mbeans.toArray();
        int caught = 0;
        for (Object name : mBeans) {
            mbeans.remove((ObjectName)name);
            try {
                unregister((ObjectName)name);
            } catch (JMException jmex) {
                LOG.info("Exception unregistering MBean", jmex);
                caught++;
            }
        }
        if (caught > 0) {
            LOG.warn("A number of " + caught
                     + " exceptions caught while unregistering MBeans during stop operation.  "
                     + "See INFO log for details.");
        }
    }

    private void registerMBeanWithServer(Object obj, ObjectName name, boolean forceRegistration)
        throws JMException {

        ObjectInstance instance = null;
        try {
            instance = server.registerMBean(obj, name);
        } catch (InstanceAlreadyExistsException e) {
            if (forceRegistration) {
                server.unregisterMBean(name);
                instance = server.registerMBean(obj, name);
            } else {
                throw e;
            }
        }

        if (instance != null) {
            mbeans.add(name);
        }
    }

    public void enableJmx() {
        enableJmx(DEFAULT_DOMAIN, DEFAULT_CONNECTOR_PATH, DEFAULT_PORT);
    }

    public void enableJmx(String domainName, String connectorPath,  int port) {
        jmxEnabled = true;
        jmxDomainName = domainName;
        jmxConnectorPath = connectorPath;
        jmxConnectorPort = port;
    }

    protected void createMBeanServer() {
        String hostName = DEFAULT_HOST;
        boolean canAccessSystemProps = true;
        try {
            // we'll do it this way mostly to determine if we should lookup the
            // hostName
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPropertiesAccess();
            }
        } catch (SecurityException se) {
            canAccessSystemProps = false;
        }

        if (canAccessSystemProps) {
            if (!jmxEnabled) {
                jmxEnabled = null != System.getProperty(SYSTEM_PROPERTY_JMX);
                if (!jmxEnabled) {
                    // we're done here
                    return;
                }
            }

            if (jmxConnectorPort <= 0) {
                String portKey = SYSTEM_PROPERTY_JMX + ".port";
                String portValue = System.getProperty(portKey);
                if (portValue != null && portValue.length() > 0) {
                    try {
                        jmxConnectorPort = Integer.parseInt(portValue);
                    } catch (NumberFormatException nfe) {
                        LOG.info("Invalid port number specified via System property [" + portKey + "="
                                 + portValue + "].  Using default: " + DEFAULT_PORT);
                        jmxConnectorPort = DEFAULT_PORT;
                    }
                }
            }

            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException uhe) {
                LOG.info("Cannot determine host name.  Using default: " + DEFAULT_PORT, uhe);
                hostName = DEFAULT_HOST;
            }
        } else {
            jmxDomainName = jmxDomainName != null ? jmxDomainName : DEFAULT_DOMAIN;
            jmxConnectorPort = jmxConnectorPort > 0 ? jmxConnectorPort : DEFAULT_PORT;
            hostName = DEFAULT_HOST;
        }

        if (!jmxEnabled) {
            return;
        }

        // jmx is enabled but there's no MBeanServer, so create one
        if (Boolean.getBoolean(SYSTEM_PROPERTY_JMX_USE_PLATFORM_MBS) || usePlatformMBeanServer) {
            server = ManagementFactory.getPlatformMBeanServer();
        } else {
            // jmx is enabled but there's no MBeanServer, so create one
            List servers = MBeanServerFactory.findMBeanServer(jmxDomainName);
            if (servers.size() == 0) {
                server = MBeanServerFactory.createMBeanServer(jmxDomainName);
            } else {
                server = (MBeanServer)servers.get(0);
            }
        }

        try {
            // Create the connector if we need
            if (createConnector) {
                createJmxConnector(hostName);
            }
        } catch (IOException ioe) {
            LOG.warn("Could not create and start jmx connector.", ioe);
        }
    }

    protected void createJmxConnector(String host) throws IOException {
        if (jmxConnectorPort > 0) {
            try {
                LocateRegistry.createRegistry(jmxConnectorPort);
            } catch (RemoteException ex) {
                // The registry may had been created
                LocateRegistry.getRegistry(jmxConnectorPort);
            }

            if (jmxConnectorPath == null) {
                jmxConnectorPath = DEFAULT_CONNECTOR_PATH;
            }
            // Create an RMI connector and start it
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":"
                                                  + jmxConnectorPort + jmxConnectorPath);
            cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);

            // Start the connector server asynchronously (in a separate thread).
            Thread connectorThread = new Thread() {
                public void run() {
                    try {
                        cs.start();
                    } catch (IOException ioe) {
                        LOG.warn("Could not start jmx connector thread.", ioe);
                    }
                }
            };
            connectorThread.setName("JMX Connector Thread [" + url + "]");
            connectorThread.start();
            LOG.info("Jmx connector thread started on " + url);
        }
    }
}
