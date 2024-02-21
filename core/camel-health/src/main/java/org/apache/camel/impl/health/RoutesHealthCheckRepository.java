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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.StaticService;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Repository for routes {@link HealthCheck}s.
 */
@org.apache.camel.spi.annotations.HealthCheck("routes-repository")
@DeferredContextBinding
public class RoutesHealthCheckRepository extends ServiceSupport
        implements CamelContextAware, HealthCheckRepository, StaticService, NonManagedService {

    private final ConcurrentMap<Route, HealthCheck> checks;
    private volatile CamelContext context;
    private boolean enabled = true;

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

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        return this.context != null && enabled
                ? this.context.getRoutes()
                        .stream()
                        .filter(route -> route.getId() != null)
                        .map(this::toRouteHealthCheck)
                : Stream.empty();
    }

    // *****************************
    // Helpers
    // *****************************

    private HealthCheck toRouteHealthCheck(Route route) {
        return checks.computeIfAbsent(route, r -> {
            RouteHealthCheck rhc = new RouteHealthCheck(route);
            CamelContextAware.trySetCamelContext(rhc, route.getCamelContext());
            return rhc;
        });
    }

}
