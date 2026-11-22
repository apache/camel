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

import java.util.Collections;
import java.util.Enumeration;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.servlet.BaseUrlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives the requests of a path on the platform-http server to the CXF destination of a cxf consumer, on a worker
 * thread, as CXF is blocking. The request body is streamed to CXF, and the response is streamed back.
 */
final class CxfPlatformHttpHandler implements Handler<RoutingContext> {

    /**
     * The request attribute with the address of the endpoint for this request, as the CXF servlet sets it.
     */
    static final String ENDPOINT_ADDRESS = "org.apache.cxf.transport.endpoint.address";

    private static final Logger LOG = LoggerFactory.getLogger(CxfPlatformHttpHandler.class);
    private static final ServletConfig SERVLET_CONFIG = new PlatformHttpServletConfig();

    private final AbstractHTTPDestination destination;
    private final String servletPath;
    private final boolean useXForwardedHeaders;

    CxfPlatformHttpHandler(AbstractHTTPDestination destination, String servletPath, boolean useXForwardedHeaders) {
        this.destination = destination;
        this.servletPath = servletPath;
        this.useXForwardedHeaders = useXForwardedHeaders;
    }

    @Override
    public void handle(RoutingContext rc) {
        // on the event loop: the stream reads the (paused) request on demand, from the worker thread
        Context context = rc.vertx().getOrCreateContext();
        VertxServletInputStream in = new VertxServletInputStream(context, rc.request());
        VertxServletResponse response = new VertxServletResponse(context, rc.response());
        context.executeBlocking(() -> {
            process(rc, in, response);
            return null;
        }, false).onFailure(e -> {
            LOG.warn("Error serving the cxf consumer at {}: {}", rc.request().path(), e.getMessage(), e);
            response.fail();
        });
    }

    private void process(RoutingContext rc, VertxServletInputStream in, VertxServletResponse response)
            throws Exception {
        VertxServletRequest request = new VertxServletRequest(
                rc, in, contextPath(rc), servletPath, destination.getPath(), useXForwardedHeaders);
        Bus bus = destination.getBus();
        Bus origBus = BusFactory.getAndSetThreadDefaultBus(bus);
        ClassLoaderUtils.ClassLoaderHolder origLoader = null;
        try {
            ClassLoader loader = bus != null ? bus.getExtension(ClassLoader.class) : null;
            if (loader != null) {
                origLoader = ClassLoaderUtils.setThreadContextClassloader(loader);
            }
            if (destination.getMessageObserver() == null) {
                // the consumer is stopping
                response.sendError(503, "The service is not available");
            } else {
                // the address of the endpoint for this request, from its URL, as the CXF servlet does it
                String address = destination.getEndpointInfo().getAddress();
                if (address != null && !address.startsWith("http")) {
                    request.setAttribute(ENDPOINT_ADDRESS, BaseUrlHelper.getBaseURL(request) + address);
                }
                destination.invoke(SERVLET_CONFIG, SERVLET_CONFIG.getServletContext(), request, response);
            }
            response.close();
        } finally {
            BusFactory.setThreadDefaultBus(origBus);
            if (origLoader != null) {
                origLoader.reset();
            }
        }
    }

    private static String contextPath(RoutingContext rc) {
        // the path that the router of the platform-http server is mounted at (camel.server.path)
        String mount = rc.mountPoint();
        return CxfPlatformHttpTransport.normalizePath(mount);
    }

    /**
     * The CXF destination reads no init parameters and needs no servlet context.
     */
    private static final class PlatformHttpServletConfig implements ServletConfig {
        @Override
        public String getServletName() {
            return "camel-cxf-platform-http";
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.emptyEnumeration();
        }
    }
}
