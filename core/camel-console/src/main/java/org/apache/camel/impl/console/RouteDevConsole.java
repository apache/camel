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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "route", description = "Route information")
public class RouteDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(RouteDevConsole.class);

    /**
     * Filters the routes matching by route id, route uri, or route group, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    /**
     * Whether to include processors
     */
    public static final String PROCESSORS = "processors";

    /**
     * Action to perform such as start,stop,suspend,resume on one or more routes
     */
    public static final String ACTION = "action";

    public RouteDevConsole() {
        super("camel", "route", "Route", "Route information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String action = (String) options.get(ACTION);
        String filter = (String) options.get(FILTER);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return "";
        }

        final boolean processors = "true".equals(options.getOrDefault(PROCESSORS, "false"));
        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(String.format("    Id: %s", mrb.getRouteId()));
            if (mrb.getNodePrefixId() != null) {
                sb.append(String.format("\n    Node Prefix Id: %s", mrb.getNodePrefixId()));
            }
            if (mrb.getRouteGroup() != null) {
                sb.append(String.format("\n    Group: %s", mrb.getRouteGroup()));
            }
            if (mrb.getDescription() != null) {
                sb.append(String.format("\n    Description: %s", mrb.getDescription()));
            }
            sb.append(String.format("\n    From: %s", mrb.getEndpointUri()));
            sb.append(String.format("\n    Remote: %s", mrb.isRemoteEndpoint()));
            if (mrb.getSourceLocation() != null) {
                sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
            }
            sb.append(String.format("\n    State: %s", mrb.getState()));
            if (mrb.getLastError() != null) {
                String phase = StringHelper.capitalize(mrb.getLastError().getPhase().name().toLowerCase());
                String ago = TimeUtils.printSince(mrb.getLastError().getDate().getTime());
                sb.append(String.format("\n    Error Ago: %s", ago));
                sb.append(String.format("\n    Error Phase: %s", phase));
                Throwable cause = mrb.getLastError().getException();
                if (cause != null) {
                    sb.append(String.format("\n    Error Message: %s", cause.getMessage()));
                    final String stackTrace = ExceptionHelper.stackTraceToString(cause);
                    sb.append("\n\n");
                    sb.append(stackTrace);
                    sb.append("\n\n");
                }
            }
            sb.append(String.format("\n    Uptime: %s", mrb.getUptime()));
            String coverage = calculateRouteCoverage(mrb, true);
            if (coverage != null) {
                sb.append(String.format("\n    Coverage: %s", coverage));
            }
            String load1 = getLoad1(mrb);
            String load5 = getLoad5(mrb);
            String load15 = getLoad15(mrb);
            if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                sb.append(String.format("\n    Load Average: %s %s %s", load1, load5, load15));
            }
            String thp = getThroughput(mrb);
            if (!thp.isEmpty()) {
                sb.append(String.format("\n    Messages/Sec: %s", thp));
            }
            sb.append(String.format("\n    Total: %s", mrb.getExchangesTotal()));
            sb.append(String.format("\n    Failed: %s", mrb.getExchangesFailed()));
            sb.append(String.format("\n    Inflight: %s", mrb.getExchangesInflight()));
            long idle = mrb.getIdleSince();
            if (idle > 0) {
                sb.append(String.format("\n    Idle Since: %s", TimeUtils.printDuration(idle)));
            } else {
                sb.append(String.format("\n    Idle Since: %s", ""));
            }
            sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mrb.getMeanProcessingTime(), true)));
            sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mrb.getMaxProcessingTime(), true)));
            sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mrb.getMinProcessingTime(), true)));
            if (mrb.getExchangesTotal() > 0) {
                sb.append(String.format("\n    Last Time: %s", TimeUtils.printDuration(mrb.getLastProcessingTime(), true)));
                sb.append(String.format("\n    Delta Time: %s", TimeUtils.printDuration(mrb.getDeltaProcessingTime(), true)));
            }
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
            if (processors) {
                includeProcessorsText(mrb, sb);
            }
            sb.append("\n");
            return null;
        };
        doCall(options, task);
        return sb.toString();
    }

    private void includeProcessorsText(ManagedRouteMBean mrb, StringBuilder sb) {
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        Collection<String> ids;
        try {
            ids = mrb.processorIds();
        } catch (Exception e) {
            return;
        }

        // sort by index
        List<ManagedProcessorMBean> mps = new ArrayList<>();
        for (String id : ids) {
            ManagedProcessorMBean mp = mcc.getManagedProcessor(id);
            if (mp != null) {
                mps.add(mp);
            }
        }
        // sort processors by index
        mps.sort(Comparator.comparingInt(ManagedProcessorMBean::getIndex));

        for (ManagedProcessorMBean mp : mps) {
            sb.append("\n");
            sb.append(String.format("\n        Id: %s", mp.getProcessorId()));
            if (mp.getNodePrefixId() != null) {
                sb.append(String.format("\n        Node Prefix Id: %s", mp.getNodePrefixId()));
            }
            if (mp.getDescription() != null) {
                sb.append(String.format("\n        Description: %s", mp.getDescription()));
            }
            sb.append(String.format("\n        Processor: %s", mp.getProcessorName()));
            sb.append(String.format("\n        Level: %d", mp.getLevel()));
            if (mp.getSourceLocation() != null) {
                String loc = mp.getSourceLocation();
                if (mp.getSourceLineNumber() != null) {
                    loc += ":" + mp.getSourceLineNumber();
                }
                sb.append(String.format("\n        Source: %s", loc));
            }
            sb.append(String.format("\n        Total: %s", mp.getExchangesTotal()));
            sb.append(String.format("\n        Failed: %s", mp.getExchangesFailed()));
            sb.append(String.format("\n        Inflight: %s", mp.getExchangesInflight()));
            long idle = mp.getIdleSince();
            if (idle > 0) {
                sb.append(String.format("\n        Idle Since: %s", TimeUtils.printDuration(idle)));
            } else {
                sb.append(String.format("\n        Idle Since: %s", ""));
            }
            sb.append(String.format("\n        Mean Time: %s", TimeUtils.printDuration(mp.getMeanProcessingTime(), true)));
            sb.append(String.format("\n        Max Time: %s", TimeUtils.printDuration(mp.getMaxProcessingTime(), true)));
            sb.append(String.format("\n        Min Time: %s", TimeUtils.printDuration(mp.getMinProcessingTime(), true)));
            if (mp.getExchangesTotal() > 0) {
                sb.append(String.format("\n        Last Time: %s", TimeUtils.printDuration(mp.getLastProcessingTime(), true)));
                sb.append(
                        String.format("\n        Delta Time: %s", TimeUtils.printDuration(mp.getDeltaProcessingTime(), true)));
            }
            Date last = mp.getLastExchangeCompletedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("\n        Since Last Completed: %s", ago));
            }
            last = mp.getLastExchangeFailureTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("\n        Since Last Failed: %s", ago));
            }
        }
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String action = (String) options.get(ACTION);
        String filter = (String) options.get(FILTER);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return new JsonObject();
        }

        final boolean processors = "true".equals(options.getOrDefault(PROCESSORS, "false"));
        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            JsonObject jo = new JsonObject();
            list.add(jo);
            jo.put("routeId", mrb.getRouteId());
            if (mrb.getRouteGroup() != null) {
                jo.put("group", mrb.getRouteGroup());
            }
            if (mrb.getNodePrefixId() != null) {
                jo.put("nodePrefixId", mrb.getNodePrefixId());
            }
            if (mrb.getDescription() != null) {
                jo.put("description", mrb.getDescription());
            }
            jo.put("from", mrb.getEndpointUri());
            jo.put("remote", mrb.isRemoteEndpoint());
            if (mrb.getSourceLocation() != null) {
                jo.put("source", mrb.getSourceLocation());
            }
            jo.put("state", mrb.getState());
            jo.put("uptime", mrb.getUptime());
            if (mrb.getLastError() != null) {
                String phase = StringHelper.capitalize(mrb.getLastError().getPhase().name().toLowerCase());
                JsonObject eo = new JsonObject();
                eo.put("phase", phase);
                eo.put("timestamp", mrb.getLastError().getDate().getTime());
                Throwable cause = mrb.getLastError().getException();
                if (cause != null) {
                    eo.put("message", cause.getMessage());
                    JsonArray arr2 = new JsonArray();
                    final String trace = ExceptionHelper.stackTraceToString(cause);
                    eo.put("stackTrace", arr2);
                    Collections.addAll(arr2, trace.split("\n"));
                }
                jo.put("lastError", eo);
            }
            JsonObject stats = new JsonObject();
            String coverage = calculateRouteCoverage(mrb, false);
            if (coverage != null) {
                stats.put("coverage", coverage);
            }
            String load1 = getLoad1(mrb);
            String load5 = getLoad5(mrb);
            String load15 = getLoad15(mrb);
            if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                stats.put("load01", load1);
                stats.put("load05", load5);
                stats.put("load15", load15);
            }
            String thp = getThroughput(mrb);
            if (!thp.isEmpty()) {
                stats.put("exchangesThroughput", thp);
            }
            stats.put("idleSince", mrb.getIdleSince());
            stats.put("exchangesTotal", mrb.getExchangesTotal());
            stats.put("exchangesFailed", mrb.getExchangesFailed());
            stats.put("exchangesInflight", mrb.getExchangesInflight());
            stats.put("meanProcessingTime", mrb.getMeanProcessingTime());
            stats.put("maxProcessingTime", mrb.getMaxProcessingTime());
            stats.put("minProcessingTime", mrb.getMinProcessingTime());
            if (mrb.getExchangesTotal() > 0) {
                stats.put("lastProcessingTime", mrb.getLastProcessingTime());
                stats.put("deltaProcessingTime", mrb.getDeltaProcessingTime());
            }
            Date last = mrb.getLastExchangeCreatedTimestamp();
            if (last != null) {
                stats.put("lastCreatedExchangeTimestamp", last.getTime());
            }
            last = mrb.getLastExchangeCompletedTimestamp();
            if (last != null) {
                stats.put("lastCompletedExchangeTimestamp", last.getTime());
            }
            last = mrb.getLastExchangeFailureTimestamp();
            if (last != null) {
                stats.put("lastFailedExchangeTimestamp", last.getTime());
            }
            jo.put("statistics", stats);
            if (processors) {
                JsonArray arr = new JsonArray();
                jo.put("processors", arr);
                includeProcessorsJson(mrb, arr);
            }
            return null;
        };
        doCall(options, task);
        root.put("routes", list);
        return root;
    }

    private void includeProcessorsJson(ManagedRouteMBean mrb, JsonArray arr) {
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        Collection<String> ids;
        try {
            ids = mrb.processorIds();
        } catch (Exception e) {
            return;
        }

        List<ManagedProcessorMBean> mps = ids.stream().map(mcc::getManagedProcessor)
                .filter(Objects::nonNull)
                // sort processors by index
                .sorted(Comparator.comparingInt(ManagedProcessorMBean::getIndex))
                .toList();

        for (ManagedProcessorMBean mp : mps) {
            JsonObject jo = new JsonObject();
            arr.add(jo);

            jo.put("id", mp.getProcessorId());
            if (mp.getNodePrefixId() != null) {
                jo.put("nodePrefixId", mp.getNodePrefixId());
            }
            if (mp.getDescription() != null) {
                jo.put("description", mp.getDescription());
            }
            if (mp.getSourceLocation() != null) {
                String loc = mp.getSourceLocation();
                if (mp.getSourceLineNumber() != null) {
                    loc += ":" + mp.getSourceLineNumber();
                }
                jo.put("source", loc);
            }
            String line = ConsoleHelper.loadSourceLine(getCamelContext(), mp.getSourceLocation(), mp.getSourceLineNumber());
            if (line != null) {
                JsonArray ca = new JsonArray();
                jo.put("code", ca);
                JsonObject c = new JsonObject();
                if (mp.getSourceLineNumber() != null) {
                    c.put("line", mp.getSourceLineNumber());
                }
                c.put("code", Jsoner.escape(line));
                c.put("match", true);
                ca.add(c);
            }
            jo.put("processor", mp.getProcessorName());
            jo.put("level", mp.getLevel());
            final JsonObject stats = getStatsObject(mp);
            jo.put("statistics", stats);
        }
    }

    private static JsonObject getStatsObject(ManagedProcessorMBean mp) {
        JsonObject stats = new JsonObject();
        stats.put("idleSince", mp.getIdleSince());
        stats.put("exchangesTotal", mp.getExchangesTotal());
        stats.put("exchangesFailed", mp.getExchangesFailed());
        stats.put("exchangesInflight", mp.getExchangesInflight());
        stats.put("meanProcessingTime", mp.getMeanProcessingTime());
        stats.put("maxProcessingTime", mp.getMaxProcessingTime());
        stats.put("minProcessingTime", mp.getMinProcessingTime());
        if (mp.getExchangesTotal() > 0) {
            stats.put("lastProcessingTime", mp.getLastProcessingTime());
            stats.put("deltaProcessingTime", mp.getDeltaProcessingTime());
        }
        Date last = mp.getLastExchangeCreatedTimestamp();
        if (last != null) {
            stats.put("lastCreatedExchangeTimestamp", last.getTime());
        }
        last = mp.getLastExchangeCompletedTimestamp();
        if (last != null) {
            stats.put("lastCompletedExchangeTimestamp", last.getTime());
        }
        last = mp.getLastExchangeFailureTimestamp();
        if (last != null) {
            stats.put("lastFailedExchangeTimestamp", last.getTime());
        }
        return stats;
    }

    protected void doCall(Map<String, Object> options, Function<ManagedRouteMBean, Object> task) {
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
                    .map(route -> mcc.getManagedRoute(route.getRouteId()))
                    .filter(Objects::nonNull)
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

        if (filter.startsWith("group:")) {
            filter = filter.substring(6);
            return PatternHelper.matchPattern(mrb.getRouteGroup(), filter);
        }

        String onlyName = LoggerHelper.sourceNameOnly(mrb.getSourceLocation());
        return PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getEndpointUri(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocationShort(), filter)
                || PatternHelper.matchPattern(onlyName, filter);
    }

    private static int sort(ManagedRouteMBean o1, ManagedRouteMBean o2) {
        return o1.getRouteId().compareToIgnoreCase(o2.getRouteId());
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

    private String getThroughput(ManagedRouteMBean mrb) {
        String s = mrb.getThroughput();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String calculateRouteCoverage(ManagedRouteMBean mrb, boolean percent) {
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        Collection<String> ids;
        try {
            ids = mrb.processorIds();
        } catch (Exception e) {
            return null;
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
                .filter(r -> {
                    for (String p : patterns) {
                        String source = r.getRouteId();
                        if (p.startsWith("group:")) {
                            source = r.getGroup();
                            p = p.substring(6);
                        }
                        if (PatternHelper.matchPattern(source, p)) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(Route::getRouteId).toList();
        for (String id : ids) {
            try {
                if ("start".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.getRouteController().startAllRoutes();
                    } else {
                        camelContext.getRouteController().startRoute(id);
                    }
                } else if ("stop".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.getRouteController().stopAllRoutes();
                    } else {
                        camelContext.getRouteController().stopRoute(id);
                    }
                } else if ("suspend".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.suspend();
                    } else {
                        camelContext.getRouteController().suspendRoute(id);
                    }
                } else if ("resume".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.resume();
                    } else {
                        camelContext.getRouteController().resumeRoute(id);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error {} route: {} due to: {}. This exception is ignored.", command, id, e.getMessage(), e);
            }
        }
    }

}
