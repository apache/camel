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

import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedPerformanceCounterMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole("top")
public class TopDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes and processors matching by route id, route uri, processor id, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    public TopDevConsole() {
        super("camel", "top", "Top", "Display the top routes");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        final StringBuilder sb = new StringBuilder();
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            if (subPath == null || subPath.isBlank()) {
                Function<ManagedRouteMBean, Object> task = mrb -> {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(String.format("    Route Id: %s", mrb.getRouteId()));
                    sb.append(String.format("\n    From: %s", mrb.getEndpointUri()));
                    if (mrb.getSourceLocation() != null) {
                        sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
                    }
                    sb.append(String.format("\n    Total: %s", mrb.getExchangesTotal()));
                    sb.append(String.format("\n    Failed: %s", mrb.getExchangesFailed()));
                    sb.append(String.format("\n    Inflight: %s", mrb.getExchangesInflight()));
                    sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mrb.getMeanProcessingTime(), true)));
                    sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mrb.getMaxProcessingTime(), true)));
                    sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mrb.getMinProcessingTime(), true)));
                    sb.append(
                            String.format("\n    Last Time: %s", TimeUtils.printDuration(mrb.getLastProcessingTime(), true)));
                    sb.append(
                            String.format("\n    Delta Time: %s", TimeUtils.printDuration(mrb.getDeltaProcessingTime(), true)));
                    sb.append(
                            String.format("\n    Total Time: %s", TimeUtils.printDuration(mrb.getTotalProcessingTime(), true)));
                    sb.append("\n");
                    return null;
                };
                topRoutes(filter, max, mcc, task);
            } else {
                Function<ManagedProcessorMBean, Object> task = mpb -> {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(String.format("    Route Id: %s", mpb.getRouteId()));
                    sb.append(String.format("\n    Processor Id: %s", mpb.getProcessorId()));
                    String loc = mpb.getSourceLocation();
                    StringBuilder code = new StringBuilder();
                    if (loc != null && mpb.getSourceLineNumber() != null) {
                        int line = mpb.getSourceLineNumber();
                        try {
                            loc = LoggerHelper.stripSourceLocationLineNumber(loc);
                            Resource resource = PluginHelper.getResourceLoader(getCamelContext()).resolveResource(loc);
                            if (resource != null) {
                                LineNumberReader reader = new LineNumberReader(resource.getReader());
                                for (int i = 1; i < line + 3; i++) {
                                    String t = reader.readLine();
                                    if (t != null) {
                                        int low = line - 2;
                                        int high = line + 4;
                                        if (i >= low && i <= high) {
                                            String arrow = i == line ? "-->" : "   ";
                                            code.append(String.format("\n        %s #%s %s", arrow, i, t));
                                        }
                                    }
                                }
                                IOHelper.close(reader);
                            }
                            loc += ":" + mpb.getSourceLineNumber();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    if (loc != null) {
                        sb.append(String.format("\n    Source: %s", loc));
                        if (code.length() > 0) {
                            sb.append(code);
                        }
                    }
                    sb.append(String.format("\n    Total: %s", mpb.getExchangesTotal()));
                    sb.append(String.format("\n    Failed: %s", mpb.getExchangesFailed()));
                    sb.append(String.format("\n    Inflight: %s", mpb.getExchangesInflight()));
                    sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mpb.getMeanProcessingTime(), true)));
                    sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mpb.getMaxProcessingTime(), true)));
                    sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mpb.getMinProcessingTime(), true)));
                    sb.append(
                            String.format("\n    Last Time: %s", TimeUtils.printDuration(mpb.getLastProcessingTime(), true)));
                    sb.append(
                            String.format("\n    Delta Time: %s", TimeUtils.printDuration(mpb.getDeltaProcessingTime(), true)));
                    sb.append(
                            String.format("\n    Total Time: %s", TimeUtils.printDuration(mpb.getTotalProcessingTime(), true)));
                    sb.append("\n");
                    return null;
                };
                topProcessors(filter, subPath, max, mcc, task);
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            if (subPath == null || subPath.isBlank()) {
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
                    stats.put("exchangesTotal", mrb.getExchangesTotal());
                    stats.put("exchangesFailed", mrb.getExchangesFailed());
                    stats.put("exchangesInflight", mrb.getExchangesInflight());
                    stats.put("meanProcessingTime", mrb.getMeanProcessingTime());
                    stats.put("maxProcessingTime", mrb.getMaxProcessingTime());
                    stats.put("minProcessingTime", mrb.getMinProcessingTime());
                    stats.put("lastProcessingTime", mrb.getLastProcessingTime());
                    stats.put("deltaProcessingTime", mrb.getDeltaProcessingTime());
                    stats.put("totalProcessingTime", mrb.getTotalProcessingTime());
                    jo.put("statistics", stats);
                    return null;
                };
                topRoutes(filter, max, mcc, task);
                root.put("routes", list);
            } else {
                Function<ManagedProcessorMBean, Object> task = mpb -> {
                    JsonObject jo = new JsonObject();
                    list.add(jo);

                    jo.put("routeId", mpb.getRouteId());
                    jo.put("processorId", mpb.getProcessorId());
                    String loc = mpb.getSourceLocation();
                    List<JsonObject> code = new ArrayList<>();
                    if (loc != null && mpb.getSourceLineNumber() != null) {
                        int line = mpb.getSourceLineNumber();
                        try {
                            loc = LoggerHelper.stripSourceLocationLineNumber(loc);
                            Resource resource = PluginHelper.getResourceLoader(getCamelContext()).resolveResource(loc);
                            if (resource != null) {
                                LineNumberReader reader = new LineNumberReader(resource.getReader());
                                for (int i = 1; i < line + 3; i++) {
                                    String t = reader.readLine();
                                    if (t != null) {
                                        int low = line - 2;
                                        int high = line + 4;
                                        if (i >= low && i <= high) {
                                            JsonObject c = new JsonObject();
                                            c.put("line", i);
                                            if (line == i) {
                                                c.put("match", true);
                                            }
                                            c.put("code", Jsoner.escape(t));
                                            code.add(c);
                                        }
                                    }
                                }
                                IOHelper.close(reader);
                            }
                            loc += ":" + mpb.getSourceLineNumber();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    if (loc != null) {
                        jo.put("location", loc);
                        if (!code.isEmpty()) {
                            jo.put("code", code);
                        }
                    }

                    JsonObject stats = new JsonObject();
                    stats.put("exchangesTotal", mpb.getExchangesTotal());
                    stats.put("exchangesFailed", mpb.getExchangesFailed());
                    stats.put("exchangesInflight", mpb.getExchangesInflight());
                    stats.put("meanProcessingTime", mpb.getMeanProcessingTime());
                    stats.put("maxProcessingTime", mpb.getMaxProcessingTime());
                    stats.put("minProcessingTime", mpb.getMinProcessingTime());
                    stats.put("lastProcessingTime", mpb.getLastProcessingTime());
                    stats.put("deltaProcessingTime", mpb.getDeltaProcessingTime());
                    stats.put("totalProcessingTime", mpb.getTotalProcessingTime());
                    jo.put("statistics", stats);
                    return null;
                };
                topProcessors(filter, subPath, max, mcc, task);
                root.put("processors", list);
            }
        }

        return root;
    }

    private void topRoutes(
            String filter, int max, ManagedCamelContext mcc,
            Function<ManagedRouteMBean, Object> task) {
        List<Route> routes = getCamelContext().getRoutes();
        routes.stream()
                .map(route -> mcc.getManagedRoute(route.getRouteId()))
                .filter(r -> acceptRoute(r, filter))
                .sorted(TopDevConsole::top)
                .limit(max)
                .forEach(task::apply);
    }

    private void topProcessors(
            String filter, String subPath, int max, ManagedCamelContext mcc,
            Function<ManagedProcessorMBean, Object> task) {
        List<Route> routes = getCamelContext().getRoutes();
        Collection<String> ids = new ArrayList<>();

        routes.stream()
                .map(route -> mcc.getManagedRoute(route.getRouteId()))
                .filter(r -> acceptRoute(r, subPath))
                .forEach(r -> {
                    try {
                        ids.addAll(r.processorIds());
                    } catch (Exception e) {
                        // ignore
                    }
                });

        ids.stream()
                .map(mcc::getManagedProcessor)
                .filter(p -> acceptProcessor(p, filter))
                .sorted(TopDevConsole::top)
                .limit(max)
                .forEach(task::apply);
    }

    private static boolean acceptRoute(ManagedRouteMBean mrb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getEndpointUri(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocationShort(), filter);
    }

    private static boolean acceptProcessor(ManagedProcessorMBean mpb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(mpb.getProcessorId(), filter)
                || PatternHelper.matchPattern(mpb.getSourceLocation(), filter);
    }

    private static int top(ManagedPerformanceCounterMBean o1, ManagedPerformanceCounterMBean o2) {
        // sort for highest mean value as we want the slowest in the top
        long m1 = o1.getMeanProcessingTime();
        long m2 = o2.getMeanProcessingTime();
        if (m1 < m2) {
            return 1;
        } else if (m1 > m2) {
            return -1;
        } else {
            return 0;
        }
    }

}
