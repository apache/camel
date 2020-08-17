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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.Route;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PatternHelper;

/**
 * Repository for routes {@link HealthCheck}s.
 */
@JdkService("routes-health-check-repository")
@DeferredContextBinding
public class RoutesHealthCheckRepository implements CamelContextAware, HealthCheckRepository {
    private final ConcurrentMap<Route, HealthCheck> checks;
    private volatile CamelContext context;
    private Map<String, HealthCheckConfiguration> configurations;
    private HealthCheckConfiguration fallbackConfiguration;
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
    public Map<String, HealthCheckConfiguration> getConfigurations() {
        return configurations;
    }

    @Override
    public void setConfigurations(Map<String, HealthCheckConfiguration> configurations) {
        this.configurations = configurations;
    }

    @Override
    public void addConfiguration(String id, HealthCheckConfiguration configuration) {
        if ("*".equals(id)) {
            fallbackConfiguration = configuration;
        } else {
            if (configurations == null) {
                configurations = new LinkedHashMap<>();
            }
            configurations.put(id, configuration);
        }
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
            HealthCheckConfiguration hcc = matchConfiguration(route.getRouteId());
            if (hcc != null) {
                rhc.setConfiguration(hcc);
            }
            return rhc;
        });
    }

    private HealthCheckConfiguration matchConfiguration(String id) {
        if (configurations != null) {
            for (String key : configurations.keySet()) {
                if (PatternHelper.matchPattern(id, key)) {
                    return configurations.get(key);
                }
            }
        }
        return fallbackConfiguration;
    }

}
