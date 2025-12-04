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
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "route-dump", description = "Dump route in XML or YAML format")
public class RouteDumpDevConsole extends AbstractDevConsole {

    private static final Pattern XML_SOURCE_LOCATION_PATTERN = Pattern.compile("(\\ssourceLocation=\"(.*?)\")");
    private static final Pattern XML_SOURCE_LINE_PATTERN = Pattern.compile("(\\ssourceLineNumber=\"(.*?)\")");

    /**
     * To output in either xml, yaml, or text format
     */
    public static final String FORMAT = "format";

    /**
     * Filters the routes matching by route id, route uri, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    /**
     * Whether to expand URIs into separated key/value parameters
     */
    public static final String URI_AS_PARAMETERS = "uriAsParameters";

    public RouteDumpDevConsole() {
        super("camel", "route-dump", "Route Dump", "Dump route in XML or YAML format");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final String uriAsParameters = (String) options.getOrDefault(URI_AS_PARAMETERS, "false");

        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            String dump = null;
            try {
                String format = (String) options.get(FORMAT);
                if (format == null || "xml".equals(format)) {
                    dump = mrb.dumpRouteAsXml(true);
                } else if ("yaml".equals(format)) {
                    dump = mrb.dumpRouteAsYaml(true, "true".equals(uriAsParameters));
                }
            } catch (Exception e) {
                // ignore
            }
            sb.append(String.format("    Id: %s", mrb.getRouteId()));
            if (mrb.getSourceLocation() != null) {
                sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
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
    protected JsonObject doCallJson(Map<String, Object> options) {
        final String uriAsParameters = (String) options.getOrDefault(URI_AS_PARAMETERS, "false");

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
                String dump = null;
                String format = (String) options.get(FORMAT);
                if (format == null || "xml".equals(format)) {
                    jo.put("format", "xml");
                    dump = mrb.dumpRouteAsXml(true, false, true);
                } else if ("yaml".equals(format)) {
                    jo.put("format", "yaml");
                    dump = mrb.dumpRouteAsYaml(true, "true".equals(uriAsParameters), false, true);
                }
                if (dump != null) {
                    List<JsonObject> code;
                    if (format == null || "xml".equals(format)) {
                        code = xmlLoadSourceAsJson(new StringReader(dump));
                    } else {
                        code = yamlLoadSourceAsJson(new StringReader(dump));
                    }
                    if (code != null) {
                        jo.put("code", code);
                    }
                }
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

        ManagedCamelContext mcc =
                getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
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

    private static List<JsonObject> xmlLoadSourceAsJson(Reader reader) {
        List<JsonObject> code = new ArrayList<>();
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
                    JsonObject c = new JsonObject();
                    c.put("line", idx != null ? Integer.parseInt(idx) : -1);
                    c.put("code", Jsoner.escape(t));
                    code.add(c);
                }
            } while (t != null);
            IOHelper.close(lnr);
        } catch (Exception e) {
            // ignore
        }

        return code.isEmpty() ? null : code;
    }

    private static List<JsonObject> yamlLoadSourceAsJson(Reader reader) {
        List<JsonObject> code = new ArrayList<>();
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
                            JsonObject c = code.get(code.size() - 1);
                            try {
                                c.put("line", Integer.parseInt(idx));
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                    } else {
                        JsonObject c = new JsonObject();
                        c.put("code", Jsoner.escape(t));
                        c.put("line", -1);
                        code.add(c);
                    }
                }
            } while (t != null);
            IOHelper.close(lnr);
        } catch (Exception e) {
            // ignore
        }

        return code.isEmpty() ? null : code;
    }
}
