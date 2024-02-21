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

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.Ordered;
import org.apache.camel.Route;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.backoff.BackOffTimer;

/**
 * Readiness {@link org.apache.camel.health.HealthCheck} for route controller.
 */
@org.apache.camel.spi.annotations.HealthCheck("route-controller-check")
public class RouteControllerHealthCheck extends AbstractHealthCheck {

    public RouteControllerHealthCheck() {
        super("camel", "route-controller");
    }

    @Override
    public int getOrder() {
        // controller should be early
        return Ordered.HIGHEST + 1000;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        boolean up = false;

        RouteController rc = getCamelContext().getRouteController();
        if (rc != null) {
            // should only be up if there are no unhealthy routes
            up = !rc.hasUnhealthyRoutes();
            // do we have any details about why we are not up
            if (!up && rc instanceof SupervisingRouteController src) {
                Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
                routes.addAll(src.getRestartingRoutes());
                for (Route route : routes) {
                    builderDetails(builder, src, route, false);
                }
                routes = new TreeSet<>(Comparator.comparing(Route::getId));
                routes.addAll(src.getExhaustedRoutes());
                for (Route route : routes) {
                    builderDetails(builder, src, route, true);
                }
            }
        }

        if (up) {
            builder.up();
        } else {
            builder.detail("route.controller", "Starting routes in progress");
            builder.down();
        }
    }

    private void builderDetails(
            HealthCheckResultBuilder builder, SupervisingRouteController src, Route route, boolean exhausted) {
        String routeId = route.getRouteId();
        Throwable cause = src.getRestartException(routeId);
        if (cause != null) {
            String status = src.getRouteStatus(routeId).name();
            String uri = route.getEndpoint().getEndpointBaseUri();
            uri = URISupport.sanitizeUri(uri);

            BackOffTimer.Task state = src.getRestartingRouteState(routeId);
            long attempts = state != null ? state.getCurrentAttempts() : 0;
            long elapsed;
            long last;
            long next;
            // we can only track elapsed/time for active supervised routes
            elapsed = state != null && BackOffTimer.Task.Status.Active == state.getStatus()
                    ? state.getCurrentElapsedTime() : 0;
            last = state != null && BackOffTimer.Task.Status.Active == state.getStatus()
                    ? state.getLastAttemptTime() : 0;
            next = state != null && BackOffTimer.Task.Status.Active == state.getStatus()
                    ? state.getNextAttemptTime() : 0;

            String key = "route." + routeId;
            builder.detail(key + ".id", routeId);
            builder.detail(key + ".status", status);
            builder.detail(key + ".phase", exhausted ? "Exhausted" : "Restarting");
            builder.detail(key + ".uri", uri);
            builder.detail(key + ".attempts", attempts);
            builder.detail(key + ".lastAttempt", last);
            builder.detail(key + ".nextAttempt", next);
            builder.detail(key + ".elapsed", elapsed);
            if (cause.getMessage() != null) {
                builder.detail(key + ".error", cause.getMessage());
                // only one exception can be stored so lets just store first found
                if (builder.error() == null) {
                    builder.error(cause);
                    String msg;
                    if (exhausted) {
                        msg = String.format("Restarting route: %s is exhausted after %s attempts due %s."
                                            + " No more attempts will be made and the route is no longer supervised by this route controller and remains as stopped.",
                                routeId, attempts,
                                cause.getMessage());
                    } else {
                        msg = String.format("Failed restarting route: %s attempt: %s due: %s", routeId, attempts,
                                cause.getMessage());
                    }
                    builder.message(msg);
                }
            }
        }
    }

}
