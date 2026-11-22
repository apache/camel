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
package org.apache.camel.main.download;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the cxf consumers on the platform-http server (camel-cxf-platform-http), as the CXF extension of Quarkus does
 * on the Vert.x server of Quarkus.
 * <p>
 * When the first route with a cxf consumer is added (a route of the integration or of a Kamelet),
 * camel-cxf-platform-http is downloaded, as a dependency of the integration, so camel export includes it, and its
 * transport is bound with a starter of the platform-http server, which starts when the first cxf consumer starts. An
 * integration with only cxf producers (SOAP clients) needs neither.
 */
public final class DependencyDownloaderCxf extends ServiceSupport
        implements CamelContextAware, ModelLifecycleStrategy {

    static final String TRANSPORT_BEAN_NAME = "camelCxfPlatformHttpTransport";
    private static final String TRANSPORT_CLASS
            = "org.apache.camel.component.cxf.platform.http.CxfPlatformHttpTransport";
    private static final Logger LOG = LoggerFactory.getLogger(DependencyDownloaderCxf.class);

    private CamelContext camelContext;
    private final boolean silent;
    private final AtomicBoolean done = new AtomicBoolean();

    public DependencyDownloaderCxf(CamelContext camelContext, boolean silent) {
        this.camelContext = camelContext;
        this.silent = silent;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doBuild() throws Exception {
        if (camelContext instanceof ModelCamelContext mcc) {
            mcc.addModelLifecycleStrategy(this);
            // the routes that are added already
            for (RouteDefinition route : mcc.getRouteDefinitions()) {
                onAddRouteDefinition(route);
            }
        }
    }

    @Override
    public void onAddRouteDefinition(RouteDefinition routeDefinition) {
        if (!done.get() && routeDefinition.getInput() != null
                && isCxfConsumer(camelContext, routeDefinition.getInput().getEndpointUri())
                && done.compareAndSet(false, true)) {
            LOG.debug("Route: {} has a cxf consumer, which is served on the platform-http server",
                    routeDefinition.getRouteId());
            servePlatformHttp();
        }
    }

    @Override
    public void onRemoveRouteDefinition(RouteDefinition routeDefinition) {
        // noop
    }

    @Override
    public void onAddRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) {
        // noop
    }

    @Override
    public void onRemoveRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) {
        // noop
    }

    /**
     * Whether the endpoint uri of the input of a route is cxf (a SOAP service); not cxfrs, which is another component.
     */
    static boolean isCxfConsumer(CamelContext camelContext, String uri) {
        if (uri != null && uri.startsWith("{{")) {
            // the uri is a property placeholder
            try {
                uri = camelContext.resolvePropertyPlaceholders(uri);
            } catch (Exception e) {
                return false;
            }
        }
        return uri != null && uri.startsWith("cxf:");
    }

    private void servePlatformHttp() {
        DependencyDownloader downloader = camelContext.hasService(DependencyDownloader.class);
        if (downloader != null) {
            downloader.downloadDependency("org.apache.camel", "camel-cxf-platform-http", camelContext.getVersion());
        }
        try {
            Object transport = camelContext.getRegistry().lookupByName(TRANSPORT_BEAN_NAME);
            if (transport == null) {
                Class<?> type = camelContext.getClassResolver().resolveMandatoryClass(TRANSPORT_CLASS);
                transport = type.getConstructor().newInstance();
                CamelContextAware.trySetCamelContext(transport, camelContext);
                camelContext.getRegistry().bind(TRANSPORT_BEAN_NAME, transport);
            }
            // start the platform-http server when the first cxf consumer starts
            Runnable starter = () -> MainHttpServerFactory.setupHttpServer(camelContext, false);
            transport.getClass().getMethod("setHttpServerStarter", Runnable.class).invoke(transport, starter);
            if (!silent) {
                LOG.info("Camel CLI serves cxf consumers on the platform-http server under {}",
                        camelContext.resolvePropertyPlaceholders("{{camel.cxf.path:/services}}"));
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }
}
