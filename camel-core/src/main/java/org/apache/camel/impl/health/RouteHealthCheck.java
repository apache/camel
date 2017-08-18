/**
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteHealthCheck extends AbstractHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(RouteHealthCheck.class);

    private final Route route;
    private final List<PerformanceCounterEvaluator<ManagedRouteMBean>> evaluators;

    public RouteHealthCheck(Route route) {
        this(route, null);
    }

    public RouteHealthCheck(Route route, Collection<PerformanceCounterEvaluator<ManagedRouteMBean>> evaluators) {
        super("camel", "route:" + route.getId());

        this.route = route;

        if (ObjectHelper.isNotEmpty(evaluators)) {
            this.evaluators = new ArrayList<>(evaluators);
        } else {
            this.evaluators = Collections.emptyList();
        }
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        if (route.getId() != null) {
            final CamelContext context = route.getRouteContext().getCamelContext();
            final ServiceStatus status = context.getRouteStatus(route.getId());

            builder.detail("route.id", route.getId());
            builder.detail("route.status", status.name());
            builder.detail("route.context.name", context.getName());

            if (route.getRouteContext().getRouteController() != null || route.getRouteContext().isAutoStartup()) {
                if (status.isStarted()) {
                    builder.up();
                } else if (status.isStopped()) {
                    builder.down();
                    builder.message(String.format("Route %s has status %s", route.getId(), status.name()));
                }
            } else {
                LOGGER.debug("Route {} marked as UP (controlled={}, auto-startup={})",
                    route.getId(),
                    route.getRouteContext().getRouteController() != null,
                    route.getRouteContext().isAutoStartup()
                );

                // Assuming that if no route controller is configured or if a
                // route is configured to not to automatically start, then the
                // route is always up as it is externally managed.
                builder.up();
            }

            if (builder.state() != State.DOWN) {
                // If JMX is enabled, use the Managed MBeans to determine route
                // health based on performance counters.
                ManagedRouteMBean managedRoute = context.getManagedRoute(route.getId(), ManagedRouteMBean.class);

                if (managedRoute != null && !evaluators.isEmpty()) {
                    Map<String, Object> details = new HashMap<>();

                    for (PerformanceCounterEvaluator evaluator : evaluators) {
                        details.clear();

                        evaluator.test(managedRoute, builder, options);

                        if (builder.state() == State.DOWN) {
                            break;
                        }
                    }
                }
            }
        }
    }
}
