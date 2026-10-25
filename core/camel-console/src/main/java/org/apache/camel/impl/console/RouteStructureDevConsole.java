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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedRoute;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.BodyTypeRegistry;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.impl.console.ConsoleHelper.extractSourceLocationLineNumber;
import static org.apache.camel.impl.console.ConsoleHelper.extractSourceLocationNoLineNumber;

@DevConsole(name = "route-structure", description = "Dump route structure")
public class RouteStructureDevConsole extends AbstractDevConsole {

    public record CodeEntry(
            @Metadata(description = "The source line number, or a sequential counter when not known") int line,
            @Metadata(description = "The processor type") String type,
            @Metadata(description = "The processor ID") String id,
            @Metadata(description = "The nesting level") int level,
            @Metadata(description = "The description (only present when known)") String description,
            @Metadata(description = "The code representation of this processor") String code,
            @Metadata(description = "The endpoint URI (only present when this processor references an endpoint)") String uri,
            @Metadata(description = "Whether the endpoint is remote (only present when true)") Boolean remote,
            @Metadata(description = "The body types this processor has been reached with, across the exchanges, in the order first seen: more than one means the type varied (only present when the message history recorded any, such as in the dev profile)") List<String> bodyTypes,
            @Metadata(description = "The kinds of the bodies this processor has been reached with: null, payload (text, bytes, a stream or a file), data (a Map or an object) or collection (a List, a Set, an array, or an iterator). More than one means the step needs attention: a step that writes or sends fails for the data, a step that reads fields fails for the payload (only present when the message history recorded any)") List<String> bodyKinds,
            @Metadata(description = "Runtime statistics for this route or processor, as an opaque JSON object (only present when metrics were requested)") Map<String, Object> statistics) {
    }

    /**
     * The body types and kinds a line of the route structure has been reached with.
     */
    record LineBodyTypes(List<String> types, List<String> kinds) {
    }

    public record RouteEntry(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The route's endpoint URI") String from,
            @Metadata(description = "The source location (only present when known)") String source,
            @Metadata(description = "The source line number (only present when known)") Integer line,
            @Metadata(description = "The route description (only present when configured)") String description,
            @Metadata(description = "The checksum of the route as written, the same in every run: the facts a tool keeps per line hold for this version of the route (only present when known, as it needs camel-xml-io)") String checksum,
            @Metadata(description = "The route's structure, one entry per processor (only present when the dump succeeded)") List<CodeEntry> code) {
    }

    public record Response(@Metadata(description = "The routes") List<RouteEntry> routes) {
    }

    @Metadata(label = "query", description = "Filters the routes matching by route id, route uri, and source location",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of entries displayed", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query",
              description = "Whether to dump in brief mode (only overall structure, and no detailed options or expressions)",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String BRIEF = "brief";

    @Metadata(label = "query",
              description = "Whether to include metrics such as number of messages processed (from JMX)",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String METRIC = "metric";

    /**
     * The widest code before the body types column of the text output.
     */
    private static final int BODY_TYPES_COLUMN = 90;

    public RouteStructureDevConsole() {
        super("camel", "route-structure", "Route Structure", "Dump route structure");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final boolean brief = optionBoolean(options, BRIEF, false);

        final StringBuilder sb = new StringBuilder();
        Function<NamedRoute, Object> task = def -> {
            try {
                ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
                List<ModelDumpLine> lines
                        = dumper.dumpStructure(getCamelContext(), def.getRouteId(), brief);
                List<LineBodyTypes> bodyTypes = bodyTypesPerLine(getCamelContext(), def.getRouteId(), lines);

                sb.append(String.format("    Id: %s", def.getRouteId()));
                if (def.getResource() != null) {
                    sb.append(String.format("%n    Source: %s",
                            extractSourceLocationNoLineNumber(def.getResource().getLocation())));
                }
                if (def.getChecksum() != null) {
                    sb.append(String.format("%n    Checksum: %s", def.getChecksum()));
                }
                sb.append("\n\n");
                // the body types line up in a column after the code (a long line pushes only its own types out)
                int width = 0;
                if (bodyTypes.stream().anyMatch(Objects::nonNull)) {
                    for (ModelDumpLine line : lines) {
                        width = Math.max(width, StringHelper.padString(line.level()).length() + line.code().length());
                    }
                    width = Math.min(width, BODY_TYPES_COLUMN);
                }
                for (int i = 0; i < lines.size(); i++) {
                    ModelDumpLine line = lines.get(i);
                    String pad = StringHelper.padString(line.level());
                    String num = "       ";
                    Integer idx = extractSourceLocationLineNumber(line.location());
                    if (idx != null) {
                        num = String.format("%4d:  ", idx);
                    }
                    String text = pad + line.code();
                    LineBodyTypes types = bodyTypes.get(i);
                    if (types != null) {
                        text = String.format("%-" + width + "s   body: %s", text, bodyTypesText(types));
                    }
                    sb.append(num).append(text).append("\n");
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
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        final boolean brief = optionBoolean(options, BRIEF, false);
        final boolean metric = optionBoolean(options, METRIC, false);

        final List<RouteEntry> list = new ArrayList<>();

        Function<NamedRoute, Object> task = def -> {
            String source = null;
            Integer line = null;
            if (def.getResource() != null) {
                source = extractSourceLocationNoLineNumber(def.getResource().getLocation());
                line = extractSourceLocationLineNumber(def.getResource().getLocation());
            }

            List<CodeEntry> code = null;
            try {
                ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
                List<ModelDumpLine> lines
                        = dumper.dumpStructure(getCamelContext(), def.getRouteId(), brief);
                code = buildCodeEntries(getCamelContext(), def.getRouteId(), lines, metric);
            } catch (Exception e) {
                // ignore
            }

            list.add(new RouteEntry(
                    def.getRouteId(), def.getEndpointUrl(), source, line, def.getDescription(), def.getChecksum(), code));
            return null;
        };
        doCall(options, task);

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

    protected void doCall(Map<String, Object> options, Function<NamedRoute, Object> task) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = optionString(options, FILTER);
        final int max = optionInt(options, LIMIT, Integer.MAX_VALUE);

        var routes = getCamelContext().getNamedRouteDefinitions();
        routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
        routes.stream()
                .filter(r -> accept(r, filter))
                .filter(r -> accept(r, subPath))
                .limit(max)
                .forEach(task::apply);
    }

    static boolean accept(NamedRoute route, String filter) {
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

    private List<CodeEntry> buildCodeEntries(
            CamelContext camelContext, String routeId, List<ModelDumpLine> lines, boolean metric) {
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        List<LineBodyTypes> bodyTypes = bodyTypesPerLine(camelContext, routeId, lines);

        List<CodeEntry> code = new ArrayList<>();
        int counter = 0;
        for (var line : lines) {
            counter++;
            Integer idx = extractSourceLocationLineNumber(line.location());
            if (idx == null) {
                idx = counter;
            }
            String uri = line.uri() != null ? Jsoner.escape(line.uri()) : null;
            Boolean remote = line.uri() != null && line.remote() ? true : null;

            Map<String, Object> statistics = null;
            if (metric && mcc != null) {
                if (counter <= 2) {
                    ManagedRouteMBean mrb = mcc.getManagedRoute(line.id());
                    if (mrb != null) {
                        JsonObject stats = RouteDevConsole.gatherRouteStats(camelContext, mrb);
                        if (counter == 2) {
                            // from is route stats minus a few values
                            stats.remove("coverage");
                            stats.remove("load01");
                            stats.remove("load05");
                            stats.remove("load15");
                        }
                        statistics = stats;
                    }
                } else {
                    ManagedProcessorMBean mp = mcc.getManagedProcessor(line.id());
                    if (mp != null) {
                        statistics = ProcessorDevConsole.gatherProcessorStats(mp);
                    }
                }
            }

            LineBodyTypes types = bodyTypes.get(counter - 1);
            code.add(new CodeEntry(
                    idx, line.type(), line.id(), line.level(), line.description(), Jsoner.escape(line.code()), uri,
                    remote, types != null ? types.types() : null, types != null ? types.kinds() : null, statistics));
        }
        return code;
    }

    /**
     * The body types each line of the route structure has been reached with, as the message history recorded them: null
     * for the route and its from (the first two lines), and for a processor no exchange has reached. The first
     * processor has the body as the from gave it.
     */
    static List<LineBodyTypes> bodyTypesPerLine(
            CamelContext camelContext, String routeId, List<ModelDumpLine> lines) {
        BodyTypeRegistry registry = camelContext.getCamelContextExtension().getContextPlugin(BodyTypeRegistry.class);
        List<LineBodyTypes> answer = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            LineBodyTypes types = null;
            String id = lines.get(i).id();
            if (registry != null && i >= 2 && id != null) {
                List<String> found = registry.getBodyTypes(routeId, id);
                if (!found.isEmpty()) {
                    types = new LineBodyTypes(found, kindNames(registry.getBodyKinds(routeId, id)));
                }
            }
            answer.add(types);
        }
        return answer;
    }

    /**
     * The kinds by their names in the JSON and text output: null, payload, data and collection.
     */
    static List<String> kindNames(List<BodyTypeRegistry.Kind> kinds) {
        List<String> answer = new ArrayList<>(kinds.size());
        for (BodyTypeRegistry.Kind kind : kinds) {
            answer.add(kind.name().toLowerCase(Locale.ROOT));
        }
        return answer;
    }

    /**
     * The body types by their simple class names, such as {@code String | LinkedHashMap}, followed by the kinds when
     * there is more than one: {@code String | LinkedHashMap (payload and data)}.
     */
    static String bodyTypesText(LineBodyTypes types) {
        StringJoiner sj = new StringJoiner(" | ");
        for (String type : types.types()) {
            sj.add(StringHelper.afterLast(type, ".", type));
        }
        String answer = sj.toString();
        if (types.kinds().size() > 1) {
            answer += " (" + mixedKindsText(types.kinds()) + ")";
        }
        return answer;
    }

    /**
     * Kinds in words: {@code payload and data}, or {@code null, payload and data}.
     */
    static String mixedKindsText(List<String> kinds) {
        if (kinds.size() < 2) {
            return String.join("", kinds);
        }
        return String.join(", ", kinds.subList(0, kinds.size() - 1)) + " and " + kinds.get(kinds.size() - 1);
    }

}
