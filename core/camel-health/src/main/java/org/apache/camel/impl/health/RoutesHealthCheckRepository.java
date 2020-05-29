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
package org.apache.camel.impl.health;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.Route;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.spi.annotations.JdkService;

/**
 * Repository for routes {@link HealthCheck}s.
 */
@JdkService("routes-health-check-repository")
@DeferredContextBinding
public class RoutesHealthCheckRepository implements CamelContextAware, HealthCheckRepository {
    private final ConcurrentMap<Route, HealthCheck> checks;
    private Set<String> blacklist;
    private volatile CamelContext context;

    public RoutesHealthCheckRepository() {
        this.checks = new ConcurrentHashMap<>();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.context = camelContext;
    }

    @Override
    public String getId() {
        return "routes";
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    public void setBlacklistedRoutes(Collection<String> blacklistedRoutes) {
        blacklistedRoutes.forEach(this::addBlacklistedRoute);
    }

    public void addBlacklistedRoute(String routeId) {
        if (this.blacklist == null) {
            this.blacklist = new HashSet<>();
        }

        this.blacklist.add(routeId);
    }

    @Override
    public Stream<HealthCheck> stream() {
        // This is not really efficient as getRoutes() creates a copy of the routes
        // array for each invocation. It would be nice to have more stream oriented
        // operation on CamelContext i.e.
        //
        // interface CamelContext {
        //
        //     Stream<Route> routes();
        //
        //     void forEachRoute(Consumer<Route> consumer);
        // }
        //
        return this.context != null
            ? this.context.getRoutes()
                .stream()
                .filter(route -> route.getId() != null)
                .filter(route -> isNotBlacklisted(route))
                .map(this::toRouteHealthCheck)
            : Stream.empty();
    }

    // *****************************
    // Helpers
    // *****************************

    private boolean isNotBlacklisted(Route route) {
        return this.blacklist == null || !this.blacklist.contains(route.getId());
    }

    private HealthCheck toRouteHealthCheck(Route route) {
        return checks.computeIfAbsent(route, r -> new RouteHealthCheck(route));
    }

}
