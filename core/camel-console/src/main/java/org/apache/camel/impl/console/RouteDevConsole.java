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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("route")
public class RouteDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes matching by route id, route uri, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    public RouteDevConsole() {
        super("camel", "route", "Route", "Route information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(String.format("    Id: %s", mrb.getRouteId()));
            sb.append(String.format("\n    From: %s", mrb.getEndpointUri()));
            if (mrb.getSourceLocation() != null) {
                sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
            }
            sb.append(String.format("\n    State: %s", mrb.getState()));
            sb.append(String.format("\n    Uptime: %s", mrb.getUptime()));
            String load1 = getLoad1(mrb);
            String load5 = getLoad5(mrb);
            String load15 = getLoad15(mrb);
            if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                sb.append(String.format("\n    Load Average: %s %s %s\n", load1, load5, load15));
            }
            sb.append(String.format("\n    Total: %s", mrb.getExchangesTotal()));
            sb.append(String.format("\n    Failed: %s", mrb.getExchangesFailed()));
            sb.append(String.format("\n    Inflight: %s", mrb.getExchangesInflight()));
            sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mrb.getMeanProcessingTime(), true)));
            sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mrb.getMaxProcessingTime(), true)));
            sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mrb.getMinProcessingTime(), true)));
            Date last = mrb.getLastExchangeCreatedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("\n    Since Last Started: %s", ago));
            }
            last = mrb.getLastExchangeCompletedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("\n    Since Last Completed: %s", ago));
            }
            last = mrb.getLastExchangeFailureTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("\n    Since Last Failed: %s", ago));
            }
            sb.append("\n");
            return null;
        };
        doCall(options, task);
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();

        Function<ManagedRouteMBean, Object> task = mrb -> {
            JsonObject jo = new JsonObject();
            list.add(jo);
            jo.put("routeId", mrb.getRouteId());
            jo.put("from", mrb.getEndpointUri());
            if (mrb.getSourceLocation() != null) {
                jo.put("source", mrb.getSourceLocation());
            }
            jo.put("state", mrb.getState());
            jo.put("uptime", mrb.getUptime());
            JsonObject stats = new JsonObject();
            String load1 = getLoad1(mrb);
            String load5 = getLoad5(mrb);
            String load15 = getLoad15(mrb);
            if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                stats.put("load01", load1);
                stats.put("load05", load5);
                stats.put("load15", load15);
            }
            stats.put("exchangesTotal", mrb.getExchangesTotal());
            stats.put("exchangesFailed", mrb.getExchangesFailed());
            stats.put("exchangesInflight", mrb.getExchangesInflight());
            stats.put("meanProcessingTime", mrb.getMeanProcessingTime());
            stats.put("maxProcessingTime", mrb.getMaxProcessingTime());
            stats.put("minProcessingTime", mrb.getMinProcessingTime());
            Date last = mrb.getLastExchangeCreatedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                stats.put("sinceLastCreatedExchange", ago);
            }
            last = mrb.getLastExchangeCompletedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                stats.put("sinceLastCompletedExchange", ago);
            }
            last = mrb.getLastExchangeFailureTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                stats.put("sinceLastFailedExchange", ago);
            }
            jo.put("statistics", stats);
            return null;
        };
        doCall(options, task);
        root.put("routes", list);
        return root;
    }

    protected void doCall(Map<String, Object> options, Function<ManagedRouteMBean, Object> task) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        ManagedCamelContext mcc = getCamelContext().getExtension(ManagedCamelContext.class);
        if (mcc != null) {
            List<Route> routes = getCamelContext().getRoutes();
            routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
            routes.stream()
                    .map(route -> mcc.getManagedRoute(route.getRouteId()))
                    .filter(r -> accept(r, filter))
                    .filter(r -> accept(r, subPath))
                    .sorted(RouteDevConsole::sort)
                    .limit(max)
                    .forEach(task::apply);
        }
    }

    private static boolean accept(ManagedRouteMBean mrb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getEndpointUri(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocationShort(), filter);
    }

    private static int sort(ManagedRouteMBean o1, ManagedRouteMBean o2) {
        // sort by id
        return o1.getRouteId().compareTo(o2.getRouteId());
    }

    private String getLoad1(ManagedRouteMBean mrb) {
        String s = mrb.getLoad01();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getLoad5(ManagedRouteMBean mrb) {
        String s = mrb.getLoad05();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getLoad15(ManagedRouteMBean mrb) {
        String s = mrb.getLoad15();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

}
