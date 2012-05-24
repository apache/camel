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
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.http.ssl.SslContextFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;

public class WebsocketComponent extends DefaultComponent {

    protected static final Logger LOG = LoggerFactory.getLogger(WebsocketComponent.class);
    protected static final HashMap<String, ConnectorRef> CONNECTORS = new HashMap<String, ConnectorRef>();

    protected ServletContextHandler context;
    protected SSLContextParameters sslContextParameters;
    protected Server server;
    protected MBeanContainer mbContainer;

    protected Integer port;
    protected String host;

    protected boolean enableJmx;

    protected String staticResources;
    protected String sslKeyPassword;
    protected String sslPassword;
    protected String sslKeystore;

    class ConnectorRef {
        Server server;
        Connector connector;
        Servlet servlet;
        int refCount;

        public ConnectorRef(Server server, Connector connector, Servlet servlet) {
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

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     */
    public void connect(WebsocketProducerConsumer prodcon) throws Exception {

        Server server = null;
        DefaultServlet defaultServlet = null;
        String baseResource = null;
        WebsocketEndpoint endpoint = prodcon.getEndpoint();

        String connectorKey = "websocket" + ":" + endpoint.getHost() + ":" + endpoint.getPort();

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if (endpoint.getSslContextParameters() != null) {
                    connector = getSslSocketConnector(endpoint.getSslContextParameters());
                } else {
                    connector = new SelectChannelConnector();
                }

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

                // Define Context and SessionManager
                context.setContextPath("/");

                SessionManager sm = new HashSessionManager();
                SessionHandler sh = new SessionHandler(sm);
                context.setSessionHandler(sh);

                if (endpoint.getHome() != null) {
                    if (endpoint.getHome().startsWith("classpath:")) {
                        baseResource = ObjectHelper.after(endpoint.getHome(), "classpath:");
                        LOG.debug("Using base resource from classpath: {}", baseResource);
                        context.setBaseResource(new JettyClassPathResource(getCamelContext().getClassResolver(), baseResource));
                    } else {
                        LOG.debug("Using base resource: {}", baseResource);
                        context.setResourceBase(baseResource);
                    }
                    defaultServlet = new DefaultServlet();
                    ServletHolder holder = new ServletHolder(defaultServlet);

                    // avoid file locking on windows
                    // http://stackoverflow.com/questions/184312/how-to-make-jetty-dynamically-load-static-pages
                    holder.setInitParameter("useFileMappedBuffer", "false");
                    context.addServlet(holder, "/");
                }

                // Create Server and add connector
                server = new Server();
                server.addConnector(connector);
                server.setHandler(context);

                connectorRef = new ConnectorRef(server, connector, defaultServlet);
                CONNECTORS.put(connectorKey, connectorRef);

                server.start();

            } else {
                connectorRef.increment();
            }

        }

    }

    /**
     * Disconnects the URL specified on the endpoint from the specified
     * processor.
     */
    public void disconnect(WebsocketProducerConsumer prodcon) throws Exception {

        WebsocketEndpoint endpoint = prodcon.getEndpoint();
        String connectorKey = "websocket" + ":" + endpoint.getHost() + ":" + endpoint.getPort();

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef != null) {
                if (connectorRef.decrement() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectorRef.server.stop();
                    CONNECTORS.remove(CONNECTORS);
                }
            }
        }

    }

    /*protected Server createServer(ServletContextHandler context, String host, int port, String home) {

        String connectorKey = "websocket" + ":" + host + ":" + port;
        Server server = null;
        DefaultServlet defaultServlet = null;
        // WebsocketComponent websocketComponent = (WebsocketComponent) this.getCamelContext().getEndpoint(connectorKey);

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if (sslContextParameters != null) {
                    connector = getSslSocketConnector();
                } else {
                    connector = new SelectChannelConnector();
                }

                connector.setHost(host);
                connector.setPort(port);

                // Define Context and SessionManager
                context.setContextPath("/");

                SessionManager sm = new HashSessionManager();
                SessionHandler sh = new SessionHandler(sm);
                context.setSessionHandler(sh);

                if (home != null) {
                    if (home.startsWith("classpath:")) {
                        home = ObjectHelper.after(home, "classpath:");
                        LOG.debug("Using base resource from classpath: {}", home);
                        context.setBaseResource(new JettyClassPathResource(getCamelContext().getClassResolver(), home));
                    } else {
                        LOG.debug("Using base resource: {}", home);
                        context.setResourceBase(home);
                    }
                    defaultServlet = new DefaultServlet();
                    ServletHolder holder = new ServletHolder(defaultServlet);

                    // avoid file locking on windows
                    // http://stackoverflow.com/questions/184312/how-to-make-jetty-dynamically-load-static-pages
                    holder.setInitParameter("useFileMappedBuffer", "false");
                    context.addServlet(holder, "/");
                }

                // Create Server and add connector
                server = new Server();
                server.addConnector(connector);
                server.setHandler(context);
                connectorRef = new ConnectorRef(server, connector, defaultServlet);

                CONNECTORS.put(connectorKey, connectorRef);

            } else {
                connectorRef.increment();
            }

        }

        return server;
    }
*/
    protected SslConnector getSslSocketConnector(SSLContextParameters sslContextParameters) {
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

    public WebsocketComponentServlet addServlet(NodeSynchronization sync, WebsocketConsumer consumer, String remaining) {
        String pathSpec = createPathSpec(remaining);
        WebsocketComponentServlet servlet = servlets.get(pathSpec);
        if (servlet == null) {
            servlet = createServlet(sync, pathSpec, servlets, context);
        }
        if (servlet.getConsumer() == null && consumer != null) {
            servlet.setConsumer(consumer);
        }
        return servlet;
    }

    WebsocketComponentServlet createServlet(NodeSynchronization sync, String pathSpec, Map<String, WebsocketComponentServlet> servlets, ServletContextHandler handler) {
        WebsocketComponentServlet servlet = new WebsocketComponentServlet(sync);
        servlets.put(pathSpec, servlet);
        handler.addServlet(new ServletHolder(servlet), pathSpec);
        return servlet;
    }

    ServletContextHandler createContext() {
        return new ServletContextHandler(ServletContextHandler.SESSIONS);
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

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }


    @Override
    protected void doStart() throws Exception {
        super.doStart();
        context = createContext();
        //LOG.info("Starting server {}:{}; static resources: {}", new Object[]{host, port, staticResources});
        //server = createServer(context, host, port, staticResources);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
        for (ConnectorRef connectorRef : CONNECTORS.values()) {
            connectorRef.server.removeConnector(connectorRef.connector);
            connectorRef.connector.stop();
            connectorRef.server.stop();
        }
        CONNECTORS.clear();
    }

}
