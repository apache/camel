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
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "route-dump", description = "Dump route in XML, YAML, or Java DSL format")
public class RouteDumpDevConsole extends AbstractDevConsole {

    public record CodeLine(
            @Metadata(description = "The source line number, or -1 when not known") int line,
            @Metadata(description = "The source code line") String code) {
    }

    public record RouteEntry(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The route's endpoint URI") String from,
            @Metadata(description = "The source location (only present when known)") String source,
            @Metadata(description = "The dump format, xml/yaml/java (only present when the dump succeeded)") String format,
            @Metadata(description = "The dumped route source code (only present when the dump succeeded)") List<CodeLine> code) {
    }

    public record Response(@Metadata(description = "The routes") List<RouteEntry> routes) {
    }

    private static final Pattern XML_SOURCE_LOCATION_PATTERN = Pattern.compile("(\\ssourceLocation=\"(.*?)\")");
    private static final Pattern XML_SOURCE_LINE_PATTERN = Pattern.compile("(\\ssourceLineNumber=\"(.*?)\")");

    @Metadata(label = "query", description = "To output in either xml, yaml, or java format", javaType = "java.lang.String",
              enums = "xml,yaml,java")
    public static final String FORMAT = "format";

    @Metadata(label = "query", description = "Filters the routes matching by route id, route uri, and source location",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of entries displayed", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query", description = "Whether to expand URIs into separated key/value parameters",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String URI_AS_PARAMETERS = "uriAsParameters";

    public RouteDumpDevConsole() {
        super("camel", "route-dump", "Route Dump", "Dump route in XML, YAML, or Java DSL format");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final boolean uriAsParameters = optionBoolean(options, URI_AS_PARAMETERS, false);

        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            String dump = null;
            try {
                String format = optionString(options, FORMAT);
                if (format == null || "xml".equals(format)) {
                    dump = mrb.dumpRouteAsXml(true);
                } else if ("yaml".equals(format)) {
                    dump = mrb.dumpRouteAsYaml(true, uriAsParameters);
                } else if ("java".equals(format)) {
                    dump = mrb.dumpRouteAsJava(true, false);
                }
            } catch (Exception e) {
                // ignore
            }
            sb.append(String.format("    Id: %s", mrb.getRouteId()));
            if (mrb.getSourceLocation() != null) {
                sb.append(String.format("%n    Source: %s", mrb.getSourceLocation()));
            }
            if (dump != null && !dump.isEmpty()) {
                sb.append("\n\n");
                for (String line : dump.split("\n")) {
                    sb.append("    ").append(line).append("\n");
                }
                sb.append("\n");
            }

            sb.append("\n");
            return null;
        };
        doCall(options, task);
        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        final boolean uriAsParameters = optionBoolean(options, URI_AS_PARAMETERS, false);

        final List<RouteEntry> list = new ArrayList<>();

        Function<ManagedRouteMBean, Object> task = mrb -> {
            String format = null;
            List<CodeLine> code = null;

            try {
                String dump = null;
                String requestedFormat = optionString(options, FORMAT);
                if (requestedFormat == null || "xml".equals(requestedFormat)) {
                    format = "xml";
                    dump = mrb.dumpRouteAsXml(true, false, true);
                } else if ("yaml".equals(requestedFormat)) {
                    format = "yaml";
                    dump = mrb.dumpRouteAsYaml(true, uriAsParameters, false, true);
                } else if ("java".equals(requestedFormat)) {
                    format = "java";
                    dump = mrb.dumpRouteAsJava(true, false, true);
                }
                if (dump != null) {
                    if (requestedFormat == null || "xml".equals(requestedFormat)) {
                        code = xmlLoadSourceAsJson(new StringReader(dump));
                    } else {
                        code = javaOrYamlLoadSourceAsJson(new StringReader(dump));
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            list.add(new RouteEntry(mrb.getRouteId(), mrb.getEndpointUri(), mrb.getSourceLocation(), format, code));
            return null;
        };
        doCall(options, task);

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
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
                    .sorted(RouteDumpDevConsole::sort)
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

    private static List<CodeLine> xmlLoadSourceAsJson(Reader reader) {
        List<CodeLine> code = new ArrayList<>();
        try {
            LineNumberReader lnr = new LineNumberReader(reader);
            String t;
            do {
                t = lnr.readLine();
                if (t != null) {
                    // extra source location from code line
                    String idx = null;
                    Matcher m = XML_SOURCE_LOCATION_PATTERN.matcher(t);
                    if (m.find()) {
                        t = m.replaceFirst("");
                    }
                    m = XML_SOURCE_LINE_PATTERN.matcher(t);
                    if (m.find()) {
                        idx = m.group(2);
                        t = m.replaceFirst("");
                    }
                    code.add(new CodeLine(idx != null ? Integer.parseInt(idx) : -1, Jsoner.escape(t)));
                }
            } while (t != null);
            IOHelper.close(lnr);
        } catch (Exception e) {
            // ignore
        }

        return code.isEmpty() ? null : code;
    }

    private static List<CodeLine> javaOrYamlLoadSourceAsJson(Reader reader) {
        List<CodeLine> code = new ArrayList<>();
        try {
            LineNumberReader lnr = new LineNumberReader(reader);
            String t;
            do {
                t = lnr.readLine();
                if (t != null) {
                    // extra source location from code line
                    if (t.contains("sourceLocation: ")) {
                        // skip this line
                    } else if (t.contains("sourceLineNumber: ")) {
                        String idx = StringHelper.after(t, "sourceLineNumber: ").trim();
                        if (!code.isEmpty()) {
                            // assign line number to previous code line
                            CodeLine prev = code.get(code.size() - 1);
                            try {
                                code.set(code.size() - 1, new CodeLine(Integer.parseInt(idx), prev.code()));
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                    } else {
                        code.add(new CodeLine(-1, Jsoner.escape(t)));
                    }
                }
            } while (t != null);
            IOHelper.close(lnr);
        } catch (Exception e) {
            // ignore
        }

        // merge trailing ; with previous code line (sourceLineNumber comments may have separated them)
        if (code.size() > 1) {
            CodeLine last = code.get(code.size() - 1);
            String lastCode = Jsoner.unescape(last.code()).trim();
            if (";".equals(lastCode)) {
                code.remove(code.size() - 1);
                CodeLine prev = code.get(code.size() - 1);
                code.set(code.size() - 1, new CodeLine(prev.line(), prev.code() + ";"));
            }
        }

        return code.isEmpty() ? null : code;
    }

}
