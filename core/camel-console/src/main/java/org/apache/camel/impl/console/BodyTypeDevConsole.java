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

import org.apache.camel.NamedRoute;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.impl.console.ConsoleHelper.extractSourceLocationLineNumber;
import static org.apache.camel.impl.console.ConsoleHelper.extractSourceLocationNoLineNumber;

/**
 * The body types each step of the routes has been reached with, across the messages, as the
 * {@link org.apache.camel.spi.BodyTypeRegistry} keeps them while the message history is on (CAMEL-25750).
 */
@DevConsole(name = "body-type", description = "The body types each step of the routes has been reached with")
public class BodyTypeDevConsole extends AbstractDevConsole {

    public record StepEntry(
            @Metadata(description = "The processor ID") String id,
            @Metadata(description = "The processor type") String type,
            @Metadata(description = "The source line number (only present when known)") Integer line,
            @Metadata(description = "The code representation of this processor") String code,
            @Metadata(description = "The body types this processor has been reached with, across the messages, in the order first seen: more than one means the type varied") List<String> bodyTypes,
            @Metadata(description = "The kinds of the bodies this processor has been reached with: null, payload (text, bytes, a stream or a file), data (a Map or an object) or collection (a List, a Set, an array, or an iterator). More than one means the step needs attention") List<String> bodyKinds) {
    }

    public record RouteEntry(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The source location (only present when known)") String source,
            @Metadata(description = "The steps of the route that a message has reached, with their body types") List<StepEntry> steps) {
    }

    public record Response(
            @Metadata(description = "The routes with a step that a message has reached (the message history records the body types, such as in the dev profile)") List<RouteEntry> routes) {
    }

    @Metadata(label = "query", description = "Filters the routes matching by route id, route uri, and source location",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query",
              description = "Whether to include only the steps that have been reached with more than one body type",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String VARIED = "varied";

    public BodyTypeDevConsole() {
        super("camel", "body-type", "Body Type", "The body types each step of the routes has been reached with");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        List<RouteEntry> routes = routes(options);
        if (routes.isEmpty()) {
            return "No step has been reached by a message while the message history is on (the dev profile turns it on)\n";
        }
        StringBuilder sb = new StringBuilder();
        for (RouteEntry route : routes) {
            sb.append(String.format("    Route: %s", route.routeId()));
            if (route.source() != null) {
                sb.append(String.format(" (%s)", route.source()));
            }
            sb.append("\n\n");
            int width = 0;
            for (StepEntry step : route.steps()) {
                width = Math.max(width, Jsoner.unescape(step.code()).length());
            }
            for (StepEntry step : route.steps()) {
                String num = step.line() != null ? String.format("%4d:  ", step.line()) : "       ";
                String text = String.format("%-" + width + "s   body: %s", Jsoner.unescape(step.code()),
                        RouteStructureDevConsole.bodyTypesText(
                                new RouteStructureDevConsole.LineBodyTypes(step.bodyTypes(), step.bodyKinds())));
                sb.append(num).append(text).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        return JsonRecordSupport.toJsonObject(new Response(routes(options)));
    }

    private List<RouteEntry> routes(Map<String, Object> options) {
        final String filter = optionString(options, FILTER);
        final boolean varied = optionBoolean(options, VARIED, false);

        List<RouteEntry> answer = new ArrayList<>();
        List<NamedRoute> routes = getCamelContext().getNamedRouteDefinitions();
        routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
        for (NamedRoute route : routes) {
            if (!RouteStructureDevConsole.accept(route, filter)) {
                continue;
            }
            List<StepEntry> steps = steps(route.getRouteId(), varied);
            if (!steps.isEmpty()) {
                String source = route.getResource() != null
                        ? extractSourceLocationNoLineNumber(route.getResource().getLocation()) : null;
                answer.add(new RouteEntry(route.getRouteId(), source, steps));
            }
        }
        return answer;
    }

    private List<StepEntry> steps(String routeId, boolean varied) {
        List<StepEntry> answer = new ArrayList<>();
        try {
            ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
            List<ModelDumpLine> lines = dumper.dumpStructure(getCamelContext(), routeId, false);
            List<RouteStructureDevConsole.LineBodyTypes> bodyTypes
                    = RouteStructureDevConsole.bodyTypesPerLine(getCamelContext(), routeId, lines);
            for (int i = 0; i < lines.size(); i++) {
                RouteStructureDevConsole.LineBodyTypes types = bodyTypes.get(i);
                if (types == null || varied && types.types().size() < 2) {
                    continue;
                }
                ModelDumpLine line = lines.get(i);
                answer.add(new StepEntry(
                        line.id(), line.type(), extractSourceLocationLineNumber(line.location()), Jsoner.escape(line.code()),
                        types.types(), types.kinds()));
            }
        } catch (Exception e) {
            // the route has no model to dump, such as a route added without one
        }
        return answer;
    }
}
