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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "route-group", description = "Route Group information", readOnly = false)
public class RouteGroupDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(RouteGroupDevConsole.class);

    public record Statistics(
            @Metadata(description = "Route coverage (only present when computable)") String coverage,
            @Metadata(description = "1 minute load average (only present when available)") String load01,
            @Metadata(description = "5 minute load average (only present when available)") String load05,
            @Metadata(description = "15 minute load average (only present when available)") String load15,
            @Metadata(description = "Messages per second throughput (only present when available)") String exchangesThroughput,
            @Metadata(description = "Epoch time in milliseconds since the route group has been idle") long idleSince,
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

    public record RouteGroupEntry(
            @Metadata(description = "The route group ID") String group,
            @Metadata(description = "Number of routes in this group") int size,
            @Metadata(description = "The route group state") String state,
            @Metadata(description = "Route uptime, human readable text") String uptime,
            @Metadata(description = "The route IDs within this group") List<String> routeIds,
            @Metadata(description = "Runtime statistics") Statistics statistics) {
    }

    public record Response(
            @Metadata(description = "The route groups (only present when no action was requested)") List<RouteGroupEntry> routeGroups) {
    }

    @Metadata(label = "query", description = "Filters the route groups matching by group id", javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of entries displayed", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query", description = "Action to perform such as start or stop", javaType = "java.lang.String",
              enums = "start,stop")
    public static final String ACTION = "action";

    public RouteGroupDevConsole() {
        super("camel", "route-group", "Route Group", "Route Group information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String action = optionString(options, ACTION);
        String filter = optionString(options, FILTER);
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
            last = mrg.getLastExchangeFailureHandledTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n    Since Last Failure Handled: %s", ago));
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
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String action = optionString(options, ACTION);
        String filter = optionString(options, FILTER);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return JsonRecordSupport.toJsonObject(new Response(null));
        }

        final List<RouteGroupEntry> list = new ArrayList<>();
        Function<ManagedRouteGroupMBean, Object> task = mrg -> {
            Statistics stats = buildStatistics(mrg);
            list.add(new RouteGroupEntry(
                    mrg.getRouteGroup(), mrg.getGroupSize(), mrg.getState(), mrg.getUptime(),
                    Arrays.asList(mrg.getGroupIds()), stats));
            return null;
        };
        doCall(options, task);

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

    private Statistics buildStatistics(ManagedRouteGroupMBean mrg) {
        String coverage = calculateRouteCoverage(mrg, false);

        String load1 = getLoad1(mrg);
        String load5 = getLoad5(mrg);
        String load15 = getLoad15(mrg);
        boolean hasLoad = !load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty();

        String thp = getThroughput(mrg);

        Long p50 = null;
        Long p95 = null;
        Long p99 = null;
        if (mrg.getProcessingTimeP50() >= 0) {
            p50 = mrg.getProcessingTimeP50();
            p95 = mrg.getProcessingTimeP95();
            p99 = mrg.getProcessingTimeP99();
        }

        Long lastProcessingTime = null;
        Long deltaProcessingTime = null;
        if (mrg.getExchangesTotal() > 0) {
            lastProcessingTime = mrg.getLastProcessingTime();
            deltaProcessingTime = mrg.getDeltaProcessingTime();
        }

        Long lastCreated = timestampOf(mrg.getLastExchangeCreatedTimestamp());
        Long lastCompleted = timestampOf(mrg.getLastExchangeCompletedTimestamp());
        Long lastFailureHandled = timestampOf(mrg.getLastExchangeFailureHandledTimestamp());
        Long lastFailed = timestampOf(mrg.getLastExchangeFailureTimestamp());

        return new Statistics(
                coverage, hasLoad ? load1 : null, hasLoad ? load5 : null, hasLoad ? load15 : null,
                thp.isEmpty() ? null : thp,
                mrg.getIdleSince(), mrg.getExchangesTotal(), mrg.getExchangesFailed(), mrg.getExchangesInflight(),
                mrg.getMeanProcessingTime(), mrg.getMaxProcessingTime(), mrg.getMinProcessingTime(),
                p50, p95, p99, lastProcessingTime, deltaProcessingTime,
                lastCreated, lastCompleted, lastFailureHandled, lastFailed);
    }

    private static Long timestampOf(Date date) {
        return date != null ? date.getTime() : null;
    }

    protected void doCall(Map<String, Object> options, Function<ManagedRouteGroupMBean, Object> task) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = optionString(options, FILTER);
        final int max = optionInt(options, LIMIT, Integer.MAX_VALUE);

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
