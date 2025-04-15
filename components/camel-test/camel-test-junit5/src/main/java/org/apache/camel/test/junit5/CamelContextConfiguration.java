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
package org.apache.camel.test.junit5;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.EndpointHelper;

/**
 * Configures a context for test execution
 */
public class CamelContextConfiguration {
    @FunctionalInterface
    public interface CamelContextSupplier {
        CamelContext createCamelContext() throws Exception;
    }

    @FunctionalInterface
    public interface RegistryBinder {
        void bindToRegistry(Registry registry) throws Exception;
    }

    @FunctionalInterface
    public interface RoutesSupplier {
        RoutesBuilder[] createRouteBuilders() throws Exception;
    }

    @FunctionalInterface
    public interface PostProcessor {
        void postSetup() throws Exception;
    }

    private String routeFilterIncludePattern;
    private String routeFilterExcludePattern;
    private Registry registry;
    private Breakpoint breakpoint;
    private String mockEndpoints;
    private String mockEndpointsAndSkip;
    private String stubEndpoints;
    private String autoStartupExcludePatterns;
    private Properties useOverridePropertiesWithPropertiesComponent;
    private Boolean ignoreMissingLocationWithPropertiesComponent;

    private CamelContextSupplier camelContextSupplier;
    private RegistryBinder registryBinder;
    private Service camelContextService;

    private int shutdownTimeout = 10;

    private RoutesSupplier routesSupplier;
    private final Map<String, String> fromEndpoints = new HashMap<>();
    private PostProcessor postProcessor;

    public String routeFilterIncludePattern() {
        return routeFilterIncludePattern;
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example, to only include routes which start with foo in their route id's, use: include=foo&#42; And to
     * exclude routes which start from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example, to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    public CamelContextConfiguration withRouteFilterIncludePattern(String routeFilterIncludePattern) {
        this.routeFilterIncludePattern = routeFilterIncludePattern;
        return this;
    }

    public String routeFilterExcludePattern() {
        return routeFilterExcludePattern;
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example, to only include routes which starts with foo in their route id's, use: include=foo&#42; And to
     * exclude routes which start from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example, to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    public CamelContextConfiguration withRouteFilterExcludePattern(String routeFilterExcludePattern) {
        this.routeFilterExcludePattern = routeFilterExcludePattern;
        return this;
    }

    public Registry registry() {
        return registry;
    }

    /**
     * Sets a custom {@link Registry}.
     * <p>
     * However, if you need to bind beans to the registry, then this is possible already with the bind method on
     * registry, and there is no need to use this method.
     */
    public CamelContextConfiguration withRegistry(Registry registry) {
        this.registry = registry;
        return this;
    }

    public boolean useDebugger() {
        return this.breakpoint != null;
    }

    public Breakpoint breakpoint() {
        return breakpoint;
    }

    /**
     * Provides a debug breakpoint to be executed before and/or after entering processors
     *
     * @param breakpoint a new debug breakpoint
     */
    public CamelContextConfiguration withBreakpoint(DebugBreakpoint breakpoint) {
        this.breakpoint = breakpoint;
        return this;
    }

    public String mockEndpoints() {
        return mockEndpoints;
    }

    /**
     * Enables auto mocking endpoints based on the pattern.
     * <p/>
     * Use <tt>*</tt> to mock all endpoints.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    public CamelContextConfiguration withMockEndpoints(String mockEndpoints) {
        this.mockEndpoints = mockEndpoints;
        return this;
    }

    public String mockEndpointsAndSkip() {
        return mockEndpointsAndSkip;
    }

    /**
     * Enables auto mocking endpoints based on the pattern, and <b>skip</b> sending to original endpoint.
     * <p/>
     * Use <tt>*</tt> to mock all endpoints.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    public CamelContextConfiguration withMockEndpointsAndSkip(String mockEndpointsAndSkip) {
        this.mockEndpointsAndSkip = mockEndpointsAndSkip;
        return this;
    }

    public String stubEndpoints() {
        return stubEndpoints;
    }

    /**
     * Enables auto stub endpoints based on the pattern.
     * <p/>
     * Use <tt>*</tt> to stub all endpoints.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    public CamelContextConfiguration withStubEndpoints(String pattern) {
        this.stubEndpoints = pattern;
        return this;
    }

    public String autoStartupExcludePatterns() {
        return autoStartupExcludePatterns;
    }

    /**
     * Used for exclusive filtering of routes to not automatically start with Camel starts.
     *
     * The pattern support matching by route id or endpoint urls.
     *
     * Multiple patterns can be specified separated by comma, as example, to exclude all the routes starting from kafka
     * or jms use: kafka,jms.
     */
    public CamelContextConfiguration withAutoStartupExcludePatterns(String pattern) {
        this.autoStartupExcludePatterns = pattern;
        return this;
    }

    public Properties useOverridePropertiesWithPropertiesComponent() {
        return useOverridePropertiesWithPropertiesComponent;
    }

    /**
     * To include and override properties with the Camel {@link PropertiesComponent}.
     *
     * @param useOverridePropertiesWithPropertiesComponent additional properties to add/override.
     */
    public CamelContextConfiguration withUseOverridePropertiesWithPropertiesComponent(
            Properties useOverridePropertiesWithPropertiesComponent) {
        this.useOverridePropertiesWithPropertiesComponent = useOverridePropertiesWithPropertiesComponent;
        return this;
    }

    public Boolean ignoreMissingLocationWithPropertiesComponent() {
        return ignoreMissingLocationWithPropertiesComponent;
    }

    /**
     * Whether to ignore missing locations with the {@link PropertiesComponent}. For example, when unit testing, you may
     * want to ignore locations that are not available in the environment used for testing.
     *
     * @param ignoreMissingLocationWithPropertiesComponent Use <tt>true</tt> to ignore, <tt>false</tt> to not ignore,
     *                                                     and <tt>null</tt> to leave it as configured on the
     *                                                     {@link PropertiesComponent}
     */
    public CamelContextConfiguration withIgnoreMissingLocationWithPropertiesComponent(
            Boolean ignoreMissingLocationWithPropertiesComponent) {
        this.ignoreMissingLocationWithPropertiesComponent = ignoreMissingLocationWithPropertiesComponent;
        return this;
    }

    public CamelContextSupplier camelContextSupplier() {
        return camelContextSupplier;
    }

    /**
     * To set a supplier for the CamelContext
     *
     * @param camelContextSupplier A supplier for the Camel context
     */
    public CamelContextConfiguration withCamelContextSupplier(
            CamelContextSupplier camelContextSupplier) {
        this.camelContextSupplier = camelContextSupplier;
        return this;
    }

    public RegistryBinder registryBinder() {
        return registryBinder;
    }

    /**
     * A supplier to create a custom {@link Registry}.
     * <p>
     * Do not use it for binding beans to the registry.
     */
    public CamelContextConfiguration withRegistryBinder(
            RegistryBinder registryBinder) {
        this.registryBinder = registryBinder;
        return this;
    }

    public int shutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Sets the timeout to use when shutting down (unit in seconds).
     * <p/>
     * By default, it uses 10 seconds.
     *
     * @param shutdownTimeout the timeout to use
     */
    public CamelContextConfiguration withShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
        return this;
    }

    public Service camelContextService() {
        return camelContextService;
    }

    /**
     * Allows a service to be registered a separate lifecycle service to start and stop the context; such as for Spring
     * when the ApplicationContext is started and stopped, rather than directly stopping the CamelContext
     */
    public CamelContextConfiguration withCamelContextService(Service camelContextService) {
        this.camelContextService = camelContextService;
        return this;
    }

    public RoutesSupplier routesSupplier() {
        return routesSupplier;
    }

    /**
     * A supplier that classes can use to create a {@link RouteBuilder} to define the routes for testing
     */
    protected CamelContextConfiguration withRoutesSupplier(
            RoutesSupplier routesSupplier) {
        this.routesSupplier = routesSupplier;
        return this;
    }

    public Map<String, String> fromEndpoints() {
        return fromEndpoints;
    }

    /**
     * To replace from routes with a different one
     *
     * @param routeId
     * @param fromEndpoint
     */
    public void replaceRouteFromWith(String routeId, String fromEndpoint) {
        fromEndpoints.put(routeId, fromEndpoint);
    }

    public PostProcessor postProcessor() {
        return postProcessor;
    }

    /**
     * Set a custom post-test processor
     *
     * @param postProcessor the post-test processor to use
     */
    protected CamelContextConfiguration withPostProcessor(
            PostProcessor postProcessor) {
        this.postProcessor = postProcessor;
        return this;
    }
}
