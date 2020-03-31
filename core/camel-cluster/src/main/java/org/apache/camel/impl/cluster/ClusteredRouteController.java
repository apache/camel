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
package org.apache.camel.impl.cluster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Experimental;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.impl.engine.DefaultRouteController;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.cluster.ClusterServiceHelper;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Experimental
public class ClusteredRouteController extends DefaultRouteController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusteredRouteController.class);

    private final Set<String> routes;
    private final ConcurrentMap<String, ClusteredRouteConfiguration> configurations;
    private final List<ClusteredRouteFilter> filters;
    private final PolicyFactory policyFactory;
    private final ClusteredRouteConfiguration defaultConfiguration;
    private CamelClusterService clusterService;
    private CamelClusterService.Selector clusterServiceSelector;

    public ClusteredRouteController() {
        this.routes = new CopyOnWriteArraySet<>();
        this.configurations = new ConcurrentHashMap<>();
        this.filters = new ArrayList<>();
        this.clusterServiceSelector = ClusterServiceSelectors.DEFAULT_SELECTOR;
        this.policyFactory = new PolicyFactory();

        this.defaultConfiguration = new ClusteredRouteConfiguration();
        this.defaultConfiguration.setInitialDelay(Duration.ofMillis(0));
    }

    // *******************************
    // Properties.
    // *******************************

    /**
     * Add a filter used to to filter cluster aware routes.
     */
    public void addFilter(ClusteredRouteFilter filter) {
        this.filters.add(filter);
    }

    /**
     * Sets the filters used to filter cluster aware routes.
     */
    public void setFilters(Collection<ClusteredRouteFilter> filters) {
        this.filters.clear();
        this.filters.addAll(filters);
    }

    public Collection<ClusteredRouteFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    /**
     * Add a configuration for the given route.
     */
    public void addRouteConfiguration(String routeId, ClusteredRouteConfiguration configuration) {
        configurations.put(routeId, configuration);
    }

    /**
     * Sets the configurations for the routes.
     */
    public void setRoutesConfiguration(Map<String, ClusteredRouteConfiguration> configurations) {
        this.configurations.clear();
        this.configurations.putAll(configurations);
    }

    public Map<String, ClusteredRouteConfiguration> getRoutesConfiguration() {
        return Collections.unmodifiableMap(this.configurations);
    }

    public Duration getInitialDelay() {
        return this.defaultConfiguration.getInitialDelay();
    }

    /**
     * Set the amount of time the route controller should wait before to start
     * the routes after the camel context is started.
     *
     * @param initialDelay the initial delay.
     */
    public void setInitialDelay(Duration initialDelay) {
        this.defaultConfiguration.setInitialDelay(initialDelay);
    }

    public String getNamespace() {
        return this.defaultConfiguration.getNamespace();
    }

    /**
     * Set the default namespace.
     */
    public void setNamespace(String namespace) {
        this.defaultConfiguration.setNamespace(namespace);
    }

    public CamelClusterService getClusterService() {
        return clusterService;
    }

    /**
     * Set the cluster service to use.
     */
    public void setClusterService(CamelClusterService clusterService) {
        ObjectHelper.notNull(clusterService, "CamelClusterService");

        this.clusterService = clusterService;
    }

    public CamelClusterService.Selector getClusterServiceSelector() {
        return clusterServiceSelector;
    }

    /**
     * Set the selector strategy to look-up a {@link CamelClusterService}
     */
    public void setClusterServiceSelector(CamelClusterService.Selector clusterServiceSelector) {
        ObjectHelper.notNull(clusterService, "CamelClusterService.Selector");

        this.clusterServiceSelector = clusterServiceSelector;
    }

    // *******************************
    //
    // *******************************

    @Override
    public Collection<Route> getControlledRoutes() {
        return this.routes.stream().map(getCamelContext()::getRoute).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void doStart() throws Exception {
        final CamelContext context = getCamelContext();

        // Parameters validation
        ObjectHelper.notNull(defaultConfiguration.getNamespace(), "Namespace");
        ObjectHelper.notNull(defaultConfiguration.getInitialDelay(), "initialDelay");
        ObjectHelper.notNull(context, "camelContext");

        if (clusterService == null) {
            // Finally try to grab it from the camel context.
            clusterService = ClusterServiceHelper.mandatoryLookupService(context, clusterServiceSelector);
        }

        LOGGER.debug("Using ClusterService instance {} (id={}, type={})", clusterService, clusterService.getId(), clusterService.getClass().getName());

        if (!ServiceHelper.isStarted(clusterService)) {
            // Start the cluster service if not yet started.
            clusterService.start();
        }

        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        if (ServiceHelper.isStarted(clusterService)) {
            // Stop the cluster service.
            clusterService.stop();
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        if (!camelContext.getRoutePolicyFactories().contains(this.policyFactory)) {
            camelContext.addRoutePolicyFactory(this.policyFactory);
        }

        super.setCamelContext(camelContext);
    }

    // *******************************
    // Route operations are disabled
    // *******************************

    @Override
    public void startRoute(String routeId) throws Exception {
        failIfClustered(routeId);

        // Delegate to default impl.
        super.startRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId) throws Exception {
        failIfClustered(routeId);

        // Delegate to default impl.
        super.stopRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        failIfClustered(routeId);

        // Delegate to default impl.
        super.stopRoute(routeId, timeout, timeUnit);
    }

    @Override
    public boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        failIfClustered(routeId);

        // Delegate to default impl.
        return super.stopRoute(routeId, timeout, timeUnit, abortAfterTimeout);
    }

    @Override
    public void suspendRoute(String routeId) throws Exception {
        failIfClustered(routeId);

        // Delegate to default impl.
        super.suspendRoute(routeId);
    }

    @Override
    public void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        failIfClustered(routeId);

        // Delegate to default impl.
        super.suspendRoute(routeId, timeout, timeUnit);
    }

    @Override
    public void resumeRoute(String routeId) throws Exception {
        failIfClustered(routeId);

        // Delegate to default impl.
        super.resumeRoute(routeId);
    }

    // *******************************
    // Helpers
    // *******************************

    private void failIfClustered(String routeId) {
        // Can't perform action on routes managed by this controller as they
        // are clustered and they may be part of the same view.
        if (routes.contains(routeId)) {
            throw new UnsupportedOperationException("Operation not supported as route " + routeId + " is clustered");
        }
    }

    // *******************************
    // Factories
    // *******************************

    private final class PolicyFactory implements RoutePolicyFactory {
        @Override
        public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode node) {
            RouteDefinition route = (RouteDefinition)node;
            // All the filter have to be match to include the route in the
            // clustering set-up
            if (filters.stream().allMatch(filter -> filter.test(camelContext, routeId, route))) {

                if (ObjectHelper.isNotEmpty(route.getRoutePolicies())) {
                    // Check if the route is already configured with a clustered
                    // route policy, in that case exclude it.
                    if (route.getRoutePolicies().stream().anyMatch(ClusteredRoutePolicy.class::isInstance)) {
                        LOGGER.debug("Route '{}' has a ClusteredRoutePolicy already set-up", routeId);
                        return null;
                    }
                }

                try {
                    final ClusteredRouteConfiguration configuration = configurations.getOrDefault(routeId, defaultConfiguration);
                    final String namespace = ObjectHelper.supplyIfEmpty(configuration.getNamespace(), defaultConfiguration::getNamespace);
                    final Duration initialDelay = ObjectHelper.supplyIfEmpty(configuration.getInitialDelay(), defaultConfiguration::getInitialDelay);

                    ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace(clusterService, namespace);
                    policy.setCamelContext(getCamelContext());
                    policy.setInitialDelay(initialDelay);

                    LOGGER.debug("Attaching route '{}' to namespace '{}'", routeId, namespace);

                    routes.add(routeId);

                    return policy;
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }

            return null;
        }
    }
}
