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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
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
    
    protected static final HashMap<String, ConnectorRef> CONNECTORS = new HashMap<String, ConnectorRef>();
   
    private static final transient Log LOG = LogFactory.getLog(JettyHttpComponent.class);
    private static final String JETTY_SSL_KEYSTORE = "jetty.ssl.keystore";
    
    protected String sslKeyPassword;
    protected String sslPassword;
    protected String sslKeystore;
    protected Map<Integer, SslSocketConnector> sslSocketConnectors;

    class ConnectorRef {
        Server server;
        Connector connector;
        CamelServlet servlet;
        int refCount;

        public ConnectorRef(Server server, Connector connector, CamelServlet servlet) {
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
    }
    
    
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        uri = uri.startsWith("jetty:") ? remaining : uri;

        HttpClientParams params = new HttpClientParams();
        IntrospectionSupport.setProperties(params, parameters, "httpClient.");

        // handlers
        List<Handler> handlerList = new ArrayList<Handler>();
        String handlers = getAndRemoveParameter(parameters, "handlers", String.class);
        if (handlers != null) {
            // remove any leading # for reference lookup as we know its a reference lookup
            handlers = handlers.replaceAll("#", "");
            // lookup each individual handler and add it to the list
            for (String key : handlers.split(",")) {
                handlerList.add(CamelContextHelper.mandatoryLookup(getCamelContext(), key, Handler.class));
            }
        }

        // configure regular parameters
        configureParameters(parameters);

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(UnsafeUriCharactersEncoder.encode(uri)), parameters);
        uri = httpUri.toString();

        JettyHttpEndpoint result = new JettyHttpEndpoint(this, uri, httpUri, params, getHttpConnectionManager(), httpClientConfigurer);
        if (httpBinding != null) {
            result.setBinding(httpBinding);
        }
        setEndpointHeaderFilterStrategy(result);
        if (handlerList.size() > 0) {
            result.setHandlers(handlerList);
        }
        setProperties(result, parameters);
        return result;
    }

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     */
    @Override
    public void connect(HttpConsumer consumer) throws Exception {
        // Make sure that there is a connector for the requested endpoint.
        JettyHttpEndpoint endpoint = (JettyHttpEndpoint)consumer.getEndpoint();
        String connectorKey = getConnectorKey(endpoint);

        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef == null) {
                Connector connector;
                if ("https".equals(endpoint.getProtocol())) {
                    connector = getSslSocketConnector(endpoint.getPort());
                } else {
                    connector = new SelectChannelConnector();
                }
                connector.setPort(endpoint.getPort());
                connector.setHost(endpoint.getHttpUri().getHost());
                if ("localhost".equalsIgnoreCase(endpoint.getHttpUri().getHost())) {
                    LOG.warn("You use localhost interface! It means that no external connections will be available. Don't you want to use 0.0.0.0 instead (all network interfaces)?");
                }
                Server server = createServer();
                server.addConnector(connector);

                connectorRef = new ConnectorRef(server, connector, createServletForConnector(server, connector, endpoint.getHandlers()));
                connector.start();
                
                CONNECTORS.put(connectorKey, connectorRef);
                
            } else {
                // ref track the connector
                connectorRef.increment();
            }
            // check the session support
            if (endpoint.isSessionSupport()) {                
                enableSessionSupport(connectorRef.server);
            }
            connectorRef.servlet.connect(consumer);
        }
    }

    private void enableSessionSupport(Server server) throws Exception {
        Context context = (Context)server.getChildHandlerByClass(Context.class);
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
        String connectorKey = getConnectorKey(endpoint);
        
        synchronized (CONNECTORS) {
            ConnectorRef connectorRef = CONNECTORS.get(connectorKey);
            if (connectorRef != null) {
                connectorRef.servlet.disconnect(consumer);
                if (connectorRef.decrement() == 0) {
                    connectorRef.server.removeConnector(connectorRef.connector);
                    connectorRef.connector.stop();
                    connectorRef.server.stop();
                    CONNECTORS.remove(connectorKey);
                }
            }
        }
    }
    
    private String getConnectorKey(HttpEndpoint endpoint) {
        return endpoint.getProtocol() + ":" + endpoint.getHttpUri().getHost() + ":" + endpoint.getPort();
    }

    // Properties
    // -------------------------------------------------------------------------
       
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

    public SslSocketConnector getSslSocketConnector(int port) {
        SslSocketConnector answer = null;
        if (sslSocketConnectors != null) {
            answer = sslSocketConnectors.get(port);
        }
        if (answer == null) {
            answer = createSslSocketConnector();
        } else {
            // try the keystore system property as a backup, jetty doesn't seem
            // to read this property anymore
            String keystoreProperty = System.getProperty(JETTY_SSL_KEYSTORE);
            if (keystoreProperty != null) {
                answer.setKeystore(keystoreProperty);
            }

        }
        return answer;
    }
    
    public SslSocketConnector createSslSocketConnector() {
        SslSocketConnector answer = new SslSocketConnector();
        // with default null values, jetty ssl system properties
        // and console will be read by jetty implementation
        answer.setPassword(sslPassword);
        answer.setKeyPassword(sslKeyPassword);
        if (sslKeystore != null) {
            answer.setKeystore(sslKeystore);
        } else {
            // try the keystore system property as a backup, jetty doesn't seem
            // to read this property anymore
            String keystoreProperty = System.getProperty(JETTY_SSL_KEYSTORE);
            if (keystoreProperty != null) {
                answer.setKeystore(keystoreProperty);
            }
        }
        
        return answer;
    }

    public void setSslSocketConnectors(Map <Integer, SslSocketConnector> connectors) {
        sslSocketConnectors = connectors;
    }

    protected CamelServlet createServletForConnector(Server server, Connector connector, List<Handler> handlers) throws Exception {
        CamelServlet camelServlet = new CamelServlet();

        Context context = new Context(server, "/", Context.NO_SECURITY | Context.NO_SESSIONS);
        context.setConnectorNames(new String[] {connector.getName()});

        if (handlers != null) {
            for (Handler handler : handlers) {
                context.addHandler(handler);
            }
        }

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
   
}
