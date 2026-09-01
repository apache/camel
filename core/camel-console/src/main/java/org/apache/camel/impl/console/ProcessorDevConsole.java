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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedDestinationAware;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "processor", description = "Processor information", readOnly = false)
public class ProcessorDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessorDevConsole.class);

    public record CodeLine(
            @Metadata(description = "The source line number (only present when known)") Integer line,
            @Metadata(description = "The source code line") String code,
            @Metadata(description = "Whether this is the matched line (only present when true)") Boolean match) {
    }

    public record Statistics(
            @Metadata(description = "Epoch time in milliseconds since the processor has been idle") long idleSince,
            @Metadata(description = "Total number of exchanges") long exchangesTotal,
            @Metadata(description = "Number of failed exchanges") long exchangesFailed,
            @Metadata(description = "Number of inflight exchanges") long exchangesInflight,
            @Metadata(description = "Messages per second throughput (only present when available)") String exchangesThroughput,
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

    public record ProcessorEntry(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The processor ID") String id,
            @Metadata(description = "The node prefix ID (only present when known)") String nodePrefixId,
            @Metadata(description = "The processor description (only present when configured)") String description,
            @Metadata(description = "The processor note (only present when configured)") String note,
            @Metadata(description = "The source location, optionally with a line number suffix (only present when known)") String source,
            @Metadata(description = "The processor state") String state,
            @Metadata(description = "Whether the processor is disabled (only present when known)") Boolean disabled,
            @Metadata(description = "The step ID (only present when known)") String stepId,
            @Metadata(description = "A snippet of source code around the processor (only present when known)") List<CodeLine> code,
            @Metadata(description = "The processor name") String processor,
            @Metadata(description = "The processor level in the route tree") int level,
            @Metadata(description = "The destination URI, for processors that send to a destination (only present when applicable)") String uri,
            @Metadata(description = "Runtime statistics") Statistics statistics) {
    }

    public record Response(
            @Metadata(description = "The processors (only present when no action was requested)") List<ProcessorEntry> processors) {
    }

    @Metadata(label = "query",
              description = "Filters the processors matching by processor id, route id, or route group, and source location",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of entries displayed", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query",
              description = "Action to perform such as start,stop,enable,disable on one or more processors",
              javaType = "java.lang.String", enums = "start,stop,enable,disable")
    public static final String ACTION = "action";

    public ProcessorDevConsole() {
        super("camel", "processor", "Processor", "Processor information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String action = optionString(options, ACTION);
        String filter = optionString(options, FILTER);
        final int max = optionInt(options, LIMIT, Integer.MAX_VALUE);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return "";
        }

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        final StringBuilder sb = new StringBuilder();
        final AtomicInteger counter = new AtomicInteger();
        for (Route r : getCamelContext().getRoutes()) {
            ManagedRouteMBean mrb = mcc.getManagedRoute(r.getRouteId());
            includeProcessorsText(mrb, sb, filter, max, counter);
            sb.append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    private void includeProcessorsText(ManagedRouteMBean mrb, StringBuilder sb, String filter, int max, AtomicInteger counter) {
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
            if (mp != null && accept(mp, filter)) {
                mps.add(mp);
            }
        }

        // sort processors by index
        mps.sort(Comparator.comparingInt(ManagedProcessorMBean::getIndex));

        includeProcessorsText(getCamelContext(), sb, max, counter, mps);
    }

    public static void includeProcessorsText(
            CamelContext camelContext,
            StringBuilder sb, int max, AtomicInteger counter, List<ManagedProcessorMBean> mps) {
        for (ManagedProcessorMBean mp : mps) {
            if (counter != null && counter.incrementAndGet() > max) {
                return;
            }
            sb.append("\n");
            sb.append(String.format("%n        Route Id: %s", mp.getRouteId()));
            sb.append(String.format("%n        Id: %s", mp.getProcessorId()));
            if (mp.getNodePrefixId() != null) {
                sb.append(String.format("%n        Node Prefix Id: %s", mp.getNodePrefixId()));
            }
            if (mp.getDescription() != null) {
                sb.append(String.format("%n        Description: %s", mp.getDescription()));
            }
            if (mp.getNote() != null) {
                sb.append(String.format("%n        Note: %s", mp.getNote()));
            }
            sb.append(String.format("%n        Processor: %s", mp.getProcessorName()));
            if (mp.getStepId() != null) {
                sb.append(String.format("%n        Step Id: %s", mp.getStepId()));
            }
            sb.append(String.format("%n        Level: %d", mp.getLevel()));
            if (mp.getSourceLocation() != null) {
                String loc = mp.getSourceLocation();
                if (mp.getSourceLineNumber() != null) {
                    loc += ":" + mp.getSourceLineNumber();
                }
                sb.append(String.format("%n        Source: %s", loc));
            }

            // processors which can send to a destination (such as to/toD/poll etc)
            String destination = getDestination(camelContext, mp);
            if (destination != null) {
                sb.append(String.format("%n        Uri: %s", destination));
            }

            sb.append(String.format("%n        State: %s", mp.getState()));
            sb.append(String.format("%n        Disabled: %s", mp.getDisabled()));
            sb.append(String.format("%n        Total: %s", mp.getExchangesTotal()));
            sb.append(String.format("%n        Failed: %s", mp.getExchangesFailed()));
            sb.append(String.format("%n        Inflight: %s", mp.getExchangesInflight()));
            long idle = mp.getIdleSince();
            if (idle > 0) {
                sb.append(String.format("%n        Idle Since: %s", TimeUtils.printDuration(idle)));
            } else {
                sb.append(String.format("%n        Idle Since: %s", ""));
            }
            sb.append(String.format("%n        Mean Time: %s", TimeUtils.printDuration(mp.getMeanProcessingTime(), true)));
            sb.append(String.format("%n        Max Time: %s", TimeUtils.printDuration(mp.getMaxProcessingTime(), true)));
            sb.append(String.format("%n        Min Time: %s", TimeUtils.printDuration(mp.getMinProcessingTime(), true)));
            if (mp.getExchangesTotal() > 0) {
                sb.append(String.format("%n        Last Time: %s", TimeUtils.printDuration(mp.getLastProcessingTime(), true)));
                sb.append(
                        String.format("%n        Delta Time: %s", TimeUtils.printDuration(mp.getDeltaProcessingTime(), true)));
            }
            Date last = mp.getLastExchangeCompletedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n        Since Last Completed: %s", ago));
            }
            last = mp.getLastExchangeFailureHandledTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n        Since Last Failure Handled: %s", ago));
            }
            last = mp.getLastExchangeFailureTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("%n        Since Last Failed: %s", ago));
            }
        }
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String action = optionString(options, ACTION);
        String filter = optionString(options, FILTER);
        final int max = optionInt(options, LIMIT, Integer.MAX_VALUE);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return JsonRecordSupport.toJsonObject(new Response(null));
        }

        List<ProcessorEntry> list = new ArrayList<>();
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        for (Route r : getCamelContext().getRoutes()) {
            ManagedRouteMBean mrb = mcc.getManagedRoute(r.getRouteId());
            includeProcessorsJson(mrb, list, filter, max);
        }

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

    private void includeProcessorsJson(ManagedRouteMBean mrb, List<ProcessorEntry> list, String filter, int max) {
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
            if (mp != null && accept(mp, filter)) {
                mps.add(mp);
            }
        }

        // sort processors by index
        mps.sort(Comparator.comparingInt(ManagedProcessorMBean::getIndex));

        // include processors into the list
        includeProcessorsJSon(getCamelContext(), list, max, mps);
    }

    public static void includeProcessorsJSon(
            CamelContext camelContext, List<ProcessorEntry> list, int max, List<ManagedProcessorMBean> mps) {
        for (int i = 0; i < mps.size(); i++) {
            ManagedProcessorMBean mp = mps.get(i);
            if (list.size() > max) {
                return;
            }

            String source = null;
            if (mp.getSourceLocation() != null) {
                source = mp.getSourceLocation();
                if (mp.getSourceLineNumber() != null) {
                    source += ":" + mp.getSourceLineNumber();
                }
            }

            // calculate end line number
            ManagedProcessorMBean mp2 = i < mps.size() - 1 ? mps.get(i + 1) : null;
            Integer end = mp2 != null ? mp2.getSourceLineNumber() : null;
            if (mp.getSourceLineNumber() != null) {
                if (end == null) {
                    end = mp.getSourceLineNumber() + 5;
                } else {
                    // clip so we do not read ahead to far, as we just want a snippet of the source code
                    end = Math.min(mp.getSourceLineNumber() + 5, end);
                }
            }

            List<CodeLine> code = new ArrayList<>();
            List<String> lines
                    = ConsoleHelper.loadSourceLines(camelContext, mp.getSourceLocation(), mp.getSourceLineNumber(), end);
            Integer pos = mp.getSourceLineNumber();
            for (String line : lines) {
                Boolean match = pos != null && pos.equals(mp.getSourceLineNumber()) ? true : null;
                code.add(new CodeLine(pos, Jsoner.escape(line), match));
                if (pos != null) {
                    pos++;
                }
            }

            // processors which can send to a destination (such as to/toD/poll etc)
            String destination = getDestination(camelContext, mp);

            list.add(new ProcessorEntry(
                    mp.getRouteId(), mp.getProcessorId(), mp.getNodePrefixId(), mp.getDescription(), mp.getNote(),
                    source, mp.getState(), mp.getDisabled(), mp.getStepId(), code.isEmpty() ? null : code,
                    mp.getProcessorName(), mp.getLevel(), destination, buildStatistics(mp)));
        }
    }

    private static String getDestination(CamelContext camelContext, ManagedProcessorMBean mp) {
        // processors which can send to a destination (such as to/toD/poll etc)
        String kind = mp.getProcessorName();
        if ("dynamicRouter".equals(kind) || "enrich".equals(kind) || "pollEnrich".equals(kind) || "poll".equals(kind)
                || "toD".equals(kind) || "to".equals(kind) || "wireTap".equals(kind)) {
            ManagedCamelContext mcc = camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
            ManagedDestinationAware mda = mcc.getManagedProcessor(mp.getProcessorId(), ManagedDestinationAware.class);
            if (mda != null) {
                return mda.getDestination();
            }
        }
        return null;
    }

    public static JsonObject gatherProcessorStats(ManagedProcessorMBean mp) {
        return JsonRecordSupport.toJsonObject(buildStatistics(mp));
    }

    private static Statistics buildStatistics(ManagedProcessorMBean mp) {
        String thp = mp.getThroughput();
        if (thp != null) {
            thp = thp.replace(',', '.');
            if (thp.isEmpty()) {
                thp = null;
            }
        }

        Long p50 = null;
        Long p95 = null;
        Long p99 = null;
        if (mp.getProcessingTimeP50() >= 0) {
            p50 = mp.getProcessingTimeP50();
            p95 = mp.getProcessingTimeP95();
            p99 = mp.getProcessingTimeP99();
        }

        Long lastProcessingTime = null;
        Long deltaProcessingTime = null;
        if (mp.getExchangesTotal() > 0) {
            lastProcessingTime = mp.getLastProcessingTime();
            deltaProcessingTime = mp.getDeltaProcessingTime();
        }

        Long lastCreated = timestampOf(mp.getLastExchangeCreatedTimestamp());
        Long lastCompleted = timestampOf(mp.getLastExchangeCompletedTimestamp());
        Long lastFailureHandled = timestampOf(mp.getLastExchangeFailureHandledTimestamp());
        Long lastFailed = timestampOf(mp.getLastExchangeFailureTimestamp());

        return new Statistics(
                mp.getIdleSince(), mp.getExchangesTotal(), mp.getExchangesFailed(), mp.getExchangesInflight(), thp,
                mp.getMeanProcessingTime(), mp.getMaxProcessingTime(), mp.getMinProcessingTime(),
                p50, p95, p99, lastProcessingTime, deltaProcessingTime,
                lastCreated, lastCompleted, lastFailureHandled, lastFailed);
    }

    private static Long timestampOf(Date date) {
        return date != null ? date.getTime() : null;
    }

    private static boolean accept(ManagedProcessorMBean mrb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        String onlyName = LoggerHelper.sourceNameOnly(mrb.getSourceLocation());
        return PatternHelper.matchPattern(mrb.getProcessorId(), filter)
                || PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocationShort(), filter)
                || PatternHelper.matchPattern(onlyName, filter);
    }

    protected void doAction(CamelContext camelContext, String command, String filter) {
        if (filter == null) {
            filter = "*";
        }
        String[] patterns = filter.split(",");

        List<ManagedProcessorMBean> mps = new ArrayList<>();
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        for (Route r : getCamelContext().getRoutes()) {
            ManagedRouteMBean mrb = mcc.getManagedRoute(r.getRouteId());
            try {
                for (String id : mrb.processorIds()) {
                    mps.add(mcc.getManagedProcessor(id));
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // find matching IDs
        mps = mps.stream()
                .filter(mp -> {
                    for (String p : patterns) {
                        if (PatternHelper.matchPattern(mp.getProcessorId(), p)
                                || PatternHelper.matchPattern(mp.getRouteId(), p)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();

        for (ManagedProcessorMBean mp : mps) {
            try {
                if ("start".equals(command)) {
                    mp.start();
                } else if ("stop".equals(command)) {
                    mp.stop();
                } else if ("disable".equals(command)) {
                    mp.disable();
                } else if ("enable".equals(command)) {
                    mp.enable();
                }
            } catch (Exception e) {
                LOG.warn("Error {} processor: {} due to: {}. This exception is ignored.", command, mp.getProcessorId(),
                        e.getMessage(), e);
            }
        }
    }

}
