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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.cometd.Extension;
import org.cometd.SecurityPolicy;
import org.cometd.server.AbstractBayeux;
import org.cometd.server.continuation.ContinuationCometdServlet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for Jetty Cometd
 */
public class CometdComponent extends DefaultComponent {
    private static final transient Logger LOG = LoggerFactory.getLogger(CometdComponent.class);

    private final Map<String, ConnectorRef> connectors = new HashMap<String, ConnectorRef>();

    private Server server;
    private String sslKeyPassword;
    private String sslPassword;
    private String sslKeystore;
    private SslSocketConnector sslSocketConnector;
    private SecurityPolicy securityPolicy;
    private List<Extension> extensions;

    class ConnectorRef {
        Connector connector;
        ContinuationCometdServlet servlet;
        int refCount;

        public ConnectorRef(Connector connector,
                            ContinuationCometdServlet servlet) {
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
        CometdEndpoint endpoint = (CometdEndpoint) prodcon.getEndpoint();
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
                getServer().addConnector(connector);

                ContinuationCometdServlet servlet = createServletForConnector(connector, endpoint);
                connectorRef = new ConnectorRef(connector, servlet);
                getServer().start();

                connectors.put(connectorKey, connectorRef);
            } else {
                connectorRef.increment();
            }

            AbstractBayeux bayeux = connectorRef.servlet.getBayeux();
            bayeux.setJSONCommented(endpoint.isJsonCommented());

            if (securityPolicy != null) {
                bayeux.setSecurityPolicy(securityPolicy);
            }
            if (extensions != null) {
                for (Extension extension : extensions) {
                    bayeux.addExtension(extension);
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
                    getServer().removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectors.remove(connectorKey);
                }
            }
        }
    }

    protected ContinuationCometdServlet createServletForConnector(Connector connector, CometdEndpoint endpoint) throws Exception {
        ContinuationCometdServlet servlet = new ContinuationCometdServlet();

        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        context.setConnectorNames(new String[]{connector.getName()});

        ServletHolder holder = new ServletHolder();
        holder.setServlet(servlet);

        // Use baseResource to pass as a parameter the url
        // pointing to by example classpath:webapp
        if (endpoint.getBaseResource() != null) {
            String[] resources = endpoint.getBaseResource().split(":");
            LOG.debug(">>> Protocol found :" + resources[0] + ", and resource : " + resources[1]);

            if (resources[0].equals("file")) {
                context.setBaseResource(Resource.newResource(resources[1]));
            } else if (resources[0].equals("classpath")) {
                // Create a URL handler using classpath protocol
                URL url = this.getCamelContext().getClassResolver().loadResourceAsURL(resources[1]);
                context.setBaseResource(Resource.newResource(url));
            }
        }

        context.addServlet(holder, "/cometd/*");
        context.addServlet("org.eclipse.jetty.servlet.DefaultServlet", "/");

        holder.setInitParameter("timeout", Integer.toString(endpoint.getTimeout()));
        holder.setInitParameter("interval", Integer.toString(endpoint.getInterval()));
        holder.setInitParameter("maxInterval", Integer.toString(endpoint.getMaxInterval()));
        holder.setInitParameter("multiFrameInterval", Integer.toString(endpoint.getMultiFrameInterval()));
        holder.setInitParameter("JSONCommented", Boolean.toString(endpoint.isJsonCommented()));
        holder.setInitParameter("logLevel", Integer.toString(endpoint.getLogLevel()));

        return servlet;
    }

    public synchronized SslSocketConnector getSslSocketConnector() {
        if (sslSocketConnector == null) {
            sslSocketConnector = new SslSocketConnector();
            // with default null values, jetty ssl system properties
            // and console will be read by jetty implementation
            sslSocketConnector.setPassword(sslPassword);
            sslSocketConnector.setKeyPassword(sslKeyPassword);
            if (sslKeystore != null) {
                sslSocketConnector.setKeystore(sslKeystore);
            }
        }
        return sslSocketConnector;
    }

    public Server getServer() throws Exception {
        if (server == null) {
            server = createServer();
        }
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
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

    public List<Extension> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
    }

    public void addExtension(Extension extension) {
        if (extensions == null) {
            extensions = new ArrayList<Extension>();
        }
        extensions.add(extension);
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

        if (server != null) {
            server.stop();
        }
        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }
}
