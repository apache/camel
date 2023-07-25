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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheckResultBuilder;

/**
 * {@link org.apache.camel.health.HealthCheck} for a given route.
 */
public class RouteHealthCheck extends AbstractHealthCheck {

    final Route route;

    public RouteHealthCheck(Route route) {
        this(route, "route:" + route.getId());
    }

    public RouteHealthCheck(Route route, String id) {
        super("camel", id);
        this.route = route;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        if (route.getId() != null) {
            final CamelContext context = route.getCamelContext();
            final ServiceStatus status = context.getRouteController().getRouteStatus(route.getId());

            builder.detail("route.id", route.getId());
            builder.detail("route.status", status.name());

            if (route.getRouteController() != null || route.isAutoStartup()) {
                if (status.isStarted()) {
                    builder.up();
                } else if (status.isStopped()) {
                    builder.down();
                    builder.message(String.format("Route %s has status %s", route.getId(), status.name()));
                }
            } else {
                if (route.getRouteController() == null
                        && Boolean.TRUE == route.getProperties().getOrDefault(Route.SUPERVISED, Boolean.FALSE)) {
                    // the route has no route controller which mean it may be supervised and then failed
                    // all attempts and be exhausted, and if so then we are in unknown status

                    // the supervised route controller would store the last error if the route is regarded
                    // as unhealthy which we will use to signal it is down, otherwise we are in unknown state
                    builder.unknown();
                    if (route.getLastError() != null && route.getLastError().isUnhealthy()) {
                        builder.down();
                    }
                } else if (!route.isAutoStartup()) {
                    // if a route is configured to not to automatically start, then the
                    // route is always up as it is externally managed.
                    builder.up();
                } else {
                    // route in unknown state
                    builder.unknown();
                }
            }
        }

        doCallCheck(builder, options);
    }

    /**
     * Additional checks
     */
    protected void doCallCheck(HealthCheckResultBuilder builder, Map<String, Object> options) {
        // noop
    }

}
