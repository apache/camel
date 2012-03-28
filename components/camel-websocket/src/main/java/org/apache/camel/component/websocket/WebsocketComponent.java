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

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebsocketComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(WebsocketComponent.class);

    private ServletContextHandler context;
    private Server server;
    private String host = WebsocketConstants.DEFAULT_HOST;
    private int port = WebsocketConstants.DEFAULT_PORT;
    private String staticResources;

    /**
     * Map for storing endpoints. Endpoint is identified by remaining part from endpoint URI.
     * Eg. <tt>ws://foo?bar=123</tt> and <tt>ws://foo</tt> are referring to the same endpoint.
     */
    private Map<String, WebsocketEndpoint> endpoints = new HashMap<String, WebsocketEndpoint>();

    /**
     * Map for storing servlets. {@link WebsocketComponentServlet} is identified by pathSpec {@link String}.
     */
    private Map<String, WebsocketComponentServlet> servlets = new HashMap<String, WebsocketComponentServlet>();

    public WebsocketComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        WebsocketEndpoint endpoint = endpoints.get(remaining);
        if (endpoint == null) {
            endpoint = new WebsocketEndpoint(uri, this, remaining);
            setProperties(endpoint, parameters);
            endpoints.put(remaining, endpoint);
        }
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

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    protected Server createServer(ServletContextHandler context, String host, int port, String home) {
        InetSocketAddress address = new InetSocketAddress(host, port);
        Server server = new Server(address);

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
            DefaultServlet defaultServlet = new DefaultServlet();
            ServletHolder holder = new ServletHolder(defaultServlet);

            // avoid file locking on windows
            // http://stackoverflow.com/questions/184312/how-to-make-jetty-dynamically-load-static-pages
            holder.setInitParameter("useFileMappedBuffer", "false");
            context.addServlet(holder, "/");
        }

        server.setHandler(context);

        return server;
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
        return String.format("/%s/*", remaining);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Starting server {}:{}; static resources: {}", new Object[]{host, port, staticResources});
        context = createContext();
        server = createServer(context, host, port, staticResources);
        server.start();
    }

    @Override
    public void doStop() throws Exception {
        if (server != null) {
            LOG.info("Stopping server {}:{}", host, port);
            server.stop();
            server = null;
        }
        endpoints.clear();
    }

}
