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
package org.apache.camel.component.websocket;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.http.ssl.SslContextFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.servlet.Servlet;

public class WebsocketComponent extends DefaultComponent {

    protected static final Logger LOG = LoggerFactory.getLogger(WebsocketComponent.class);
    protected static final HashMap<String, ConnectorRef> CONNECTORS = new HashMap<String, ConnectorRef>();

    protected SSLContextParameters sslContextParameters;
    protected MBeanContainer mbContainer;
    protected ThreadPool threadPool;
    protected ServletContextHandler context;

    protected Integer port;
    protected Integer minThreads;
    protected Integer maxThreads;

    protected boolean enableJmx;

    protected String host;
    protected String staticResources;
    protected String sslKeyPassword;
    protected String sslPassword;
    protected String sslKeystore;

    class ConnectorRef {
        Server server;
        Connector connector;
        WebsocketComponentServlet servlet;
        int refCount;

        public ConnectorRef(Server server, Connector connector, WebsocketComponentServlet servlet) {
            this.server = server;
            this.connector = connector;
            this.servlet = servlet;
            increment();
        }

        public int increment() {
            return ++refCount;
        }

        public int decrement() {
            return --refCount;
        }

        public int getRefCount() {
            return refCount;
        }
    }

    /**
     * Map for storing servlets. {@link WebsocketComponentServlet} is identified by pathSpec {@link String}.
     */
    private Map<String, WebsocketComponentServlet> servlets = new HashMap<String, WebsocketComponentServlet>();

    public WebsocketComponent() {
    }


    /**
     * Connects the URL specified on the endpoint to the specified processor.
     */
    public void connect(WebsocketProducerConsumer prodcon) throws Exception {

        Server server = null;
        String baseResource = null;
        WebsocketEndpoint endpoint = prodcon.getEndpoint();

        String connectorKey = getConnectorKey(endpoint);

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if (endpoint.getSslContextParameters() != null) {
                    connector = getSslSocketConnector(endpoint.getSslContextParameters());
                } else {
                    connector = new SelectChannelConnector();
                }

                LOG.debug("Jetty Connector added : " + connector.getName());

                if (port != null) {
                    connector.setPort(port);
                } else {
                    connector.setPort(endpoint.getPort());
                }

                if (host != null) {
                    connector.setHost(host);
                } else {
                    connector.setHost(endpoint.getHost());
                }

                connector.setHost(endpoint.getHost());

                // TODO -  Is it the right place to define static resources ?
                if (endpoint.getHome() != null) {

                    ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);

                    if (endpoint.getHome().startsWith("classpath:")) {
                        baseResource = ObjectHelper.after(endpoint.getHome(), "classpath:");
                        LOG.debug("Using base resource from classpath: {}", baseResource);
                        context.setBaseResource(new JettyClassPathResource(getCamelContext().getClassResolver(), baseResource));
                    } else {
                        LOG.debug("Using base resource: {}", baseResource);
                        context.setResourceBase(baseResource);
                    }
                    DefaultServlet defaultServlet = new DefaultServlet();
                    ServletHolder holder = new ServletHolder(defaultServlet);

                    // avoid file locking on windows
                    // http://stackoverflow.com/questions/184312/how-to-make-jetty-dynamically-load-static-pages
                    holder.setInitParameter("useFileMappedBuffer", "false");
                    context.addServlet(holder, "/");
                }

                // Create Server and add connector
                server = createServer();
                if (endpoint.isEnableJmx()) {
                    enableJmx(server);
                }

                server.addConnector(connector);

                // Create ServletContextHandler
                context = createContext(server,connector,endpoint.getHandlers());

                // Don't provide a Servlet object as Producer/Consumer will create them later on
                connectorRef = new ConnectorRef(server, connector, null);

                // must enable session before we start
                if (endpoint.isSessionSupport()) {
                    enableSessionSupport(connectorRef.server, connectorKey);
                }
                connectorRef.server.start();

                CONNECTORS.put(connectorKey, connectorRef);

                LOG.debug("Jetty Server started for host : " + connector.getHost() + ", on port : " + connector.getPort());
                server.start();

            } else {
                connectorRef.increment();
            }

            // check the session support
            if (endpoint.isSessionSupport()) {
                enableSessionSupport(connectorRef.server, connectorKey);
            }

            // TODO - As we can define WebSocket for Consumer/Producer
            // This part of the code must be adapted compare to camel-jetty where we only use
            // Jetty as a server = Consumer
            // connectorRef.servlet.connect(consumer);

        }

    }

    /**
     * Disconnects the URL specified on the endpoint from the specified
     * processor.
     */
    public void disconnect(WebsocketProducerConsumer prodcon) throws Exception {
        // If the connector is not needed anymore then stop it
        WebsocketEndpoint endpoint = prodcon.getEndpoint();
        String connectorKey = getConnectorKey(endpoint);

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef != null) {
                if (connectorRef.decrement() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectorRef.server.stop();
                    CONNECTORS.remove(connectorKey);
                    // Camel controls the lifecycle of these entities so remove the
                    // registered MBeans when Camel is done with the managed objects.
                    if (mbContainer != null) {
                        mbContainer.removeBean(connectorRef.server);
                        mbContainer.removeBean(connectorRef.connector);
                    }
                }
            }
        }
    }

    public synchronized MBeanContainer getMbContainer() {
        // If null, provide the default implementation.
        if (mbContainer == null) {
            MBeanServer mbs = null;

            final ManagementStrategy mStrategy = this.getCamelContext().getManagementStrategy();
            final ManagementAgent mAgent = mStrategy.getManagementAgent();
            if (mAgent != null) {
                mbs = mAgent.getMBeanServer();
            }

            if (mbs != null) {
                mbContainer = new MBeanContainer(mbs);
                startMbContainer();
            } else {
                LOG.warn("JMX disabled in CamelContext. Jetty JMX extensions will remain disabled.");
            }
        }

        return this.mbContainer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        Map<String, Object> websocketParameters = new HashMap<String, Object>(parameters);

        Boolean enableJmx = getAndRemoveParameter(parameters, "enableJmx", Boolean.class);
        SSLContextParameters sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParametersRef", SSLContextParameters.class);
        int port = extractPortNumber(remaining);
        String host = extractHostName(remaining);

        WebsocketEndpoint endpoint = new WebsocketEndpoint(this, uri, remaining, parameters);

        if (enableJmx != null) {
            endpoint.setEnableJmx(enableJmx);
        } else {
            endpoint.setEnableJmx(isEnableJmx());
        }

        if (sslContextParameters == null) {
            sslContextParameters = this.sslContextParameters;
        }

        endpoint.setSslContextParameters(sslContextParameters);
        endpoint.setPort(port);
        endpoint.setHost(host);

        setProperties(endpoint, parameters);
        return endpoint;
    }

    protected Server createServer() throws Exception {
        Server server = new Server();
        ContextHandlerCollection collection = new ContextHandlerCollection();
        server.setHandler(collection);

        // configure thread pool if min/max given
        if (minThreads != null || maxThreads != null) {
            if (getThreadPool() != null) {
                throw new IllegalArgumentException("You cannot configure both minThreads/maxThreads and a custom threadPool on JettyHttpComponent: " + this);
            }
            QueuedThreadPool qtp = new QueuedThreadPool();
            if (minThreads != null) {
                qtp.setMinThreads(minThreads.intValue());
            }
            if (maxThreads != null) {
                qtp.setMaxThreads(maxThreads.intValue());
            }
            // let the thread names indicate they are from the server
            qtp.setName("CamelJettyWebSocketServer(" + ObjectHelper.getIdentityHashCode(server) + ")");
            try {
                qtp.start();
            } catch (Exception e) {
                throw new RuntimeCamelException("Error starting JettyWebSocketServer thread pool: " + qtp, e);
            }
            server.setThreadPool(qtp);
        }

        if (getThreadPool() != null) {
            server.setThreadPool(getThreadPool());
        }

        return server;
    }

    protected WebsocketComponentServlet addServlet(NodeSynchronization sync, WebsocketConsumer consumer, String remaining) {
        String pathSpec = createPathSpec(remaining);
        WebsocketComponentServlet servlet = servlets.get(pathSpec);
        if (servlet == null) {
            servlet = createServlet(sync, pathSpec, servlets, context);
        }
        if (servlet.getConsumer() == null && consumer != null) {
            // TODO Do we have to call connect(consumer) or setConsumer on the Consumer endpoint
            servlet.setConsumer(consumer);
        }
        return servlet;
    }

    protected WebsocketComponentServlet createServlet(NodeSynchronization sync, String pathSpec, Map<String, WebsocketComponentServlet> servlets, ServletContextHandler handler) {
        WebsocketComponentServlet servlet = new WebsocketComponentServlet(sync);
        servlets.put(pathSpec, servlet);
        handler.addServlet(new ServletHolder(servlet), pathSpec);
        LOG.debug("WebSocket servlet added for the following path : " + pathSpec);
        return servlet;
    }


    protected ServletContextHandler createContext(Server server, Connector connector, List<Handler> handlers) throws Exception {
        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        context.setConnectorNames(new String[] {connector.getName()});

        if (handlers != null && !handlers.isEmpty()) {
            for (Handler handler : handlers) {
                if (handler instanceof HandlerWrapper) {
                    ((HandlerWrapper) handler).setHandler(server.getHandler());
                    server.setHandler(handler);
                } else {
                    HandlerCollection handlerCollection = new HandlerCollection();
                    handlerCollection.addHandler(server.getHandler());
                    handlerCollection.addHandler(handler);
                    server.setHandler(handlerCollection);
                }
            }
        }

        this.context = context;
        return context;

    }

    /**
     * Starts {@link #mbContainer} and registers the container with itself as a managed bean
     * logging an error if there is a problem starting the container.
     * Does nothing if {@link #mbContainer} is {@code null}.
     */
    protected void startMbContainer() {
        if (mbContainer != null && !mbContainer.isStarted()) {
            try {
                mbContainer.start();
                // Publish the container itself for consistency with
                // traditional embedded Jetty configurations.
                mbContainer.addBean(mbContainer);
            } catch (Throwable e) {
                LOG.warn("Could not start JettyWebSocket MBeanContainer. Jetty JMX extensions will remain disabled.", e);
            }
        }
    }

    private void enableSessionSupport(Server server, String connectorKey) throws Exception {
        ServletContextHandler context = server.getChildHandlerByClass(ServletContextHandler.class);
        if (context.getSessionHandler() == null) {
            SessionHandler sessionHandler = new SessionHandler();
            if (context.isStarted()) {
                throw new IllegalStateException("Server has already been started. Cannot enabled sessionSupport on " + connectorKey);
            } else {
                context.setSessionHandler(sessionHandler);
            }
        }
    }

    private SslConnector getSslSocketConnector(SSLContextParameters sslContextParameters) {
        SslSelectChannelConnector sslSocketConnector = null;
        if (sslContextParameters != null) {
            SslContextFactory sslContextFactory = new WebSocketComponentSslContextFactory();
            try {
                sslContextFactory.setSslContext(sslContextParameters.createSSLContext());
            } catch (Exception e) {
                throw new RuntimeCamelException("Error initiating SSLContext.", e);
            }
            sslSocketConnector = new SslSelectChannelConnector(sslContextFactory);
        } else {

            sslSocketConnector = new SslSelectChannelConnector();
            // with default null values, jetty ssl system properties
            // and console will be read by jetty implementation
            sslSocketConnector.getSslContextFactory().setKeyManagerPassword(sslPassword);
            sslSocketConnector.getSslContextFactory().setKeyStorePassword(sslKeyPassword);
            if (sslKeystore != null) {
                sslSocketConnector.getSslContextFactory().setKeyStorePath(sslKeystore);
            }

        }

        return sslSocketConnector;
    }


    /**
     * Override the key/trust store check method as it does not account for a factory that has
     * a pre-configured {@link javax.net.ssl.SSLContext}.
     */
    private static final class WebSocketComponentSslContextFactory extends SslContextFactory {
        // This method is for Jetty 7.0.x ~ 7.4.x
        @SuppressWarnings("unused")
        public boolean checkConfig() {
            if (getSslContext() == null) {
                return checkSSLContextFactoryConfig(this);
            } else {
                return true;
            }
        }

        // This method is for Jetty 7.5.x
        public void checkKeyStore() {
            // here we don't check the SslContext as it is already created
        }
    }

    private static boolean checkSSLContextFactoryConfig(Object instance) {
        try {
            Method method = instance.getClass().getMethod("checkConfig");
            return (Boolean) method.invoke(instance);
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (IllegalArgumentException e) {
            // ignore
        } catch (IllegalAccessException e) {
            // ignore
        } catch (InvocationTargetException e) {
            // ignore
        }
        return false;
    }

    private static String createPathSpec(String remaining) {
        // Is not correct as it does not support to add port in the URI
        //return String.format("/%s/*", remaining);

        int index = remaining.indexOf("/");
        if (index != -1) {
            return remaining.substring(index, remaining.length());
        } else {
            return "/" + remaining;
        }
    }

    private static int extractPortNumber(String remaining) {
        int index1 = remaining.indexOf(":");
        int index2 = remaining.indexOf("/");

        if ((index1 != -1) && (index2 != -1)) {
            String result = remaining.substring(index1 + 1, index2);
            return Integer.parseInt(result);
        } else {
            return 9292;
        }

    }

    private static String extractHostName(String remaining) {
        int index = remaining.indexOf(":");
        if (index != -1) {
            return remaining.substring(0, index);
        } else {
            return null;
        }

    }

    private String getConnectorKey(WebsocketEndpoint endpoint) {
        return endpoint.getProtocol() + ":" + endpoint.getHost() + ":" + endpoint.getPort();
    }

    private void enableJmx(Server server) {
        MBeanContainer containerToRegister = getMbContainer();
        if (containerToRegister != null) {
            LOG.info("Jetty JMX Extensions is enabled");
            server.getContainer().addEventListener(containerToRegister);
            // Since we may have many Servers running, don't tie the MBeanContainer
            // to a Server lifecycle or we end up closing it while it is still in use.
            //server.addBean(mbContainer);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getStaticResources() {
        return staticResources;
    }

    /**
     * Set a resource path for static resources (such as .html files etc).
     * <p/>
     * The resources can be loaded from classpath, if you prefix with <tt>classpath:</tt>,
     * otherwise the resources is loaded from file system or from JAR files.
     * <p/>
     * For example to load from root classpath use <tt>classpath:.</tt>, or
     * <tt>classpath:WEB-INF/static</tt>
     * <p/>
     * If not configured (eg <tt>null</tt>) then no static resource is in use.
     *
     * @param staticResources the base path
     */
    public void setStaticResources(String staticResources) {
        this.staticResources = staticResources;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    public String getSslPassword() {
        return sslPassword;
    }

    public String getSslKeystore() {
        return sslKeystore;
    }

    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public void setSslPassword(String sslPassword) {
        this.sslPassword = sslPassword;
    }

    public void setSslKeystore(String sslKeystore) {
        this.sslKeystore = sslKeystore;
    }

    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    public boolean isEnableJmx() {
        return enableJmx;
    }

    public Integer getMinThreads() {
        return minThreads;
    }

    public void setMinThreads(Integer minThreads) {
        this.minThreads = minThreads;
    }

    public Integer getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(Integer maxThreads) {
        this.maxThreads = maxThreads;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public ServletContextHandler getContext() {
        return context;
    }


    @Override
    protected void doStart() throws Exception {
        super.doStart();
        //LOG.info("Starting server {}:{}; static resources: {}", new Object[]{host, port, staticResources});
        //server = createServer(context, host, port, staticResources);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
        if (CONNECTORS.size() > 0) {
            for (String connectorKey : CONNECTORS.keySet()) {
                ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
                if (connectorRef != null && connectorRef.getRefCount() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectorRef.server.stop();
                }
                CONNECTORS.remove(connectorKey);
            }
        }

    }
}

