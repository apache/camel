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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "route", description = "Route information", readOnly = false)
public class RouteDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(RouteDevConsole.class);

    public record LastError(
            @Metadata(description = "The error phase") String phase,
            @Metadata(description = "Epoch time in milliseconds when the error happened") long timestamp,
            @Metadata(description = "The error message (only present when known)") String message,
            @Metadata(description = "The error stack trace, one entry per line (only present when known)") List<String> stackTrace) {
    }

    public record Statistics(
            @Metadata(description = "Route coverage (only present when computable)") String coverage,
            @Metadata(description = "1 minute load average (only present when available)") String load01,
            @Metadata(description = "5 minute load average (only present when available)") String load05,
            @Metadata(description = "15 minute load average (only present when available)") String load15,
            @Metadata(description = "Messages per second throughput (only present when available)") String exchangesThroughput,
            @Metadata(description = "Epoch time in milliseconds since the route has been idle") long idleSince,
            @Metadata(description = "Total number of exchanges") long exchangesTotal,
            @Metadata(description = "Number of failed exchanges") long exchangesFailed,
            @Metadata(description = "Number of inflight exchanges") long exchangesInflight,
            @Metadata(description = "Mean processing time in milliseconds") long meanProcessingTime,
            @Metadata(description = "Max processing time in milliseconds") long maxProcessingTime,
            @Metadata(description = "Min processing time in milliseconds") long minProcessingTime,
            @Metadata(description = "50th percentile processing time in milliseconds (only present when available)") Long p50ProcessingTime,
            @Metadata(description = "95th percentile processing time in milliseconds (only present when available)") Long p95ProcessingTime,
            @Metadata(description = "99th percentile processing time in milliseconds (only present when available)") Long p99ProcessingTime,
            @Metadata(description = "Processing time in milliseconds of the last exchange (only present once an exchange has completed)") Long lastProcessingTime,
            @Metadata(description = "Difference in processing time in milliseconds since the previous exchange (only present once an exchange has completed)") Long deltaProcessingTime,
            @Metadata(description = "Epoch time in milliseconds the last exchange was created (only present once an exchange has been created)") Long lastCreatedExchangeTimestamp,
            @Metadata(description = "Epoch time in milliseconds the last exchange completed (only present once an exchange has completed)") Long lastCompletedExchangeTimestamp,
            @Metadata(description = "Epoch time in milliseconds the last exchange failure was handled (only present once one has occurred)") Long lastFailureHandledExchangeTimestamp,
            @Metadata(description = "Epoch time in milliseconds the last exchange failed (only present once one has occurred)") Long lastFailedExchangeTimestamp) {
    }

    public record RouteEntry(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The route group (only present when known)") String group,
            @Metadata(description = "The node prefix ID (only present when known)") String nodePrefixId,
            @Metadata(description = "The route description (only present when configured)") String description,
            @Metadata(description = "The route note (only present when configured)") String note,
            @Metadata(description = "Whether the route was created by a Kamelet") boolean createdByKamelet,
            @Metadata(description = "Whether the route was created by a route template") boolean createdByRouteTemplate,
            @Metadata(description = "The route's endpoint URI") String from,
            @Metadata(description = "Whether the endpoint is remote") boolean remote,
            @Metadata(description = "The source location (only present when known)") String source,
            @Metadata(description = "The route state") String state,
            @Metadata(description = "Whether the route supports suspension") boolean supportsSuspension,
            @Metadata(description = "Route uptime, human readable text") String uptime,
            @Metadata(description = "The last lifecycle error (only present when one has occurred)") LastError lastError,
            @Metadata(description = "Runtime statistics") Statistics statistics,
            @Metadata(description = "The route's processors (only present when requested)") List<ProcessorDevConsole.ProcessorEntry> processors) {
    }

    public record Response(
            @Metadata(description = "The routes (only present when no action was requested)") List<RouteEntry> routes) {
    }

    @Metadata(label = "query",
              description = "Filters the routes matching by route id, route uri, or route group, and source location",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of entries displayed", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query", description = "Whether to include processors", javaType = "java.lang.Boolean",
              defaultValue = "false")
    public static final String PROCESSORS = "processors";

    @Metadata(label = "query", description = "Action to perform such as start,stop,suspend,resume on one or more routes",
              javaType = "java.lang.String", enums = "start,stop,suspend,resume")
    public static final String ACTION = "action";

    public RouteDevConsole() {
        super("camel", "route", "Route", "Route information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String action = optionString(options, ACTION);
        String filter = optionString(options, FILTER);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return "";
        }

        final boolean processors = optionBoolean(options, PROCESSORS, false);
        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(String.format("    Id: %s", mrb.getRouteId()));
            if (mrb.getNodePrefixId() != null) {
                sb.append(String.format("%n    Node Prefix Id: %s", mrb.getNodePrefixId()));
            }
            if (mrb.getRouteGroup() != null) {
                sb.append(String.format("%n    Group: %s", mrb.getRouteGroup()));
            }
            if (mrb.getDescription() != null) {
                sb.append(String.format("%n    Description: %s", mrb.getDescription()));
            }
            if (mrb.getNote() != null) {
                sb.append(String.format("%n    Note: %s", mrb.getNote()));
            }
            if (mrb.isCreatedByKamelet()) {
                sb.append(String.format("%n    Created By Kamelet: %s", true));
            }
            if (mrb.isCreatedByRouteTemplate()) {
                sb.append(String.format("%n    Created By Route Template: %s", true));
            }
            sb.append(String.format("%n    From: %s", mrb.getEndpointUri()));
            sb.append(String.format("%n    Remote: %s", mrb.isRemoteEndpoint()));
            if (mrb.getSourceLocation() != null) {
                sb.append(String.format("%n    Source: %s", mrb.getSourceLocation()));
            }
            sb.append(String.format("%n    State: %s", mrb.getState()));
            Route r = getCamelContext().getRoute(mrb.getRouteId());
            sb.append(String.format("%n    Supports Suspension: %s", r != null && r.supportsSuspension()));
            if (mrb.getLastError() != null) {
                String phase = StringHelper.capitalize(mrb.getLastError().getPhase().name().toLowerCase());
                String ago = TimeUtils.printSince(mrb.getLastError().getDate().getTime());
                sb.append(String.format("%n    Error Ago: %s", ago));
                sb.append(String.format("%n    Error Phase: %s", phase));
                Throwable cause = mrb.getLastError().getException();
                if (cause != null) {
                    sb.append(String.format("%n    Error Message: %s", cause.getMessage()));
                    final String stackTrace = ExceptionHelper.stackTraceToString(cause);
                    sb.append("\n\n");
                    sb.append(stackTrace);
                    sb.append("\n\n");
                }
            }
            sb.append(String.format("%n    Uptime: %s", mrb.getUptime()));
            String coverage = calculateRouteCoverage(getCamelContext(), mrb, true);
            if (coverage != null) {
                sb.append(String.format("%n    Coverage: %s", coverage));
            }
            String load1 = getLoad1(mrb);
            String load5 = getLoad5(mrb);
            String load15 = getLoad15(mrb);
            if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                sb.append(String.format("%n    Load Average: %s %s %s", load1, load5, load15));
            }
            String thp = getThroughput(mrb);
            if (!thp.isEmpty()) {
                sb.append(String.format("%n    Messages/Sec: %s", thp));
            }
            sb.append(String.format("%n    Total: %s", mrb.getExchangesTotal()));
            sb.append(String.format("%n    Failed: %s", mrb.getExchangesFailed()));
            sb.append(String.format("%n    Inflight: %s", mrb.getExchangesInflight()));
            long idle = mrb.getIdleSince();
            if (idle > 0) {
                sb.append(String.format("%n    Idle Since: %s", TimeUtils.printDuration(idle)));
            } else {
                sb.append(String.format("%n    Idle Since: %s", ""));
            }
            sb.append(String.format("%n    Mean Time: %s", TimeUtils.printDuration(mrb.getMeanProcessingTime(), true)));
            sb.append(String.format("%n    Max Time: %s", TimeUtils.printDuration(mrb.getMaxProcessingTime(), true)));
            sb.append(String.format("%n    Min Time: %s", TimeUtils.printDuration(mrb.getMinProcessingTime(), true)));
            if (mrb.getProcessingTimeP50() >= 0) {
                sb.append(String.format("%n    p50 Time: %s", TimeUtils.printDuration(mrb.getProcessingTimeP50(), true)));
                sb.append(String.format("%n    p95 Time: %s", TimeUtils.printDuration(mrb.getProcessingTimeP95(), true)));
                sb.append(String.format("%n    p99 Time: %s", TimeUtils.printDuration(mrb.getProcessingTimeP99(), true)));
            }
            if (mrb.getExchangesTotal() > 0) {
                sb.append(String.format("%n    Last Time: %s", TimeUtils.printDuration(mrb.getLastProcessingTime(), true)));
                sb.append(String.format("%n    Delta Time: %s", TimeUtils.printDuration(mrb.getDeltaProcessingTime(), true)));
            }
            Date last = mrb.getLastExchangeCreatedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Started: %s", ago));
            }
            last = mrb.getLastExchangeCompletedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Completed: %s", ago));
            }
            last = mrb.getLastExchangeFailureHandledTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Failure Handled: %s", ago));
            }
            last = mrb.getLastExchangeFailureTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Failed: %s", ago));
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

        ProcessorDevConsole.includeProcessorsText(getCamelContext(), sb, 0, null, mps);
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String action = optionString(options, ACTION);
        String filter = optionString(options, FILTER);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return JsonRecordSupport.toJsonObject(new Response(null));
        }

        final boolean processors = optionBoolean(options, PROCESSORS, false);
        final List<RouteEntry> list = new ArrayList<>();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            LastError lastError = null;
            if (mrb.getLastError() != null) {
                String phase = StringHelper.capitalize(mrb.getLastError().getPhase().name().toLowerCase());
                long timestamp = mrb.getLastError().getDate().getTime();
                String message = null;
                List<String> stackTrace = null;
                Throwable cause = mrb.getLastError().getException();
                if (cause != null) {
                    message = cause.getMessage();
                    final String trace = ExceptionHelper.stackTraceToString(cause);
                    stackTrace = Arrays.asList(trace.split("\n"));
                }
                lastError = new LastError(phase, timestamp, message, stackTrace);
            }

            Statistics stats = buildStatistics(getCamelContext(), mrb);

            List<ProcessorDevConsole.ProcessorEntry> procList = processors ? includeProcessorsJson(mrb) : null;

            Route r = getCamelContext().getRoute(mrb.getRouteId());
            list.add(new RouteEntry(
                    mrb.getRouteId(), mrb.getRouteGroup(), mrb.getNodePrefixId(), mrb.getDescription(), mrb.getNote(),
                    mrb.isCreatedByKamelet(), mrb.isCreatedByRouteTemplate(), mrb.getEndpointUri(),
                    mrb.isRemoteEndpoint(), mrb.getSourceLocation(), mrb.getState(),
                    r != null && r.supportsSuspension(), mrb.getUptime(), lastError, stats, procList));
            return null;
        };
        doCall(options, task);

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

    private List<ProcessorDevConsole.ProcessorEntry> includeProcessorsJson(ManagedRouteMBean mrb) {
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        List<ProcessorDevConsole.ProcessorEntry> entries = new ArrayList<>();
        Collection<String> ids;
        try {
            ids = mrb.processorIds();
        } catch (Exception e) {
            return entries;
        }

        List<ManagedProcessorMBean> mps = ids.stream().map(mcc::getManagedProcessor)
                .filter(Objects::nonNull)
                // sort processors by index
                .sorted(Comparator.comparingInt(ManagedProcessorMBean::getIndex))
                .toList();

        ProcessorDevConsole.includeProcessorsJSon(getCamelContext(), entries, Integer.MAX_VALUE, mps);
        return entries;
    }

    protected void doCall(Map<String, Object> options, Function<ManagedRouteMBean, Object> task) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = optionString(options, FILTER);
        final int max = optionInt(options, LIMIT, Integer.MAX_VALUE);

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

    private static String getLoad1(ManagedRouteMBean mrb) {
        String s = mrb.getLoad01();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private static String getLoad5(ManagedRouteMBean mrb) {
        String s = mrb.getLoad05();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private static String getLoad15(ManagedRouteMBean mrb) {
        String s = mrb.getLoad15();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private static String getThroughput(ManagedRouteMBean mrb) {
        String s = mrb.getThroughput();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private static String calculateRouteCoverage(CamelContext camelContext, ManagedRouteMBean mrb, boolean percent) {
        ManagedCamelContext mcc = camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

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

    public static JsonObject gatherRouteStats(CamelContext camelContext, ManagedRouteMBean mrb) {
        return JsonRecordSupport.toJsonObject(buildStatistics(camelContext, mrb));
    }

    private static Statistics buildStatistics(CamelContext camelContext, ManagedRouteMBean mrb) {
        String coverage = calculateRouteCoverage(camelContext, mrb, false);

        String load1 = getLoad1(mrb);
        String load5 = getLoad5(mrb);
        String load15 = getLoad15(mrb);
        boolean hasLoad = !load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty();

        String thp = getThroughput(mrb);

        Long p50 = null;
        Long p95 = null;
        Long p99 = null;
        if (mrb.getProcessingTimeP50() >= 0) {
            p50 = mrb.getProcessingTimeP50();
            p95 = mrb.getProcessingTimeP95();
            p99 = mrb.getProcessingTimeP99();
        }

        Long lastProcessingTime = null;
        Long deltaProcessingTime = null;
        if (mrb.getExchangesTotal() > 0) {
            lastProcessingTime = mrb.getLastProcessingTime();
            deltaProcessingTime = mrb.getDeltaProcessingTime();
        }

        Long lastCreated = timestampOf(mrb.getLastExchangeCreatedTimestamp());
        Long lastCompleted = timestampOf(mrb.getLastExchangeCompletedTimestamp());
        Long lastFailureHandled = timestampOf(mrb.getLastExchangeFailureHandledTimestamp());
        Long lastFailed = timestampOf(mrb.getLastExchangeFailureTimestamp());

        return new Statistics(
                coverage, hasLoad ? load1 : null, hasLoad ? load5 : null, hasLoad ? load15 : null,
                thp.isEmpty() ? null : thp,
                mrb.getIdleSince(), mrb.getExchangesTotal(), mrb.getExchangesFailed(), mrb.getExchangesInflight(),
                mrb.getMeanProcessingTime(), mrb.getMaxProcessingTime(), mrb.getMinProcessingTime(),
                p50, p95, p99, lastProcessingTime, deltaProcessingTime,
                lastCreated, lastCompleted, lastFailureHandled, lastFailed);
    }

    private static Long timestampOf(Date date) {
        return date != null ? date.getTime() : null;
    }

}
