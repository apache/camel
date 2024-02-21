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
package org.apache.camel.component.debug;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To make it possible to do JMX debugging via JMX remote
 */
public class JmxConnectorService extends ServiceSupport implements CamelContextAware {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_REGISTRY_PORT = 1099;
    public static final int DEFAULT_CONNECTION_PORT = -1;
    public static final String DEFAULT_SERVICE_URL_PATH = "/jmxrmi/camel";

    private static final Logger LOG = LoggerFactory.getLogger(JmxConnectorService.class);

    private CamelContext camelContext;
    private MBeanServer server;
    private JMXConnectorServer cs;
    private Registry registry;

    private boolean createConnector = true;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isCreateConnector() {
        return createConnector;
    }

    public void setCreateConnector(boolean createConnector) {
        this.createConnector = createConnector;
    }

    @Override
    protected void doStart() throws Exception {
        server = ManagementFactory.getPlatformMBeanServer();

        if (createConnector) {
            createJmxConnector(DEFAULT_HOST);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // close JMX Connector, if it was created
        if (cs != null) {
            try {
                cs.stop();
                LOG.debug("Stopped JMX Connector");
            } catch (IOException e) {
                LOG.debug("Error occurred during stopping JMXConnectorService: {}. This exception will be ignored.", cs);
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
    }

    protected void createJmxConnector(String host) throws IOException {
        int registryPort = DEFAULT_REGISTRY_PORT;
        String serviceUrlPath = DEFAULT_SERVICE_URL_PATH;
        int connectorPort = DEFAULT_CONNECTION_PORT;

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
            url = new JMXServiceURL(
                    "service:jmx:rmi://" + host + ":" + connectorPort + "/jndi/rmi://" + host
                                    + ":" + registryPort + path);
        } else {
            url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + registryPort + path);
        }

        cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);

        // use async thread for starting the JMX Connector
        // (no need to use a thread pool or enlist in JMX as this thread is terminated when the JMX connector has been started)
        Thread thread = getCamelContext().getExecutorServiceManager().newThread("CamelJMXConnector", () -> {
            try {
                LOG.debug("Staring JMX Connector thread to listen at: {}", url);
                cs.start();
                LOG.info("JMX Connector thread started and listening at: {}", url);
            } catch (IOException ioe) {
                LOG.warn("Could not start JMXConnector thread at: {}. JMX Connector not in use.", url, ioe);
            }
        });
        thread.start();
    }
}
