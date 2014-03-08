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
package org.apache.camel.component.cometd;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.servlet.DispatcherType;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.SecurityPolicy;
import org.cometd.server.BayeuxServerImpl;
import org.cometd.server.CometdServlet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for Jetty Cometd
 */
public class CometdComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(CometdComponent.class);

    private final Map<String, ConnectorRef> connectors = new LinkedHashMap<String, ConnectorRef>();
   
    private String sslKeyPassword;
    private String sslPassword;
    private String sslKeystore;
    private SecurityPolicy securityPolicy;
    private List<BayeuxServer.Extension> extensions;
    private List<BayeuxServer.BayeuxServerListener> serverListeners;
    private SSLContextParameters sslContextParameters;

    class ConnectorRef {
        Connector connector;
        CometdServlet servlet;
        Server server;
        int refCount;

        public ConnectorRef(Connector connector, CometdServlet servlet, Server server) {
            this.connector = connector;
            this.servlet = servlet;
            this.server = server;
            increment();
        }

        public int increment() {
            return ++refCount;
        }

        public int decrement() {
            return --refCount;
        }
    }

    public CometdComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        setProperties(this, parameters);
        return new CometdEndpoint(this, uri, remaining, parameters);
    }

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     */
    public void connect(CometdProducerConsumer prodcon) throws Exception {
        // Make sure that there is a connector for the requested endpoint.
        CometdEndpoint endpoint = prodcon.getEndpoint();
        String connectorKey = endpoint.getProtocol() + ":" + endpoint.getUri().getHost() + ":" + endpoint.getPort();

        synchronized (connectors) {
            ConnectorRef connectorRef = connectors.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if ("cometds".equals(endpoint.getProtocol())) {
                    connector = getSslSocketConnector();
                } else {
                    connector = new SelectChannelConnector();
                }
                connector.setPort(endpoint.getPort());
                connector.setHost(endpoint.getUri().getHost());
                if ("localhost".equalsIgnoreCase(endpoint.getUri().getHost())) {
                    LOG.warn("You use localhost interface! It means that no external connections will be available."
                            + " Don't you want to use 0.0.0.0 instead (all network interfaces)?");
                }
                Server server = createServer();
                server.addConnector(connector);

                CometdServlet servlet = createServletForConnector(server, connector, endpoint);
                connectorRef = new ConnectorRef(connector, servlet, server);
                server.start();

                connectors.put(connectorKey, connectorRef);
            } else {
                connectorRef.increment();
            }

            BayeuxServerImpl bayeux = connectorRef.servlet.getBayeux();

            if (securityPolicy != null) {
                bayeux.setSecurityPolicy(securityPolicy);
            }
            if (extensions != null) {
                for (BayeuxServer.Extension extension : extensions) {
                    bayeux.addExtension(extension);
                }
            }
            if (serverListeners != null) {
                for (BayeuxServer.BayeuxServerListener serverListener : serverListeners) {
                    bayeux.addListener(serverListener);
                }
            }
            prodcon.setBayeux(bayeux);
        }
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified
     * processor.
     */
    public void disconnect(CometdProducerConsumer prodcon) throws Exception {
        CometdEndpoint endpoint = prodcon.getEndpoint();

        String connectorKey = endpoint.getProtocol() + ":" + endpoint.getUri().getHost() + ":" + endpoint.getPort();

        synchronized (connectors) {
            ConnectorRef connectorRef = connectors.get(connectorKey);
            if (connectorRef != null) {
                if (connectorRef.decrement() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectorRef.server.stop();
                    connectors.remove(connectorKey);
                }
            }
        }
    }

    protected CometdServlet createServletForConnector(Server server, Connector connector, CometdEndpoint endpoint) throws Exception {
        CometdServlet servlet = new CometdServlet();

        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        context.setConnectorNames(new String[]{connector.getName()});

        ServletHolder holder = new ServletHolder();
        holder.setServlet(servlet);
        holder.setAsyncSupported(true);

        // Use baseResource to pass as a parameter the url
        // pointing to by example classpath:webapp
        if (endpoint.getBaseResource() != null) {
            String[] resources = endpoint.getBaseResource().split(":");
            if (LOG.isDebugEnabled()) {
                LOG.debug(">>> Protocol found: " + resources[0] + ", and resource: " + resources[1]);
            }

            if (resources[0].equals("file")) {
                context.setBaseResource(Resource.newResource(resources[1]));
            } else if (resources[0].equals("classpath")) {
                // Create a URL handler using classpath protocol
                URL url = this.getCamelContext().getClassResolver().loadResourceAsURL(resources[1]);
                context.setBaseResource(Resource.newResource(url));
            }
        }

        applyCrossOriginFiltering(endpoint, context);

        context.addServlet(holder, "/cometd/*");
        context.addServlet("org.eclipse.jetty.servlet.DefaultServlet", "/");
        context.setSessionHandler(new SessionHandler(new HashSessionManager()));

        holder.setInitParameter("timeout", Integer.toString(endpoint.getTimeout()));
        holder.setInitParameter("interval", Integer.toString(endpoint.getInterval()));
        holder.setInitParameter("maxInterval", Integer.toString(endpoint.getMaxInterval()));
        holder.setInitParameter("multiFrameInterval", Integer.toString(endpoint.getMultiFrameInterval()));
        holder.setInitParameter("JSONCommented", Boolean.toString(endpoint.isJsonCommented()));
        holder.setInitParameter("logLevel", Integer.toString(endpoint.getLogLevel()));

        return servlet;
    }

    protected SslConnector getSslSocketConnector() {
        SslSelectChannelConnector sslSocketConnector = null;
        if (sslContextParameters != null) {
            SslContextFactory sslContextFactory = new CometdComponentSslContextFactory();
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

    public void setSecurityPolicy(SecurityPolicy securityPolicy) {
        this.securityPolicy = securityPolicy;
    }

    public SecurityPolicy getSecurityPolicy() {
        return securityPolicy;
    }

    public List<BayeuxServer.Extension> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<BayeuxServer.Extension> extensions) {
        this.extensions = extensions;
    }

    public void addExtension(BayeuxServer.Extension extension) {
        if (extensions == null) {
            extensions = new ArrayList<BayeuxServer.Extension>();
        }
        extensions.add(extension);
    }
    
    public void addServerListener(BayeuxServer.BayeuxServerListener serverListener) {
        if (serverListeners == null) {
            serverListeners = new ArrayList<BayeuxServer.BayeuxServerListener>();
        }
        serverListeners.add(serverListener);
    }
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    protected Server createServer() throws Exception {
        Server server = new Server();
        ContextHandlerCollection collection = new ContextHandlerCollection();
        server.setHandler(collection);
        return server;
    }

    @Override
    protected void doStop() throws Exception {
        for (ConnectorRef connectorRef : connectors.values()) {
            connectorRef.connector.stop();
        }
        connectors.clear();
       
        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    private void applyCrossOriginFiltering(CometdEndpoint endpoint, ServletContextHandler context) {
        if (endpoint.isCrossOriginFilterOn()) {
            FilterHolder filterHolder = new FilterHolder();
            CrossOriginFilter filter = new CrossOriginFilter();
            filterHolder.setFilter(filter);
            filterHolder.setInitParameter("allowedOrigins", endpoint.getAllowedOrigins());
            context.addFilter(filterHolder, endpoint.getFilterPath(), EnumSet.allOf(DispatcherType.class));
        }
    }

    /**
     * Override the key/trust store check method as it does not account for a factory that has
     * a pre-configured {@link SSLContext}.
     */
    private static final class CometdComponentSslContextFactory extends SslContextFactory {
        @Override
        public void checkKeyStore() {
        }
    }
}
