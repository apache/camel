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
package org.apache.camel.spring.rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

public class RmiRegistryFactoryBean implements FactoryBean<Registry>, InitializingBean, DisposableBean {

    private final Log logger = LogFactory.getLog(getClass());

    private String host;

    private int port = Registry.REGISTRY_PORT;

    private RMIClientSocketFactory clientSocketFactory;

    private RMIServerSocketFactory serverSocketFactory;

    private Registry registry;

    private boolean alwaysCreate;

    private boolean created;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public RMIClientSocketFactory getClientSocketFactory() {
        return clientSocketFactory;
    }

    public void setClientSocketFactory(RMIClientSocketFactory clientSocketFactory) {
        this.clientSocketFactory = clientSocketFactory;
    }

    public RMIServerSocketFactory getServerSocketFactory() {
        return serverSocketFactory;
    }

    public void setServerSocketFactory(RMIServerSocketFactory serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public boolean isAlwaysCreate() {
        return alwaysCreate;
    }

    public void setAlwaysCreate(boolean alwaysCreate) {
        this.alwaysCreate = alwaysCreate;
    }

    public void afterPropertiesSet() throws Exception {
        // Check socket factories for registry.
        if (this.clientSocketFactory instanceof RMIServerSocketFactory) {
            this.serverSocketFactory = (RMIServerSocketFactory) this.clientSocketFactory;
        }
        if (this.clientSocketFactory != null && this.serverSocketFactory == null ||
                this.clientSocketFactory == null && this.serverSocketFactory != null) {
            throw new IllegalArgumentException(
                    "Both RMIClientSocketFactory and RMIServerSocketFactory or none required");
        }

        // Fetch RMI registry to expose.
        this.registry = getRegistry(this.host, this.port, this.clientSocketFactory, this.serverSocketFactory);
    }

    /**
     * Locate or create the RMI registry.
     *
     * @param  registryHost             the registry host to use (if this is specified, no implicit creation of a RMI
     *                                  registry will happen)
     * @param  registryPort             the registry port to use
     * @param  clientSocketFactory      the RMI client socket factory for the registry (if any)
     * @param  serverSocketFactory      the RMI server socket factory for the registry (if any)
     * @return                          the RMI registry
     * @throws java.rmi.RemoteException if the registry couldn't be located or created
     */
    protected Registry getRegistry(
            String registryHost, int registryPort,
            RMIClientSocketFactory clientSocketFactory, RMIServerSocketFactory serverSocketFactory)
            throws RemoteException {

        if (registryHost != null) {
            // Host explictly specified: only lookup possible.
            if (logger.isInfoEnabled()) {
                logger.info("Looking for RMI registry at port '" + registryPort + "' of host [" + registryHost + "]");
            }
            Registry reg = LocateRegistry.getRegistry(registryHost, registryPort, clientSocketFactory);
            testRegistry(reg);
            return reg;
        } else {
            return getRegistry(registryPort, clientSocketFactory, serverSocketFactory);
        }
    }

    /**
     * Locate or create the RMI registry.
     *
     * @param  registryPort        the registry port to use
     * @param  clientSocketFactory the RMI client socket factory for the registry (if any)
     * @param  serverSocketFactory the RMI server socket factory for the registry (if any)
     * @return                     the RMI registry
     * @throws RemoteException     if the registry couldn't be located or created
     */
    protected Registry getRegistry(
            int registryPort, RMIClientSocketFactory clientSocketFactory, RMIServerSocketFactory serverSocketFactory)
            throws RemoteException {

        if (clientSocketFactory != null) {
            if (this.alwaysCreate) {
                logger.info("Creating new RMI registry");
                this.created = true;
                return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
            }
            if (logger.isInfoEnabled()) {
                logger.info("Looking for RMI registry at port '" + registryPort + "', using custom socket factory");
            }
            synchronized (LocateRegistry.class) {
                try {
                    // Retrieve existing registry.
                    Registry reg = LocateRegistry.getRegistry(null, registryPort, clientSocketFactory);
                    testRegistry(reg);
                    return reg;
                } catch (RemoteException ex) {
                    logger.debug("RMI registry access threw exception", ex);
                    logger.info("Could not detect RMI registry - creating new one");
                    // Assume no registry found -> create new one.
                    this.created = true;
                    return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
                }
            }
        } else {
            return getRegistry(registryPort);
        }
    }

    /**
     * Locate or create the RMI registry.
     *
     * @param  registryPort    the registry port to use
     * @return                 the RMI registry
     * @throws RemoteException if the registry couldn't be located or created
     */
    protected Registry getRegistry(int registryPort) throws RemoteException {
        if (this.alwaysCreate) {
            logger.info("Creating new RMI registry");
            this.created = true;
            return LocateRegistry.createRegistry(registryPort);
        }
        if (logger.isInfoEnabled()) {
            logger.info("Looking for RMI registry at port '" + registryPort + "'");
        }
        synchronized (LocateRegistry.class) {
            try {
                // Retrieve existing registry.
                Registry reg = LocateRegistry.getRegistry(registryPort);
                testRegistry(reg);
                return reg;
            } catch (RemoteException ex) {
                logger.debug("RMI registry access threw exception", ex);
                logger.info("Could not detect RMI registry - creating new one");
                // Assume no registry found -> create new one.
                this.created = true;
                return LocateRegistry.createRegistry(registryPort);
            }
        }
    }

    /**
     * Test the given RMI registry, calling some operation on it to check whether it is still active.
     * <p>
     * Default implementation calls <code>Registry.list()</code>.
     *
     * @param  registry        the RMI registry to test
     * @throws RemoteException if thrown by registry methods
     * @see                    java.rmi.registry.Registry#list()
     */
    protected void testRegistry(Registry registry) throws RemoteException {
        registry.list();
    }

    public Registry getObject() throws Exception {
        return this.registry;
    }

    public Class<? extends Registry> getObjectType() {
        return this.registry != null ? this.registry.getClass() : Registry.class;
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Unexport the RMI registry on bean factory shutdown, provided that this bean actually created a registry.
     */
    public void destroy() throws RemoteException {
        if (this.created) {
            logger.info("Unexporting RMI registry");
            UnicastRemoteObject.unexportObject(this.registry, true);
        }
    }

}
