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
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DevConsole(name = "processor", description = "Processor information")
public class ProcessorDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessorDevConsole.class);

    /**
     * Filters the processors matching by processor id, route id, or route group, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    /**
     * Action to perform such as start,stop,suspend,resume,enable,disable on one or more processors
     */
    public static final String ACTION = "action";

    public ProcessorDevConsole() {
        super("camel", "processor", "Processor", "Processor information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String action = (String) options.get(ACTION);
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);
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
            outputProcessorBasicInfoText(sb, mp);
            outputProcessorSourceText(sb, mp);
            outputProcessorDestinationText(sb, camelContext, mp);
            outputProcessorStateText(sb, mp);
            outputProcessorStatsText(sb, mp);
        }
    }

    private static void outputProcessorBasicInfoText(StringBuilder sb, ManagedProcessorMBean mp) {
        sb.append(String.format("%n        Route Id: %s", mp.getRouteId()));
        sb.append(String.format("%n        Id: %s", mp.getProcessorId()));
        appendOptionalText(sb, "Node Prefix Id", mp.getNodePrefixId());
        appendOptionalText(sb, "Description", mp.getDescription());
        appendOptionalText(sb, "Note", mp.getNote());
        sb.append(String.format("%n        Processor: %s", mp.getProcessorName()));
        appendOptionalText(sb, "Step Id", mp.getStepId());
        sb.append(String.format("%n        Level: %d", mp.getLevel()));
    }

    private static void outputProcessorSourceText(StringBuilder sb, ManagedProcessorMBean mp) {
        if (mp.getSourceLocation() == null) {
            return;
        }
        String loc = mp.getSourceLocation();
        if (mp.getSourceLineNumber() != null) {
            loc += ":" + mp.getSourceLineNumber();
        }
        sb.append(String.format("%n        Source: %s", loc));
    }

    private static void outputProcessorDestinationText(StringBuilder sb, CamelContext camelContext, ManagedProcessorMBean mp) {
        String destination = getDestination(camelContext, mp);
        if (destination != null) {
            sb.append(String.format("%n        Uri: %s", destination));
        }
    }

    private static void outputProcessorStateText(StringBuilder sb, ManagedProcessorMBean mp) {
        sb.append(String.format("%n        State: %s", mp.getState()));
        sb.append(String.format("%n        Disabled: %s", mp.getDisabled()));
        sb.append(String.format("%n        Total: %s", mp.getExchangesTotal()));
        sb.append(String.format("%n        Failed: %s", mp.getExchangesFailed()));
        sb.append(String.format("%n        Inflight: %s", mp.getExchangesInflight()));
    }

    private static void outputProcessorStatsText(StringBuilder sb, ManagedProcessorMBean mp) {
        long idle = mp.getIdleSince();
        sb.append(String.format("%n        Idle Since: %s", idle > 0 ? TimeUtils.printDuration(idle) : ""));
        sb.append(String.format("%n        Mean Time: %s", TimeUtils.printDuration(mp.getMeanProcessingTime(), true)));
        sb.append(String.format("%n        Max Time: %s", TimeUtils.printDuration(mp.getMaxProcessingTime(), true)));
        sb.append(String.format("%n        Min Time: %s", TimeUtils.printDuration(mp.getMinProcessingTime(), true)));

        if (mp.getExchangesTotal() > 0) {
            sb.append(String.format("%n        Last Time: %s", TimeUtils.printDuration(mp.getLastProcessingTime(), true)));
            sb.append(String.format("%n        Delta Time: %s", TimeUtils.printDuration(mp.getDeltaProcessingTime(), true)));
        }

        Date lastCompleted = mp.getLastExchangeCompletedTimestamp();
        if (lastCompleted != null) {
            sb.append(String.format("%n        Since Last Completed: %s", TimeUtils.printSince(lastCompleted.getTime())));
        }
        Date lastFailed = mp.getLastExchangeFailureTimestamp();
        if (lastFailed != null) {
            sb.append(String.format("%n        Since Last Failed: %s", TimeUtils.printSince(lastFailed.getTime())));
        }
    }

    private static void appendOptionalText(StringBuilder sb, String label, String value) {
        if (value != null) {
            sb.append(String.format("%n        %s: %s", label, value));
        }
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String action = (String) options.get(ACTION);
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);
        if (action != null) {
            doAction(getCamelContext(), action, filter);
            return new JsonObject();
        }

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        for (Route r : getCamelContext().getRoutes()) {
            ManagedRouteMBean mrb = mcc.getManagedRoute(r.getRouteId());
            includeProcessorsJson(mrb, arr, filter, max);
        }

        root.put("processors", arr);
        return root;
    }

    private void includeProcessorsJson(ManagedRouteMBean mrb, JsonArray arr, String filter, int max) {
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

        // include processors into the array
        includeProcessorsJSon(getCamelContext(), arr, max, mps);
    }

    public static void includeProcessorsJSon(
            CamelContext camelContext, JsonArray arr, int max, List<ManagedProcessorMBean> mps) {
        for (int i = 0; i < mps.size(); i++) {
            if (arr.size() > max) {
                return;
            }

            ManagedProcessorMBean mp = mps.get(i);
            ManagedProcessorMBean nextMp = i < mps.size() - 1 ? mps.get(i + 1) : null;

            JsonObject jo = buildProcessorJson(camelContext, mp, nextMp);
            arr.add(jo);
        }
    }

    private static JsonObject buildProcessorJson(
            CamelContext camelContext, ManagedProcessorMBean mp, ManagedProcessorMBean nextMp) {
        JsonObject jo = new JsonObject();

        addProcessorBasicInfoJson(jo, mp);
        addProcessorSourceJson(jo, mp);
        addProcessorCodeSnippetJson(jo, camelContext, mp, nextMp);
        addProcessorDestinationJson(jo, camelContext, mp);
        jo.put("statistics", getStatsObject(mp));

        return jo;
    }

    private static void addProcessorBasicInfoJson(JsonObject jo, ManagedProcessorMBean mp) {
        jo.put("routeId", mp.getRouteId());
        jo.put("id", mp.getProcessorId());
        putIfNotNull(jo, "nodePrefixId", mp.getNodePrefixId());
        putIfNotNull(jo, "description", mp.getDescription());
        putIfNotNull(jo, "note", mp.getNote());
        jo.put("state", mp.getState());
        jo.put("disabled", mp.getDisabled());
        putIfNotNull(jo, "stepId", mp.getStepId());
        jo.put("processor", mp.getProcessorName());
        jo.put("level", mp.getLevel());
    }

    private static void addProcessorSourceJson(JsonObject jo, ManagedProcessorMBean mp) {
        if (mp.getSourceLocation() == null) {
            return;
        }
        String loc = mp.getSourceLocation();
        if (mp.getSourceLineNumber() != null) {
            loc += ":" + mp.getSourceLineNumber();
        }
        jo.put("source", loc);
    }

    private static void addProcessorCodeSnippetJson(
            JsonObject jo, CamelContext camelContext, ManagedProcessorMBean mp, ManagedProcessorMBean nextMp) {
        if (mp.getSourceLineNumber() == null) {
            return;
        }

        Integer end = calculateCodeSnippetEndLine(mp, nextMp);
        List<String> lines = ConsoleHelper.loadSourceLines(camelContext, mp.getSourceLocation(), mp.getSourceLineNumber(), end);

        if (lines.isEmpty()) {
            return;
        }

        JsonArray ca = buildCodeLinesArray(lines, mp.getSourceLineNumber());
        if (!ca.isEmpty()) {
            jo.put("code", ca);
        }
    }

    private static Integer calculateCodeSnippetEndLine(ManagedProcessorMBean mp, ManagedProcessorMBean nextMp) {
        Integer nextLine = nextMp != null ? nextMp.getSourceLineNumber() : null;
        if (nextLine == null) {
            return mp.getSourceLineNumber() + 5;
        }
        return Math.min(mp.getSourceLineNumber() + 5, nextLine);
    }

    private static JsonArray buildCodeLinesArray(List<String> lines, Integer startLine) {
        JsonArray ca = new JsonArray();
        Integer pos = startLine;
        for (String line : lines) {
            JsonObject c = new JsonObject();
            c.put("line", pos);
            c.put("code", Jsoner.escape(line));
            if (pos != null && pos.equals(startLine)) {
                c.put("match", true);
            }
            ca.add(c);
            if (pos != null) {
                pos++;
            }
        }
        return ca;
    }

    private static void addProcessorDestinationJson(JsonObject jo, CamelContext camelContext, ManagedProcessorMBean mp) {
        String destination = getDestination(camelContext, mp);
        if (destination != null) {
            jo.put("uri", destination);
        }
    }

    private static void putIfNotNull(JsonObject jo, String key, Object value) {
        if (value != null) {
            jo.put(key, value);
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
        String effectiveFilter = filter != null ? filter : "*";
        String[] patterns = effectiveFilter.split(",");

        List<ManagedProcessorMBean> mps = collectAllProcessors();
        List<ManagedProcessorMBean> matchingProcessors = filterProcessorsByPattern(mps, patterns);

        for (ManagedProcessorMBean mp : matchingProcessors) {
            executeCommand(command, mp);
        }
    }

    private List<ManagedProcessorMBean> collectAllProcessors() {
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
        return mps;
    }

    private List<ManagedProcessorMBean> filterProcessorsByPattern(List<ManagedProcessorMBean> mps, String[] patterns) {
        return mps.stream()
                .filter(mp -> matchesAnyPattern(mp, patterns))
                .toList();
    }

    private boolean matchesAnyPattern(ManagedProcessorMBean mp, String[] patterns) {
        for (String p : patterns) {
            if (PatternHelper.matchPattern(mp.getProcessorId(), p)
                    || PatternHelper.matchPattern(mp.getRouteId(), p)) {
                return true;
            }
        }
        return false;
    }

    private void executeCommand(String command, ManagedProcessorMBean mp) {
        try {
            switch (command) {
                case "start" -> mp.start();
                case "stop" -> mp.stop();
                case "disable" -> mp.disable();
                case "enable" -> mp.enable();
                default -> {
                }
            }
        } catch (Exception e) {
            LOG.warn("Error {} processor: {} due to: {}. This exception is ignored.", command, mp.getProcessorId(),
                    e.getMessage(), e);
        }
    }

}
