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

package org.apache.camel.test.junit5.util;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.InterceptSendToMockEndpointStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.debugger.DefaultDebugger;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.TestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CamelContextTestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CamelContextTestHelper.class);

    public static CamelContext createCamelContext(Registry registry) throws Exception {
        CamelContext retContext;
        if (registry != null) {
            retContext = new DefaultCamelContext(registry);
        } else {
            retContext = new DefaultCamelContext();
        }

        return retContext;
    }

    public static void setupDebugger(CamelContext context, Breakpoint breakpoint) {
        assert context != null : "You cannot set a debugger on a null context";
        assert breakpoint != null : "You cannot set a debugger using a null debug breakpoint";

        if (context.getStatus().equals(ServiceStatus.Started)) {
            LOG.info("Cannot setting the Debugger to the starting CamelContext, stop the CamelContext now.");
            // we need to stop the context first to setup the debugger
            context.stop();
        }
        context.setDebugging(true);
        final DefaultDebugger defaultDebugger = new DefaultDebugger();
        context.setDebugger(defaultDebugger);

        defaultDebugger.addBreakpoint(breakpoint);
        // when stopping CamelContext it will automatically remove the breakpoint
    }

    /**
     * Configures routes on the given context
     *
     * @param  context   the context to add the routes to
     * @param  builders  an array of route builders
     * @throws Exception
     */
    public static void setupRoutes(CamelContext context, RoutesBuilder[] builders) throws Exception {
        // add configuration before routes
        for (RoutesBuilder builder : builders) {
            if (builder instanceof RouteConfigurationsBuilder) {
                LOG.debug("Using created route configuration: {}", builder);
                context.addRoutesConfigurations((RouteConfigurationsBuilder) builder);
            }
        }
        for (RoutesBuilder builder : builders) {
            LOG.debug("Using created route builder to add routes: {}", builder);
            context.addRoutes(builder);
        }
        for (RoutesBuilder builder : builders) {
            LOG.debug("Using created route builder to add templated routes: {}", builder);
            context.addTemplatedRoutes(builder);
        }
    }

    /**
     * Lookup endpoint with the given URI on the context
     *
     * @param  context the context to lookup endpoints
     * @param  uri     the endpoint URI
     * @param  create  whether to create the endpoint if it does not exist
     * @param  target  the query-stripped normalized URI
     * @return         the MockEndpoint instance associated with the normalized URI (aka target)
     */
    public static MockEndpoint lookupEndpoint(CamelContext context, String uri, boolean create, String target) {
        MockEndpoint found = (MockEndpoint) context.getEndpointRegistry().values().stream()
                .filter(e -> e instanceof MockEndpoint).filter(e -> {
                    String t = e.getEndpointUri();
                    // strip query
                    int idx2 = t.indexOf('?');
                    if (idx2 != -1) {
                        t = t.substring(0, idx2);
                    }
                    return t.equals(target);
                }).findFirst().orElse(null);

        if (found != null) {
            return found;
        }

        if (create) {
            return TestSupport.resolveMandatoryEndpoint(context, uri, MockEndpoint.class);
        } else {
            throw new NoSuchEndpointException(String.format("MockEndpoint %s does not exist.", uri));
        }
    }

    /**
     * Enables auto mocking
     *
     * @param context
     * @param pattern
     * @param mockAndSkipPattern
     */
    public static void enableAutoMocking(CamelContext context, String pattern, String mockAndSkipPattern) {
        if (pattern != null) {
            context.getCamelContextExtension()
                    .registerEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern));
        }
        if (mockAndSkipPattern != null) {
            context.getCamelContextExtension()
                    .registerEndpointCallback(new InterceptSendToMockEndpointStrategy(mockAndSkipPattern, true));
        }
    }

    /**
     * Configure the PropertiesComponent from the given context
     *
     * @param context          the context with the PropertiesComponent to configure
     * @param extra            override properties to use (if any)
     * @param propertiesSource custom properties source to use to load/lookup properties
     * @param ignore           whether to ignore missing properties locations
     */
    public static void configurePropertiesComponent(
            CamelContext context, Properties extra, PropertiesSource propertiesSource, Boolean ignore) {
        PropertiesComponent pc = context.getPropertiesComponent();
        if (extra != null && !extra.isEmpty()) {
            pc.setOverrideProperties(extra);
        }
        pc.addPropertiesSource(propertiesSource);
        if (ignore != null) {
            pc.setIgnoreMissingLocation(ignore);
        }
    }

    /**
     * Configure route filtering patterns
     *
     * @param context the context to configure the patterns
     * @param include the inclusion pattern
     * @param exclude the exclusion pattern
     */
    public static void configureIncludeExcludePatterns(CamelContext context, String include, String exclude) {
        if (include != null || exclude != null) {
            LOG.info("Route filtering pattern: include={}, exclude={}", include, exclude);
            context.getCamelContextExtension().getContextPlugin(Model.class).setRouteFilterPattern(include, exclude);
        }
    }

    /**
     * Start the given context
     *
     * @param  context   the context to start
     * @throws Exception
     */
    public static void startCamelContext(CamelContext context) throws Exception {
        if (context instanceof DefaultCamelContext defaultCamelContext) {
            if (!defaultCamelContext.isStarted()) {
                defaultCamelContext.start();
            }
        } else {
            context.start();
        }
    }

    /**
     * Starts a CamelContext or a service managing the CamelContext. The service takes priority if provided.
     *
     * @param  context             the context to start
     * @param  camelContextService the service managing the CamelContext
     * @throws Exception
     */
    public static void startCamelContextOrService(CamelContext context, Service camelContextService) throws Exception {
        if (camelContextService != null) {
            camelContextService.start();
        } else {
            CamelContextTestHelper.startCamelContext(context);
        }
    }

    /**
     * Replaces the 'from' endpoints of the given context with the ones from the provided map
     *
     * @param  context       the context to have the 'from' endpoints replaced
     * @param  fromEndpoints the map with the new endpoint Uris
     * @throws Exception
     */
    public static void replaceFromEndpoints(ModelCamelContext context, Map<String, String> fromEndpoints) throws Exception {
        for (final Map.Entry<String, String> entry : fromEndpoints.entrySet()) {
            AdviceWith.adviceWith(context.getRouteDefinition(entry.getKey()), context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() {
                    replaceFromWith(entry.getValue());
                }
            });
        }
    }
}
