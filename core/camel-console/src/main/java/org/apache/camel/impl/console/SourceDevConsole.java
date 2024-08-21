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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "source", description = "Dump route source code")
public class SourceDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes matching by route id, route uri, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    /**
     * Whether to dump or list file names only
     */
    public static final String DUMP = "dump";

    /**
     * Whether to make downloading the source file easier from a web browser
     */
    public static final String DOWNLOAD = "download";

    public SourceDevConsole() {
        super("camel", "source", "Source", "Dump route source code");
    }

    @Override
    public boolean supportMediaType(MediaType mediaType) {
        // also supports raw
        return true;
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final StringBuilder sb = new StringBuilder();

        boolean download = "true".equals(options.getOrDefault(DOWNLOAD, "false"));
        if (download) {
            // use raw mode instead
            return doCallRaw(options);
        }

        boolean dump = "true".equals(options.getOrDefault(DUMP, "true"));
        Function<ManagedRouteMBean, Object> task = mrb -> {
            String loc = mrb.getSourceLocation();
            if (loc != null) {
                loc = LoggerHelper.stripSourceLocationLineNumber(loc);
                StringBuilder code = new StringBuilder();
                try {
                    Resource resource = PluginHelper.getResourceLoader(getCamelContext()).resolveResource(loc);
                    if (resource != null) {
                        if (dump) {
                            if (!sb.isEmpty()) {
                                sb.append("\n");
                            }
                            LineNumberReader reader = new LineNumberReader(resource.getReader());
                            int i = 0;
                            String t;
                            do {
                                t = reader.readLine();
                                if (t != null) {
                                    i++;
                                    code.append(String.format("\n    #%s %s", i, t));
                                }
                            } while (t != null);
                            IOHelper.close(reader);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                sb.append(String.format("    Id: %s", mrb.getRouteId()));
                if (mrb.getSourceLocation() != null) {
                    sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
                    sb.append(String.format("\n    File: %s", LoggerHelper.sourceNameOnly(loc)));
                }
                if (!code.isEmpty()) {
                    sb.append("\n");
                    sb.append(code);
                    sb.append("\n\n");
                }
            }
            sb.append("\n");
            return null;
        };
        doCall(options, task);
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        boolean dump = "true".equals(options.getOrDefault(DUMP, "true"));

        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();

        Function<ManagedRouteMBean, Object> task = mrb -> {
            JsonObject jo = new JsonObject();
            list.add(jo);

            jo.put("routeId", mrb.getRouteId());
            jo.put("from", mrb.getEndpointUri());
            String loc = mrb.getSourceLocation();
            if (loc != null) {
                jo.put("source", loc);
                jo.put("file", LoggerHelper.sourceNameOnly(loc));
                if (dump) {
                    List<JsonObject> code = ConsoleHelper.loadSourceAsJson(getCamelContext(), loc);
                    if (code != null) {
                        jo.put("code", code);
                    }
                }
            }
            return null;
        };
        doCall(options, task);
        root.put("routes", list);
        return root;
    }

    @Override
    protected String doCallRaw(Map<String, Object> options) {
        final StringBuilder sb = new StringBuilder();

        boolean dump = "true".equals(options.getOrDefault(DUMP, "true"));
        final AtomicInteger counter = new AtomicInteger();
        final AtomicReference<String> name = new AtomicReference<>();

        Function<ManagedRouteMBean, Object> task = mrb -> {
            String loc = mrb.getSourceLocation();
            if (loc != null) {
                loc = LoggerHelper.stripSourceLocationLineNumber(loc);
                StringBuilder code = new StringBuilder();
                try {
                    String onlyName = LoggerHelper.sourceNameOnly(mrb.getSourceLocation());
                    Resource resource = PluginHelper.getResourceLoader(getCamelContext()).resolveResource(loc);
                    if (resource != null) {
                        if (dump) {
                            // if we select only 1 file then remember the filename
                            if (counter.incrementAndGet() == 1) {
                                name.set(onlyName);
                            } else {
                                name.set(null);
                            }
                            if (!sb.isEmpty()) {
                                sb.append("\n");
                            }
                            String text = IOHelper.loadText(resource.getInputStream());
                            code.append(text);
                        } else {
                            // list of names
                            sb.append(onlyName);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                if (!code.isEmpty()) {
                    sb.append(code);
                }
            }
            sb.append("\n");
            return null;
        };
        doCall(options, task);

        if (name.get() != null) {
            options.put("Content-Disposition", String.format("attachment; filename=\"%s\"", name.get()));
        }

        return sb.toString();
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
                    .sorted(SourceDevConsole::sort)
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

}
