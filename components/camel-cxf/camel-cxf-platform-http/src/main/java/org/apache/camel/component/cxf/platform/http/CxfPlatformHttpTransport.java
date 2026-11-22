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
package org.apache.camel.component.cxf.platform.http;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.ext.web.Route;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerLifeCycleListener;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http.HttpDestinationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the SOAP services of cxf consumers on the platform-http server (Vert.x) of the CamelContext.
 * <p/>
 * The address of a cxf consumer is a path, such as {@code cxf:/orders}. For such an address CXF creates a servlet
 * destination, as it does for a service behind the CXF servlet of Spring Boot. When the CXF server of the consumer
 * starts, this listener adds a route for {@code <path><address>} (path default {@code /services}) on the router of the
 * platform-http server, which gives each request to the destination on a worker thread. When the server stops, the
 * route is removed.
 * <p/>
 * An address with a scheme and a host (such as {@code http://0.0.0.0:9000/orders}) would need a server of its own; CXF
 * asks the {@link HttpDestinationFactory} for it, and this one fails with a message that says to use a path.
 * <p/>
 * camel-cxf-soap registers the beans of the registry of these two types on the bus of each cxf consumer.
 */
public class CxfPlatformHttpTransport implements HttpDestinationFactory, ServerLifeCycleListener, CamelContextAware {

    /**
     * The name of the bean of this transport in the registry.
     */
    public static final String BEAN_NAME = "camelCxfPlatformHttpTransport";
    /**
     * The default path that the services are served under, the same as {@code cxf.path} of CXF on Spring Boot and
     * {@code quarkus.cxf.path} of Quarkus CXF.
     */
    public static final String DEFAULT_PATH = "/services";
    /**
     * The property with the path that the services are served under on the platform-http server.
     */
    public static final String PATH_PROPERTY = "camel.cxf.path";
    /**
     * The property that says whether the request URL comes from the X-Forwarded headers (behind a proxy).
     */
    public static final String USE_X_FORWARDED_HEADERS_PROPERTY = "camel.cxf.useXForwardedHeaders";

    private static final Logger LOG = LoggerFactory.getLogger(CxfPlatformHttpTransport.class);

    private final Map<String, Mounted> mounted = new ConcurrentHashMap<>();
    private CamelContext camelContext;
    private String path;
    private Boolean useXForwardedHeaders;
    private Runnable httpServerStarter;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getPath() {
        return path != null ? path : DEFAULT_PATH;
    }

    /**
     * The path that the services are served under on the platform-http server (default {@code /services}, or the
     * {@code camel.cxf.path} property). A service with address {@code /orders} is at {@code <path>/orders}.
     */
    public void setPath(String path) {
        this.path = normalizePath(path);
    }

    public boolean isUseXForwardedHeaders() {
        return useXForwardedHeaders != null && useXForwardedHeaders;
    }

    /**
     * Whether the scheme, host and port of the request URL come from the {@code X-Forwarded-Proto},
     * {@code X-Forwarded-Host} and {@code X-Forwarded-Port} headers, and the client address from
     * {@code X-Forwarded-For}. The request URL is the base of the address that the WSDL at {@code ?wsdl} gives (unless
     * the endpoint has a {@code publishedEndpointUrl}). Turn it on only behind a proxy that sets these headers, as a
     * client can set them too. Default false, or the {@code camel.cxf.useXForwardedHeaders} property.
     */
    public void setUseXForwardedHeaders(boolean useXForwardedHeaders) {
        this.useXForwardedHeaders = useXForwardedHeaders;
    }

    public Runnable getHttpServerStarter() {
        return httpServerStarter;
    }

    /**
     * Called when the first service is served, to start the platform-http server when it is not running yet (Camel
     * JBang starts it this way, only when a route has a cxf consumer).
     */
    public void setHttpServerStarter(Runnable httpServerStarter) {
        this.httpServerStarter = httpServerStarter;
    }

    /**
     * The paths of the services that are served, under {@link #getPath()}.
     */
    public Set<String> getServedPaths() {
        return Set.copyOf(mounted.keySet());
    }

    @Override
    public AbstractHTTPDestination createDestination(EndpointInfo endpointInfo, Bus bus, DestinationRegistry registry)
            throws IOException {
        // CXF asks for a destination of its own only for an address with a scheme and a host (or without an address)
        String address = endpointInfo.getAddress();
        String path = getPath();
        throw new IllegalArgumentException(
                "The address of a cxf consumer must be a path, such as cxf:/orders, but was: " + address
                                           + ". The service is served on the platform-http server under " + path
                                           + " (" + path + "/orders); its host and port are those of the platform-http"
                                           + " server (camel.server.host and camel.server.port).");
    }

    @Override
    public void startServer(Server server) {
        Destination destination = server.getDestination();
        if (!(destination instanceof AbstractHTTPDestination httpDestination)) {
            return;
        }
        String servicePath = servicePath(httpDestination.getPath());
        if (servicePath == null) {
            // not a destination for a path (such as one that another transport serves)
            return;
        }
        resolveOptions();
        String path = getPath();
        Mounted existing = mounted.get(servicePath);
        if (existing != null) {
            if (existing.server == server) {
                // the listener is registered once per cxf endpoint on a bus that endpoints may share
                return;
            }
            throw new IllegalStateException(
                    "A cxf consumer is already served at " + path + servicePath + " on the platform-http server");
        }
        if (httpServerStarter != null) {
            httpServerStarter.run();
        }
        VertxPlatformHttpRouter router = lookupRouter();
        CxfPlatformHttpHandler handler = new CxfPlatformHttpHandler(httpDestination, path, isUseXForwardedHeaders());
        Route route = router.route(path + servicePath).handler(handler);
        mounted.put(servicePath, new Mounted(server, route));
        LOG.info("cxf consumer served on the platform-http server at {}{}", path, servicePath);
    }

    @Override
    public void stopServer(Server server) {
        mounted.entrySet().removeIf(e -> {
            if (e.getValue().server == server) {
                e.getValue().route.remove();
                LOG.debug("cxf consumer no longer served at {}{}", getPath(), e.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * The options that are not set are read from the properties ({@code camel.cxf.path} and
     * {@code camel.cxf.useXForwardedHeaders}), when the first service starts, as the properties are complete then.
     */
    private synchronized void resolveOptions() {
        if (camelContext == null) {
            return;
        }
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        if (path == null) {
            pc.resolveProperty(PATH_PROPERTY).ifPresent(this::setPath);
        }
        if (useXForwardedHeaders == null) {
            pc.resolveProperty(USE_X_FORWARDED_HEADERS_PROPERTY).map(Boolean::parseBoolean)
                    .ifPresent(this::setUseXForwardedHeaders);
        }
    }

    /**
     * The path of a destination under {@link #getPath()}, or null when it is not a path destination. The destination
     * keeps the address it was created for, while CXF updates the address of its endpoint to the URL of each request.
     */
    static String servicePath(String destinationPath) {
        String path = destinationPath;
        if (ObjectHelper.isEmpty(path) || path.contains("://")) {
            return null;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private VertxPlatformHttpRouter lookupRouter() {
        Set<VertxPlatformHttpRouter> routers = camelContext.getRegistry().findByType(VertxPlatformHttpRouter.class);
        VertxPlatformHttpRouter router = routers.stream()
                .filter(VertxPlatformHttpRouter::isMainServer)
                .findFirst()
                // a bare VertxPlatformHttpServer may have no server type
                .orElseGet(() -> routers.size() == 1 ? routers.iterator().next() : null);
        if (router == null) {
            throw new IllegalStateException(
                    "A cxf consumer is served on the platform-http server (camel-cxf-platform-http), which is not"
                                            + " running. Enable the HTTP server of Camel Main (camel.server.enabled=true"
                                            + " with camel-platform-http-main on the classpath).");
        }
        return router;
    }

    static String normalizePath(String path) {
        if (ObjectHelper.isEmpty(path) || "/".equals(path)) {
            return "";
        }
        String answer = path.trim();
        if (!answer.startsWith("/")) {
            answer = "/" + answer;
        }
        while (answer.endsWith("/")) {
            answer = answer.substring(0, answer.length() - 1);
        }
        return answer;
    }

    private record Mounted(Server server, Route route) {
    }
}
