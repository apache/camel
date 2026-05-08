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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.NamedRoute;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.impl.console.ConsoleHelper.extractSourceLocationLineNumber;
import static org.apache.camel.impl.console.ConsoleHelper.extractSourceLocationNoLineNumber;

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
        Function<NamedRoute, Object> task = def -> {
            try {
                ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
                List<ModelDumpLine> lines
                        = dumper.dumpStructure(getCamelContext(), def.getRouteId(), "true".equalsIgnoreCase(brief));

                sb.append(String.format("    Id: %s", def.getRouteId()));
                if (def.getResource() != null) {
                    sb.append(String.format("%n    Source: %s",
                            extractSourceLocationNoLineNumber(def.getResource().getLocation())));
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
        final JsonArray list = new JsonArray();

        Function<NamedRoute, Object> task = def -> {
            JsonObject jo = new JsonObject();
            list.add(jo);

            jo.put("routeId", def.getRouteId());
            jo.put("from", def.getEndpointUrl());
            if (def.getResource() != null) {
                jo.put("source", extractSourceLocationNoLineNumber(def.getResource().getLocation()));
                Integer line = extractSourceLocationLineNumber(def.getResource().getLocation());
                if (line != null) {
                    jo.put("line", line);
                }
            }
            if (def.getDescription() != null) {
                jo.put("description", def.getDescription());
            }

            try {
                ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
                List<ModelDumpLine> lines
                        = dumper.dumpStructure(getCamelContext(), def.getRouteId(), "true".equalsIgnoreCase(brief));
                JsonArray code = dumpAsJSon(lines);
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

    protected void doCall(Map<String, Object> options, Function<NamedRoute, Object> task) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        var routes = getCamelContext().getNamedRouteDefinitions();
        routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
        routes.stream()
                .filter(r -> accept(r, filter))
                .filter(r -> accept(r, subPath))
                .limit(max)
                .forEach(task::apply);
    }

    private static boolean accept(NamedRoute route, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        String uri = route.getInput().getLabel();
        String loc = null;
        if (route.getResource() != null) {
            loc = LoggerHelper.sourceNameOnly(route.getResource().getLocation());
            loc = LoggerHelper.stripScheme(loc);
        }
        String onlyName = loc != null ? LoggerHelper.sourceNameOnly(loc) : null;
        return PatternHelper.matchPattern(route.getRouteId(), filter)
                || PatternHelper.matchPattern(uri, filter)
                || PatternHelper.matchPattern(loc, filter)
                || PatternHelper.matchPattern(onlyName, filter);
    }

    private static JsonArray dumpAsJSon(List<ModelDumpLine> lines) {
        JsonArray code = new JsonArray();
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
            if (line.description() != null) {
                c.put("description", line.description());
            }
            c.put("code", Jsoner.escape(line.code()));
            code.add(c);
        }
        return code;
    }

}
