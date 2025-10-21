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

        for (ManagedProcessorMBean mp : mps) {
            if (counter.incrementAndGet() > max) {
                return;
            }
            sb.append("\n");
            sb.append(String.format("\n        Route Id: %s", mp.getRouteId()));
            sb.append(String.format("\n        Id: %s", mp.getProcessorId()));
            if (mp.getNodePrefixId() != null) {
                sb.append(String.format("\n        Node Prefix Id: %s", mp.getNodePrefixId()));
            }
            if (mp.getDescription() != null) {
                sb.append(String.format("\n        Description: %s", mp.getDescription()));
            }
            if (mp.getNote() != null) {
                sb.append(String.format("\n        Note: %s", mp.getNote()));
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
            sb.append(String.format("\n        State: %s", mp.getState()));
            sb.append(String.format("\n        Disabled: %s", mp.getDisabled()));
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

        for (ManagedProcessorMBean mp : mps) {
            if (arr.size() > max) {
                return;
            }
            JsonObject jo = new JsonObject();
            arr.add(jo);

            jo.put("routeId", mp.getRouteId());
            jo.put("id", mp.getProcessorId());
            if (mp.getNodePrefixId() != null) {
                jo.put("nodePrefixId", mp.getNodePrefixId());
            }
            if (mp.getDescription() != null) {
                jo.put("description", mp.getDescription());
            }
            if (mp.getNote() != null) {
                jo.put("note", mp.getNote());
            }
            if (mp.getSourceLocation() != null) {
                String loc = mp.getSourceLocation();
                if (mp.getSourceLineNumber() != null) {
                    loc += ":" + mp.getSourceLineNumber();
                }
                jo.put("source", loc);
            }
            jo.put("state", mp.getState());
            jo.put("disabled", mp.getDisabled());
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
