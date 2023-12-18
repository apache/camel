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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.Route;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.backoff.BackOffTimer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole("route-controller")
public class RouteControllerConsole extends AbstractDevConsole {

    public static final String STACKTRACE = "stacktrace";
    public static final String ERROR = "error";

    public RouteControllerConsole() {
        super("camel", "route-controller", "Route Controller", "Route startup information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        boolean includeError = "true".equals(options.getOrDefault(ERROR, "true"));
        boolean includeStacktrace = "true".equals(options.getOrDefault(STACKTRACE, "true"));

        StringBuilder sb = new StringBuilder();

        RouteController rc = getCamelContext().getRouteController();
        if (rc instanceof SupervisingRouteController) {
            SupervisingRouteController src = (SupervisingRouteController) rc;

            Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
            routes.addAll(rc.getControlledRoutes());
            routes.addAll(src.getExhaustedRoutes());
            routes.addAll(src.getRestartingRoutes());
            long started = routes.stream().filter(r -> src.getRouteStatus(r.getRouteId()).isStarted())
                    .count();

            sb.append(String.format("\nInitial Starting Routes: %b", src.isStartingRoutes()));
            sb.append(String.format("\nUnhealthy Routes: %b", src.hasUnhealthyRoutes()));
            sb.append(String.format("Total Routes: %d", routes.size()));
            sb.append(String.format("\nStarted Routes: %d", started));
            sb.append(String.format("\nRestarting Routes: %d", src.getRestartingRoutes().size()));
            sb.append(String.format("\nExhausted Routes: %d", src.getExhaustedRoutes().size()));
            sb.append(String.format("\nInitial Delay: %d", src.getInitialDelay()));
            sb.append(String.format("\nBackoff Delay: %d", src.getBackOffDelay()));
            sb.append(String.format("\nBackoff Max Delay: %d", src.getBackOffMaxDelay()));
            sb.append(String.format("\nBackoff Max Elapsed Time: %d", src.getBackOffMaxElapsedTime()));
            sb.append(String.format("\nBackoff Max Attempts: %d", src.getBackOffMaxAttempts()));
            sb.append(String.format("\nThread Pool Size: %d", src.getThreadPoolSize()));
            sb.append(String.format("\nUnhealthy On Restarting: %b", src.isUnhealthyOnRestarting()));
            sb.append(String.format("\nUnhealthy On Exhaust: %b", src.isUnhealthyOnExhausted()));
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
                        StringWriter writer = new StringWriter();
                        cause.printStackTrace(new PrintWriter(writer));
                        writer.flush();
                        stacktrace = writer.toString();
                    }
                }

                if (supervising != null) {
                    sb.append(String.format("\n    %s %s (%s) ", status, routeId, uri));
                    sb.append(String.format("\n        Supervising: %s", supervising));
                    sb.append(String.format("\n            Attempts: %s", attempts));
                    sb.append(String.format("\n            Last Ago: %s", last));
                    sb.append(String.format("\n            Next Attempt: %s", next));
                    sb.append(String.format("\n            Elapsed: %s", elapsed));
                    if (error != null) {
                        sb.append(String.format("\n            Error: %s", error));
                        if (stacktrace != null) {
                            sb.append(String.format("\n            Stacktrace:\n%s", stacktrace));
                        }
                    }
                } else {
                    sb.append(String.format("\n    %s %s (%s) ", status, routeId, uri));
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
                sb.append(String.format("\n    %s %s (%s)", status, routeId, uri));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        boolean includeError = "true".equals(options.getOrDefault(ERROR, "true"));
        boolean includeStacktrace = "true".equals(options.getOrDefault(STACKTRACE, "true"));

        JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();

        RouteController rc = getCamelContext().getRouteController();
        if (rc instanceof SupervisingRouteController) {
            SupervisingRouteController src = (SupervisingRouteController) rc;

            Set<Route> routes = new TreeSet<>(Comparator.comparing(Route::getId));
            routes.addAll(rc.getControlledRoutes());
            routes.addAll(src.getExhaustedRoutes());
            routes.addAll(src.getRestartingRoutes());
            long started = routes.stream().filter(r -> src.getRouteStatus(r.getRouteId()).isStarted())
                    .count();

            root.put("controller", "SupervisingRouteController");
            root.put("startingRoutes", src.isStartingRoutes());
            root.put("unhealthyRoutes", src.hasUnhealthyRoutes());
            root.put("totalRoutes", routes.size());
            root.put("startedRoutes", started);
            root.put("restartingRoutes", src.getRestartingRoutes().size());
            root.put("exhaustedRoutes", src.getExhaustedRoutes().size());
            root.put("initialDelay", src.getInitialDelay());
            root.put("backoffDelay", src.getBackOffDelay());
            root.put("backoffMaxDelay", src.getBackOffMaxDelay());
            root.put("backoffMaxElapsedTime", src.getBackOffMaxElapsedTime());
            root.put("backoffMaxAttempts", src.getBackOffMaxAttempts());
            root.put("threadPoolSize", src.getThreadPoolSize());
            root.put("unhealthyOnRestarting", src.isUnhealthyOnRestarting());
            root.put("unhealthyOnExhausted", src.isUnhealthyOnExhausted());
            root.put("routes", list);

            for (Route route : routes) {
                String routeId = route.getRouteId();
                String status = rc.getRouteStatus(routeId).name();
                String uri = route.getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);

                BackOffTimer.Task state = src.getRestartingRouteState(routeId);
                String supervising = state != null ? state.getStatus().name() : null;
                long attempts = state != null ? state.getCurrentAttempts() : 0;
                long elapsed;
                long last;
                long next;
                // we can only track elapsed/time for active supervised routes
                elapsed = state != null && BackOffTimer.Task.Status.Active == state.getStatus()
                        ? state.getCurrentElapsedTime() : 0;
                last = state != null && BackOffTimer.Task.Status.Active == state.getStatus() ? state.getLastAttemptTime() : 0;
                next = state != null && BackOffTimer.Task.Status.Active == state.getStatus() ? state.getNextAttemptTime() : 0;
                JsonObject jo = new JsonObject();
                list.add(jo);
                jo.put("routeId", routeId);
                jo.put("status", status);
                jo.put("uri", uri);
                jo.put("attempts", attempts);
                jo.put("lastAttempt", last);
                jo.put("nextAttempt", next);
                jo.put("elapsed", elapsed);
                if (supervising != null) {
                    jo.put("supervising", supervising);
                    Throwable cause = src.getRestartException(routeId);
                    if (includeError && cause != null) {
                        String error = cause.getMessage();
                        jo.put("error", Jsoner.escape(error));
                        if (includeStacktrace) {
                            JsonArray arr2 = new JsonArray();
                            StringWriter writer = new StringWriter();
                            cause.printStackTrace(new PrintWriter(writer));
                            writer.flush();
                            String trace = writer.toString();
                            jo.put("stackTrace", arr2);
                            Collections.addAll(arr2, trace.split("\n"));
                        }
                    }
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

            root.put("controller", "DefaultRouteController");
            root.put("totalRoutes", routes.size());
            root.put("routes", list);
            for (Route route : routes) {
                String routeId = route.getRouteId();
                String status = rc.getRouteStatus(routeId).name();
                String uri = route.getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);

                JsonObject jo = new JsonObject();
                list.add(jo);
                jo.put("routeId", routeId);
                jo.put("status", status);
                jo.put("uri", uri);
            }
        }

        return root;
    }

}
