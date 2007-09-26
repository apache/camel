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
import org.apache.camel.component.http.HttpExchange;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.client.HttpClient;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

/**
 * An HttpComponent which starts an embedded Jetty for to handle consuming from
 * the http endpoints.
 * 
 * @version $Revision$
 */
public class JettyHttpComponent extends HttpComponent {

    class ConnectorRef {
        Connector connector;
        int refCount;

        public ConnectorRef(Connector connector) {
            this.connector = connector;
            increment();
        }

        public int increment() {
            return ++refCount;
        }

        public int decrement() {
            return --refCount;
        }
    }

    private CamelServlet camelServlet;
    private Server server;
    private final HashMap<String, ConnectorRef> connectors = new HashMap<String, ConnectorRef>();
    private HttpClient httpClient;

    @Override
    protected Endpoint<HttpExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        URI httpURL = uri.startsWith("jetty:") ? new URI(remaining) : new URI(uri);
        return new JettyHttpEndpoint(this, uri, httpURL);
    }

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     * 
     * @throws Exception
     */
    @Override
    public void connect(HttpConsumer consumer) throws Exception {

        // Make sure that there is a connector for the requested endpoint.
        HttpEndpoint endpoint = (HttpEndpoint)consumer.getEndpoint();
        String connectorKey = endpoint.getProtocol() + ":" + endpoint.getPort();

        synchronized (connectors) {
            ConnectorRef connectorRef = connectors.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if ("https".equals(endpoint.getProtocol())) {
                    connector = new SslSocketConnector();
                } else {
                    connector = new SelectChannelConnector();
                }
                connector.setPort(endpoint.getPort());
                getServer().addConnector(connector);
                connector.start();
                connectorRef = new ConnectorRef(connector);
                connectors.put(connectorKey, connectorRef);
            } else {
                // ref track the connector
                connectorRef.increment();
            }
        }

        camelServlet.connect(consumer);
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified
     * processor.
     * 
     * @throws Exception
     */
    @Override
    public void disconnect(HttpConsumer consumer) throws Exception {
        camelServlet.disconnect(consumer);

        // If the connector is not needed anymore.. then stop it.
        HttpEndpoint endpoint = (HttpEndpoint)consumer.getEndpoint();
        String connectorKey = endpoint.getProtocol() + ":" + endpoint.getPort();

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

    // Implementation methods
    // -------------------------------------------------------------------------

    protected Server createServer() throws Exception {
        camelServlet = new CamelContinuationServlet();

        Server server = new Server();
        Context context = new Context(Context.NO_SECURITY | Context.NO_SESSIONS);

        context.setContextPath("/");
        ServletHolder holder = new ServletHolder();
        holder.setServlet(camelServlet);
        context.addServlet(holder, "/*");
        server.setHandler(context);

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
