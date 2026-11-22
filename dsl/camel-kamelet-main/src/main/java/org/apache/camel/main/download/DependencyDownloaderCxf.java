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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.EndpointRequiredDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.URISupport;
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
 * <p>
 * When the first cxf endpoint (a consumer or a producer) with a WS-Security UsernameToken is added (one of the
 * wsSecurity options that turn it on is set), cxf-rt-ws-security of the CXF version of Camel is downloaded, as a
 * dependency of the integration as well. The jar brings WSS4J and more, which most SOAP routes do not need.
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
    private final AtomicBoolean wsSecurityDone = new AtomicBoolean();

    /**
     * The options of a cxf endpoint that turn on a WS-Security UsernameToken (see
     * CxfEndpoint#isWsSecurityUsernameTokenEnabled), which needs cxf-rt-ws-security.
     */
    private static final Set<String> WS_SECURITY_OPTIONS = Set.of(
            "wsSecurityUsername", "wsSecurityPassword", "wsSecurityCallbackHandler", "wsSecurityCredentials");

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
        if (routeDefinition.getInput() == null) {
            return;
        }
        if (!done.get()
                && isCxfConsumer(camelContext, routeDefinition.getInput().getEndpointUri())
                && done.compareAndSet(false, true)) {
            LOG.debug("Route: {} has a cxf consumer, which is served on the platform-http server",
                    routeDefinition.getRouteId());
            servePlatformHttp();
        }
        if (!wsSecurityDone.get()
                && usesWsSecurity(camelContext, routeDefinition)
                && wsSecurityDone.compareAndSet(false, true)) {
            LOG.debug("Route: {} has a cxf endpoint with a WS-Security UsernameToken", routeDefinition.getRouteId());
            DependencyDownloader downloader = camelContext.hasService(DependencyDownloader.class);
            if (downloader != null) {
                downloader.downloadDependency("org.apache.cxf", "cxf-rt-ws-security", "${cxf-version}");
            }
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

    /**
     * Whether a cxf endpoint of the route (its input or an output) sets one of the wsSecurity options that turn on a
     * WS-Security UsernameToken, to a value that is not empty. The property placeholders are resolved first, with the
     * parameters of the route template (such as a Kamelet), so an optional parameter that is not set, as in
     * wsSecurityUsername=RAW({{?wsSecurityUsername}}), does not count.
     */
    static boolean usesWsSecurity(CamelContext camelContext, RouteDefinition route) {
        List<String> uris = new ArrayList<>();
        uris.add(route.getInput().getEndpointUri());
        for (EndpointRequiredDefinition erd : ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(),
                EndpointRequiredDefinition.class)) {
            uris.add(erd.getEndpointUri());
        }
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        Properties previous = pc.getLocalProperties();
        Map<String, Object> parameters = route.getTemplateParameters();
        if (parameters != null) {
            Properties local = new Properties();
            if (previous != null) {
                local.putAll(previous);
            }
            parameters.forEach((k, v) -> {
                if (k != null && v != null) {
                    local.put(k, v);
                }
            });
            pc.setLocalProperties(local);
        }
        try {
            for (String uri : uris) {
                if (uri != null && uri.contains("wsSecurity") && hasWsSecurityOption(camelContext, uri)) {
                    return true;
                }
            }
            return false;
        } finally {
            if (parameters != null) {
                pc.setLocalProperties(previous);
            }
        }
    }

    private static boolean hasWsSecurityOption(CamelContext camelContext, String uri) {
        try {
            uri = camelContext.resolvePropertyPlaceholders(uri);
            if (uri == null || !uri.startsWith("cxf:")) {
                return false;
            }
            Map<String, Object> query = URISupport.parseQuery(URISupport.extractQuery(uri), true, true);
            URISupport.resolveRawParameterValues(query);
            return query.entrySet().stream()
                    .anyMatch(e -> WS_SECURITY_OPTIONS.contains(e.getKey())
                            && e.getValue() != null && !e.getValue().toString().isBlank());
        } catch (Exception e) {
            // a placeholder that cannot be resolved, or not a uri
            return false;
        }
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
