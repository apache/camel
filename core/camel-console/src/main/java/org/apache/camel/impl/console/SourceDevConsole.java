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
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;

@DevConsole("source")
public class SourceDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes matching by route id, route uri, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    public SourceDevConsole() {
        super("camel", "source", "Source", "Display route source code");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final StringBuilder sb = new StringBuilder();
        Function<ManagedRouteMBean, Object> task = mrb -> {
            String loc = mrb.getSourceLocation();
            if (loc != null) {
                loc = LoggerHelper.stripSourceLocationLineNumber(loc);
                StringBuilder code = new StringBuilder();
                try {
                    Resource resource = getCamelContext().adapt(ExtendedCamelContext.class).getResourceLoader()
                            .resolveResource(loc);
                    if (resource != null) {
                        if (sb.length() > 0) {
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
                } catch (Exception e) {
                    // ignore
                }
                sb.append(String.format("    Id: %s", mrb.getRouteId()));
                if (mrb.getSourceLocation() != null) {
                    sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
                }
                if (code.length() > 0) {
                    sb.append(code);
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

            String loc = mrb.getSourceLocation();
            List<JsonObject> code = ConsoleHelper.loadSourceAsJson(getCamelContext(), loc);
            if (code != null) {
                jo.put("code", code);
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

        ManagedCamelContext mcc = getCamelContext().getExtension(ManagedCamelContext.class);
        if (mcc != null) {
            List<Route> routes = getCamelContext().getRoutes();
            routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
            routes.stream()
                    .map(route -> mcc.getManagedRoute(route.getRouteId()))
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

        return PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getEndpointUri(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocationShort(), filter);
    }

    private static int sort(ManagedRouteMBean o1, ManagedRouteMBean o2) {
        // sort by id
        return o1.getRouteId().compareTo(o2.getRouteId());
    }

}
