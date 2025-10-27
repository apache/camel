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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "route-structure", description = "Dump route structure")
public class RouteStructureDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes matching by route id, route uri, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    /**
     * Whether to dump in brief mode (only overall structure, and no detailed options or expressions)
     */
    public static final String BRIEF = "brief";

    public RouteStructureDevConsole() {
        super("camel", "route-structure", "Route Structure", "Dump route structure");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final String brief = (String) options.getOrDefault(BRIEF, "false");

        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            try {
                ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
                Route route = getCamelContext().getRoute(mrb.getRouteId());
                List<ModelDumpLine> lines = dumper.dumpStructure(getCamelContext(), route, "true".equalsIgnoreCase(brief));

                sb.append(String.format("    Id: %s", mrb.getRouteId()));
                if (mrb.getSourceLocation() != null) {
                    sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
                }
                sb.append("\n\n");
                for (ModelDumpLine line : lines) {
                    String pad = StringHelper.padString(line.level());
                    String num = "       ";
                    Integer idx = extractSourceLocationLineNumber(line.location());
                    if (idx != null) {
                        num = String.format("%4d:  ", idx);
                    }
                    sb.append(num).append(pad).append(line.code()).append("\n");
                }
                sb.append("\n");
            } catch (Exception e) {
                // ignore
            }

            sb.append("\n");
            return null;
        };
        doCall(options, task);
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        final String brief = (String) options.getOrDefault(BRIEF, "false");

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

            try {
                ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
                Route route = getCamelContext().getRoute(mrb.getRouteId());
                List<ModelDumpLine> lines = dumper.dumpStructure(getCamelContext(), route, "true".equalsIgnoreCase(brief));
                List<JsonObject> code = dumpAsJSon(lines);
                jo.put("code", code);
            } catch (Exception e) {
                // ignore
            }
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

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            List<Route> routes = getCamelContext().getRoutes();
            routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
            routes.stream()
                    .map(route -> mcc.getManagedRoute(route.getRouteId()))
                    .filter(Objects::nonNull)
                    .filter(r -> accept(r, filter))
                    .filter(r -> accept(r, subPath))
                    .sorted(RouteStructureDevConsole::sort)
                    .limit(max)
                    .forEach(task::apply);
        }
    }

    private static boolean accept(ManagedRouteMBean mrb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        String onlyName = LoggerHelper.sourceNameOnly(mrb.getSourceLocation());
        return PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getEndpointUri(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocationShort(), filter)
                || PatternHelper.matchPattern(onlyName, filter);
    }

    private static int sort(ManagedRouteMBean o1, ManagedRouteMBean o2) {
        // sort by id
        return o1.getRouteId().compareTo(o2.getRouteId());
    }

    private static List<JsonObject> dumpAsJSon(List<ModelDumpLine> lines) {
        List<JsonObject> code = new ArrayList<>();
        int counter = 0;
        for (var line : lines) {
            counter++;
            JsonObject c = new JsonObject();
            Integer idx = extractSourceLocationLineNumber(line.location());
            if (idx == null) {
                idx = counter;
            }
            c.put("line", idx);
            c.put("type", line.type());
            c.put("id", line.id());
            c.put("level", line.level());
            c.put("code", Jsoner.escape(line.code()));
            code.add(c);
        }
        return code;
    }

    private static Integer extractSourceLocationLineNumber(String location) {
        int cnt = StringHelper.countChar(location, ':');
        if (cnt > 0) {
            int pos = location.lastIndexOf(':');
            // in case pos is end of line
            if (pos < location.length() - 1) {
                String num = location.substring(pos + 1);
                try {
                    return Integer.valueOf(num);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

}
