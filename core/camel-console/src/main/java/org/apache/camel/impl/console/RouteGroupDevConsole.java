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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteGroupMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "route-group", description = "Route Group information")
public class RouteGroupDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(RouteGroupDevConsole.class);

    /**
     * Filters the route groups matching by group id
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    /**
     * Action to perform such as start, or stop
     */
    public static final String ACTION = "action";

    public RouteGroupDevConsole() {
        super("camel", "route-group", "Route Group", "Route Group information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String action = (String) options.get(ACTION);
        String filter = (String) options.get(FILTER);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteGroupMBean, Object> task = mrg -> {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(String.format("    Group: %s", mrg.getRouteGroup()));
            sb.append(String.format("%n    Size: %s", mrg.getGroupSize()));
            sb.append(String.format("%n    State: %s", mrg.getState()));
            sb.append(String.format("%n    Uptime: %s", mrg.getUptime()));
            String coverage = calculateRouteCoverage(mrg, true);
            if (coverage != null) {
                sb.append(String.format("%n    Coverage: %s", coverage));
            }
            String load1 = getLoad1(mrg);
            String load5 = getLoad5(mrg);
            String load15 = getLoad15(mrg);
            if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                sb.append(String.format("%n    Load Average: %s %s %s", load1, load5, load15));
            }
            String thp = getThroughput(mrg);
            if (!thp.isEmpty()) {
                sb.append(String.format("%n    Messages/Sec: %s", thp));
            }
            sb.append(String.format("%n    Total: %s", mrg.getExchangesTotal()));
            sb.append(String.format("%n    Failed: %s", mrg.getExchangesFailed()));
            sb.append(String.format("%n    Inflight: %s", mrg.getExchangesInflight()));
            long idle = mrg.getIdleSince();
            if (idle > 0) {
                sb.append(String.format("%n    Idle Since: %s", TimeUtils.printDuration(idle)));
            } else {
                sb.append(String.format("%n    Idle Since: %s", ""));
            }
            sb.append(String.format("%n    Mean Time: %s", TimeUtils.printDuration(mrg.getMeanProcessingTime(), true)));
            sb.append(String.format("%n    Max Time: %s", TimeUtils.printDuration(mrg.getMaxProcessingTime(), true)));
            sb.append(String.format("%n    Min Time: %s", TimeUtils.printDuration(mrg.getMinProcessingTime(), true)));
            if (mrg.getExchangesTotal() > 0) {
                sb.append(String.format("%n    Last Time: %s", TimeUtils.printDuration(mrg.getLastProcessingTime(), true)));
                sb.append(String.format("%n    Delta Time: %s", TimeUtils.printDuration(mrg.getDeltaProcessingTime(), true)));
            }
            Date last = mrg.getLastExchangeCreatedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Started: %s", ago));
            }
            last = mrg.getLastExchangeCompletedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Completed: %s", ago));
            }
            last = mrg.getLastExchangeFailureTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Failed: %s", ago));
            }
            sb.append("\n");
            return null;
        };
        doCall(options, task);
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String action = (String) options.get(ACTION);
        String filter = (String) options.get(FILTER);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return new JsonObject();
        }

        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();
        Function<ManagedRouteGroupMBean, Object> task = mrg -> {
            JsonObject jo = new JsonObject();
            list.add(jo);
            jo.put("group", mrg.getRouteGroup());
            jo.put("size", mrg.getGroupSize());
            jo.put("state", mrg.getState());
            jo.put("uptime", mrg.getUptime());
            jo.put("routeIds", new JsonArray(Arrays.stream(mrg.getGroupIds()).toList()));
            JsonObject stats = new JsonObject();
            String coverage = calculateRouteCoverage(mrg, false);
            if (coverage != null) {
                stats.put("coverage", coverage);
            }
            String load1 = getLoad1(mrg);
            String load5 = getLoad5(mrg);
            String load15 = getLoad15(mrg);
            if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                stats.put("load01", load1);
                stats.put("load05", load5);
                stats.put("load15", load15);
            }
            String thp = getThroughput(mrg);
            if (!thp.isEmpty()) {
                stats.put("exchangesThroughput", thp);
            }
            stats.put("idleSince", mrg.getIdleSince());
            stats.put("exchangesTotal", mrg.getExchangesTotal());
            stats.put("exchangesFailed", mrg.getExchangesFailed());
            stats.put("exchangesInflight", mrg.getExchangesInflight());
            stats.put("meanProcessingTime", mrg.getMeanProcessingTime());
            stats.put("maxProcessingTime", mrg.getMaxProcessingTime());
            stats.put("minProcessingTime", mrg.getMinProcessingTime());
            if (mrg.getExchangesTotal() > 0) {
                stats.put("lastProcessingTime", mrg.getLastProcessingTime());
                stats.put("deltaProcessingTime", mrg.getDeltaProcessingTime());
            }
            Date last = mrg.getLastExchangeCreatedTimestamp();
            if (last != null) {
                stats.put("lastCreatedExchangeTimestamp", last.getTime());
            }
            last = mrg.getLastExchangeCompletedTimestamp();
            if (last != null) {
                stats.put("lastCompletedExchangeTimestamp", last.getTime());
            }
            last = mrg.getLastExchangeFailureTimestamp();
            if (last != null) {
                stats.put("lastFailedExchangeTimestamp", last.getTime());
            }
            jo.put("statistics", stats);
            return null;
        };
        doCall(options, task);
        root.put("routeGroups", list);
        return root;
    }

    protected void doCall(Map<String, Object> options, Function<ManagedRouteGroupMBean, Object> task) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            List<Route> routes = getCamelContext().getRoutes();
            routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
            routes.stream()
                    .map(route -> mcc.getManagedRouteGroup(route.getGroup()))
                    .filter(Objects::nonNull)
                    .filter(r -> accept(r, filter))
                    .filter(r -> accept(r, subPath))
                    .distinct()
                    .sorted(RouteGroupDevConsole::sort)
                    .limit(max)
                    .forEach(task::apply);
        }
    }

    private static boolean accept(ManagedRouteGroupMBean mrg, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return PatternHelper.matchPattern(mrg.getRouteGroup(), filter);
    }

    private static int sort(ManagedRouteGroupMBean o1, ManagedRouteGroupMBean o2) {
        return o1.getRouteGroup().compareToIgnoreCase(o2.getRouteGroup());
    }

    private String getLoad1(ManagedRouteGroupMBean mrg) {
        String s = mrg.getLoad01();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getLoad5(ManagedRouteGroupMBean mrg) {
        String s = mrg.getLoad05();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getLoad15(ManagedRouteGroupMBean mrg) {
        String s = mrg.getLoad15();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getThroughput(ManagedRouteGroupMBean mrg) {
        String s = mrg.getThroughput();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String calculateRouteCoverage(ManagedRouteGroupMBean mrg, boolean percent) {
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        Collection<String> ids = new ArrayList<>();
        for (String id : mrg.getGroupIds()) {
            ManagedRouteMBean mrb = mcc.getManagedRoute(id);
            try {
                ids.addAll(mrb.processorIds());
            } catch (Exception e) {
                return null;
            }
        }

        int total = ids.size();
        int covered = 0;

        for (String id : ids) {
            ManagedProcessorMBean mp = mcc.getManagedProcessor(id);
            if (mp != null) {
                if (mp.getExchangesTotal() > 0) {
                    covered++;
                }
            }
        }

        if (percent) {
            double p;
            if (total > 0) {
                p = ((double) covered / total) * 100;
            } else {
                p = 0;
            }
            String f = String.format("%.0f", p);
            return covered + "/" + total + " (" + f + "%)";
        } else {
            return covered + "/" + total;
        }
    }

    protected void doAction(CamelContext camelContext, String command, String filter) {
        if (filter == null) {
            filter = "*";
        }
        String[] patterns = filter.split(",");
        // find matching IDs
        List<String> ids = camelContext.getRoutes()
                .stream()
                .map(Route::getGroup)
                .filter(group -> {
                    for (String p : patterns) {
                        if (PatternHelper.matchPattern(group, p)) {
                            return true;
                        }
                    }
                    return false;
                })
                .distinct()
                .toList();
        for (String id : ids) {
            try {
                if ("start".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.getRouteController().startAllRoutes();
                    } else {
                        camelContext.getRouteController().startRouteGroup(id);
                    }
                } else if ("stop".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.getRouteController().stopAllRoutes();
                    } else {
                        camelContext.getRouteController().stopRouteGroup(id);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error {} route: {} due to: {}. This exception is ignored.", command, id, e.getMessage(), e);
            }
        }
    }

}
