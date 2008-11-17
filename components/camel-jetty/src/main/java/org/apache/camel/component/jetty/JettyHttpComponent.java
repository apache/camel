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
package org.apache.camel.component.jetty;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.client.HttpClient;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.SessionHandler;

/**
 * An HttpComponent which starts an embedded Jetty for to handle consuming from
 * the http endpoints.
 *
 * @version $Revision$
 */
public class JettyHttpComponent extends HttpComponent {

    class ConnectorRef {
        Connector connector;
        CamelServlet servlet;
        int refCount;

        public ConnectorRef(Connector connector, CamelServlet servlet) {
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
    
    private static final Log LOGGER = LogFactory.getLog(JettyHttpComponent.class);
    
    private Server server;
    private HashMap<String, ConnectorRef> connectors = new HashMap<String, ConnectorRef>();
    private HttpClient httpClient;
    private String sslKeyPassword;
    private String sslPassword;
    private String sslKeystore;
    private SslSocketConnector sslSocketConnector;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        URI httpURL = uri.startsWith("jetty:") ? new URI(remaining) : new URI(uri);
        JettyHttpEndpoint result = new JettyHttpEndpoint(this, uri, httpURL, getHttpConnectionManager());
        setProperties(result, parameters);
        return result;
    }

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     *
     * @throws Exception
     */
    @Override
    public void connect(HttpConsumer consumer) throws Exception {

        // Make sure that there is a connector for the requested endpoint.
        JettyHttpEndpoint endpoint = (JettyHttpEndpoint)consumer.getEndpoint();
        String connectorKey = endpoint.getProtocol() + ":" + endpoint.getHttpUri().getHost() + ":" + endpoint.getPort();

        synchronized (connectors) {
            ConnectorRef connectorRef = connectors.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if ("https".equals(endpoint.getProtocol())) {
                    connector = getSslSocketConnector();
                } else {
                    connector = new SelectChannelConnector();
                }
                connector.setPort(endpoint.getPort());
                connector.setHost(endpoint.getHttpUri().getHost());
                if ("localhost".equalsIgnoreCase(endpoint.getHttpUri().getHost())) {
                    LOGGER.warn("You use localhost interface! It means that no external connections will be available. Don't you want to use 0.0.0.0 instead (all network interfaces)?");
                }
                getServer().addConnector(connector);

                connectorRef = new ConnectorRef(connector, createServletForConnector(connector));
                connector.start();
                
                connectors.put(connectorKey, connectorRef);
                
            } else {
                // ref track the connector
                connectorRef.increment();
            }
            // check the session support
            if (endpoint.isSessionSupport()) {
                enableSessionSupport();
            }
            connectorRef.servlet.connect(consumer);
        }
    }

    private void enableSessionSupport() throws Exception {
        Context context = (Context)getServer().getChildHandlerByClass(Context.class);
        if (context.getSessionHandler() == null) {
            SessionHandler sessionHandler = new SessionHandler();
            context.setSessionHandler(sessionHandler);
            if (context.isStarted()) {
                // restart the context
                context.stop();
                context.start();
            }
        }

    }

    /**
     * Disconnects the URL specified on the endpoint from the specified
     * processor.
     */
    @Override
    public void disconnect(HttpConsumer consumer) throws Exception {
        // If the connector is not needed anymore then stop it
        HttpEndpoint endpoint = consumer.getEndpoint();
        String connectorKey = endpoint.getProtocol() + ":" + endpoint.getPort();

        synchronized (connectors) {
            ConnectorRef connectorRef = connectors.get(connectorKey);
            if (connectorRef != null) {
                connectorRef.servlet.disconnect(consumer);
                if (connectorRef.decrement() == 0) {
                    getServer().removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectors.remove(connectorKey);
                }
            }
        }
    }

    // Properties
    // -------------------------------------------------------------------------

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

    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getSslPassword() {
        return sslPassword;
    }

    public void setSslPassword(String sslPassword) {
        this.sslPassword = sslPassword;
    }

    public void setKeystore(String sslKeystore) {
        this.sslKeystore = sslKeystore;
    }

    public String getKeystore() {
        return sslKeystore;
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

    public void setSslSocketConnector(SslSocketConnector connector) {
        sslSocketConnector = connector;
    }

    protected CamelServlet createServletForConnector(Connector connector) throws Exception {
        CamelServlet camelServlet = new CamelContinuationServlet();
        
        Context context = new Context(server, "/", Context.NO_SECURITY | Context.NO_SESSIONS);
        context.setConnectorNames(new String[] {connector.getName()});

        ServletHolder holder = new ServletHolder();
        holder.setServlet(camelServlet);
        context.addServlet(holder, "/*");
        connector.start();
        context.start();
        

        return camelServlet;
    }
    
    // Implementation methods
    // -------------------------------------------------------------------------

    protected Server createServer() throws Exception {
        Server server = new Server();
        ContextHandlerCollection collection = new ContextHandlerCollection();
        collection.setServer(server);
        server.addHandler(collection);
        server.start();

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
        httpClient.stop();
        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (httpClient == null) {
            httpClient = createHttpClient();
        }
        httpClient.start();
    }

    protected HttpClient createHttpClient() throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        httpClient.setMaxConnectionsPerAddress(2);
        return httpClient;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
