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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.Route;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.backoff.BackOffTimer;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "route-controller", description = "Route controller information")
public class RouteControllerConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Whether to include stack traces", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String STACKTRACE = "stacktrace";
    @Metadata(label = "query", description = "Whether to include error details", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String ERROR = "error";

    public record RouteEntry(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The route status") String status,
            @Metadata(description = "The route endpoint URI (sanitized)") String uri,
            @Metadata(description = "Number of restart attempts (supervising controller only)") Long attempts,
            @Metadata(description = "Epoch time in milliseconds of the last restart attempt (supervising controller only)") Long lastAttempt,
            @Metadata(description = "Epoch time in milliseconds of the next restart attempt (supervising controller only)") Long nextAttempt,
            @Metadata(description = "Elapsed time in milliseconds of the current restart attempt (supervising controller only)") Long elapsed,
            @Metadata(description = "The supervising status (supervising controller only)") String supervising,
            @Metadata(description = "The restart error message (only present when the route is failing)") String error,
            @Metadata(description = "The restart error stack trace, one entry per line (only present when the route is failing and stack traces are requested)") List<String> stackTrace) {
    }

    public record Response(
            @Metadata(description = "The route controller implementation: SupervisingRouteController or DefaultRouteController") String controller,
            @Metadata(description = "Whether routes are still starting (supervising controller only)") Boolean startingRoutes,
            @Metadata(description = "Whether any route is unhealthy (supervising controller only)") Boolean unhealthyRoutes,
            @Metadata(description = "Total number of routes") int totalRoutes,
            @Metadata(description = "Number of started routes (supervising controller only)") Long startedRoutes,
            @Metadata(description = "Number of restarting routes (supervising controller only)") Integer restartingRoutes,
            @Metadata(description = "Number of exhausted routes (supervising controller only)") Integer exhaustedRoutes,
            @Metadata(description = "Initial delay in milliseconds before restarting (supervising controller only)") Long initialDelay,
            @Metadata(description = "Backoff delay in milliseconds (supervising controller only)") Long backoffDelay,
            @Metadata(description = "Maximum backoff delay in milliseconds (supervising controller only)") Long backoffMaxDelay,
            @Metadata(description = "Maximum elapsed time in milliseconds before giving up (supervising controller only)") Long backoffMaxElapsedTime,
            @Metadata(description = "Maximum number of restart attempts (supervising controller only)") Long backoffMaxAttempts,
            @Metadata(description = "The size of the restart thread pool (supervising controller only)") Integer threadPoolSize,
            @Metadata(description = "Whether a restarting route is considered unhealthy (supervising controller only)") Boolean unhealthyOnRestarting,
            @Metadata(description = "Whether an exhausted route is considered unhealthy (supervising controller only)") Boolean unhealthyOnExhausted,
            @Metadata(description = "The routes") List<RouteEntry> routes) {
    }

    public RouteControllerConsole() {
        super("camel", "route-controller", "Route Controller", "Route controller information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        boolean includeError = optionBoolean(options, ERROR, true);
        boolean includeStacktrace = optionBoolean(options, STACKTRACE, true);

        StringBuilder sb = new StringBuilder();

        RouteController rc = getCamelContext().getRouteController();
        if (rc instanceof SupervisingRouteController src) {
            Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
            routes.addAll(rc.getControlledRoutes());
            routes.addAll(src.getExhaustedRoutes());
            routes.addAll(src.getRestartingRoutes());
            long started = routes.stream().filter(r -> src.getRouteStatus(r.getRouteId()).isStarted())
                    .count();

            sb.append(String.format("%nInitial Starting Routes: %b", src.isStartingRoutes()));
            sb.append(String.format("%nUnhealthy Routes: %b", src.hasUnhealthyRoutes()));
            sb.append(String.format("Total Routes: %d", routes.size()));
            sb.append(String.format("%nStarted Routes: %d", started));
            sb.append(String.format("%nRestarting Routes: %d", src.getRestartingRoutes().size()));
            sb.append(String.format("%nExhausted Routes: %d", src.getExhaustedRoutes().size()));
            sb.append(String.format("%nInitial Delay: %d", src.getInitialDelay()));
            sb.append(String.format("%nBackoff Delay: %d", src.getBackOffDelay()));
            sb.append(String.format("%nBackoff Max Delay: %d", src.getBackOffMaxDelay()));
            sb.append(String.format("%nBackoff Max Elapsed Time: %d", src.getBackOffMaxElapsedTime()));
            sb.append(String.format("%nBackoff Max Attempts: %d", src.getBackOffMaxAttempts()));
            sb.append(String.format("%nThread Pool Size: %d", src.getThreadPoolSize()));
            sb.append(String.format("%nUnhealthy On Restarting: %b", src.isUnhealthyOnRestarting()));
            sb.append(String.format("%nUnhealthy On Exhaust: %b", src.isUnhealthyOnExhausted()));
            sb.append("\n\nRoutes:\n");

            for (Route route : routes) {
                String routeId = route.getRouteId();
                String status = src.getRouteStatus(routeId).name();
                String uri = route.getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);

                BackOffTimer.Task state = src.getRestartingRouteState(routeId);
                String supervising = state != null ? state.getStatus().name() : null;
                long attempts = state != null ? state.getCurrentAttempts() : 0;
                String elapsed = "";
                String last = "";
                String next = "";
                // we can only track elapsed/time for active supervised routes
                long time = state != null && BackOffTimer.Task.Status.Active == state.getStatus()
                        ? state.getFirstAttemptTime() : 0;
                if (time > 0) {
                    elapsed = TimeUtils.printDuration(time);
                }
                time = state != null && BackOffTimer.Task.Status.Active == state.getStatus() ? state.getLastAttemptTime() : 0;
                if (time > 0) {
                    last = TimeUtils.printSince(time);
                }
                time = state != null && BackOffTimer.Task.Status.Active == state.getStatus() ? state.getNextAttemptTime() : 0;
                if (time > 0) {
                    next = TimeUtils.printSince(time);
                }
                String error = null;
                String stacktrace = null;
                Throwable cause = src.getRestartException(routeId);
                if (includeError && cause != null) {
                    error = cause.getMessage();
                    if (includeStacktrace) {
                        stacktrace = ExceptionHelper.stackTraceToString(cause);
                    }
                }

                if (supervising != null) {
                    sb.append(String.format("%n    %s %s (%s) ", status, routeId, uri));
                    sb.append(String.format("%n        Supervising: %s", supervising));
                    sb.append(String.format("%n            Attempts: %s", attempts));
                    sb.append(String.format("%n            Last: %s", last));
                    sb.append(String.format("%n            Next Attempt: %s", next));
                    sb.append(String.format("%n            Elapsed: %s", elapsed));
                    if (error != null) {
                        sb.append(String.format("%n            Error: %s", error));
                        if (stacktrace != null) {
                            sb.append(String.format("%n            Stacktrace:%n%s", stacktrace));
                        }
                    }
                } else {
                    sb.append(String.format("%n    %s %s (%s) ", status, routeId, uri));
                }
            }
        } else {
            Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
            routes.addAll(rc.getControlledRoutes());
            if (routes.isEmpty()) {
                // default route controller does not control routes but let's then just grab
                // routes from context, so we have that to show
                routes.addAll(getCamelContext().getRoutes());
            }
            sb.append(String.format("Total Routes: %d", routes.size()));
            sb.append("\nRoutes:\n");
            for (Route route : routes) {
                String routeId = route.getRouteId();
                String status = rc.getRouteStatus(routeId).name();
                String uri = route.getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);
                sb.append(String.format("%n    %s %s (%s)", status, routeId, uri));
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        boolean includeError = optionBoolean(options, ERROR, true);
        boolean includeStacktrace = optionBoolean(options, STACKTRACE, true);

        RouteController rc = getCamelContext().getRouteController();
        Response response;
        if (rc instanceof SupervisingRouteController src) {
            Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
            routes.addAll(rc.getControlledRoutes());
            routes.addAll(src.getExhaustedRoutes());
            routes.addAll(src.getRestartingRoutes());
            long started = routes.stream().filter(r -> src.getRouteStatus(r.getRouteId()).isStarted())
                    .count();

            List<RouteEntry> list = new ArrayList<>();
            for (Route route : routes) {
                String routeId = route.getRouteId();
                String status = rc.getRouteStatus(routeId).name();
                String uri = URISupport.sanitizeUri(route.getEndpoint().getEndpointBaseUri());

                BackOffTimer.Task state = src.getRestartingRouteState(routeId);
                String supervising = state != null ? state.getStatus().name() : null;
                long attempts = state != null ? state.getCurrentAttempts() : 0;
                // we can only track elapsed/time for active supervised routes
                boolean active = state != null && BackOffTimer.Task.Status.Active == state.getStatus();
                long elapsed = active ? state.getCurrentElapsedTime() : 0;
                long last = active ? state.getLastAttemptTime() : 0;
                long next = active ? state.getNextAttemptTime() : 0;

                String error = null;
                List<String> stackTrace = null;
                if (supervising != null) {
                    Throwable cause = src.getRestartException(routeId);
                    if (includeError && cause != null) {
                        error = Jsoner.escape(cause.getMessage());
                        if (includeStacktrace) {
                            final String trace = ExceptionHelper.stackTraceToString(cause);
                            stackTrace = Arrays.asList(trace.split("\n"));
                        }
                    }
                }

                list.add(new RouteEntry(
                        routeId, status, uri, attempts, last, next, elapsed, supervising, error, stackTrace));
            }

            response = new Response(
                    "SupervisingRouteController", src.isStartingRoutes(), src.hasUnhealthyRoutes(), routes.size(), started,
                    src.getRestartingRoutes().size(), src.getExhaustedRoutes().size(), src.getInitialDelay(),
                    src.getBackOffDelay(), src.getBackOffMaxDelay(), src.getBackOffMaxElapsedTime(),
                    src.getBackOffMaxAttempts(), src.getThreadPoolSize(), src.isUnhealthyOnRestarting(),
                    src.isUnhealthyOnExhausted(), list);
        } else {
            Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
            routes.addAll(rc.getControlledRoutes());
            if (routes.isEmpty()) {
                // default route controller does not control routes but let's then just grab
                // routes from context, so we have that to show
                routes.addAll(getCamelContext().getRoutes());
            }

            List<RouteEntry> list = new ArrayList<>();
            for (Route route : routes) {
                String routeId = route.getRouteId();
                String status = rc.getRouteStatus(routeId).name();
                String uri = URISupport.sanitizeUri(route.getEndpoint().getEndpointBaseUri());
                list.add(new RouteEntry(routeId, status, uri, null, null, null, null, null, null, null));
            }

            response = new Response(
                    "DefaultRouteController", null, null, routes.size(), null, null, null, null, null, null, null, null,
                    null, null, null, list);
        }

        return JsonRecordSupport.toJsonObject(response);
    }

}
