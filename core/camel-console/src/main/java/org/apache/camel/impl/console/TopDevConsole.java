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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedPerformanceCounterMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;

@DevConsole("top")
public class TopDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes and processors matching by route id, route uri, processor id, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    public TopDevConsole() {
        super("camel", "top", "Top", "Display the top routes");
    }

    @Override
    protected Object doCall(MediaType mediaType, Map<String, Object> options) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        final int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        // only text is supported
        StringBuilder sb = new StringBuilder();

        ManagedCamelContext mcc = getCamelContext().getExtension(ManagedCamelContext.class);
        if (mcc != null) {
            if (subPath == null || subPath.isBlank()) {
                topRoutes(filter, max, sb, mcc);
            } else {
                topProcessors(filter, subPath, max, sb, mcc);
            }
        }

        return sb.toString();
    }

    private void topRoutes(String filter, int max, StringBuilder sb, ManagedCamelContext mcc) {
        List<Route> routes = getCamelContext().getRoutes();
        routes.stream()
                .map(route -> mcc.getManagedRoute(route.getRouteId()))
                .filter(r -> acceptRoute(r, filter))
                .sorted(TopDevConsole::top)
                .limit(max)
                .forEach(mrb -> {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(String.format("    Route Id: %s", mrb.getRouteId()));
                    sb.append(String.format("\n    From: %s", mrb.getEndpointUri()));
                    if (mrb.getSourceLocation() != null) {
                        sb.append(String.format("\n    Source: %s", mrb.getSourceLocation()));
                    }
                    sb.append(String.format("\n    Total: %s", mrb.getExchangesTotal()));
                    sb.append(String.format("\n    Failed: %s", mrb.getExchangesFailed()));
                    sb.append(String.format("\n    Inflight: %s", mrb.getExchangesInflight()));
                    sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mrb.getMeanProcessingTime())));
                    sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mrb.getMaxProcessingTime())));
                    sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mrb.getMinProcessingTime())));
                    sb.append(String.format("\n    Delta Time: %s", TimeUtils.printDuration(mrb.getDeltaProcessingTime())));
                    sb.append(String.format("\n    Total Time: %s", TimeUtils.printDuration(mrb.getTotalProcessingTime())));
                    sb.append("\n");
                });
    }

    private void topProcessors(String filter, String subPath, int max, StringBuilder sb, ManagedCamelContext mcc) {
        List<Route> routes = getCamelContext().getRoutes();
        Collection<String> ids = new ArrayList<>();

        routes.stream()
                .map(route -> mcc.getManagedRoute(route.getRouteId()))
                .filter(r -> acceptRoute(r, subPath))
                .forEach(r -> {
                    try {
                        ids.addAll(r.processorIds());
                    } catch (Exception e) {
                        // ignore
                    }
                });

        ids.stream()
                .map(mcc::getManagedProcessor)
                .filter(p -> acceptProcessor(p, filter))
                .sorted(TopDevConsole::top)
                .limit(max)
                .forEach(mpb -> {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(String.format("    Route Id: %s", mpb.getRouteId()));
                    sb.append(String.format("\n    Processor Id: %s", mpb.getProcessorId()));
                    String loc = mpb.getSourceLocation();
                    StringBuilder code = new StringBuilder();
                    if (loc != null && mpb.getSourceLineNumber() != null) {
                        int line = mpb.getSourceLineNumber();
                        try {
                            Resource resource = getCamelContext().adapt(ExtendedCamelContext.class).getResourceLoader().resolveResource(loc);
                            if (resource != null) {
                                LineNumberReader reader = new LineNumberReader(resource.getReader());
                                for (int i = 1; i < line + 2; i++) {
                                    String t = reader.readLine();
                                    if (t != null) {
                                        int low = line - 2;
                                        int high = line + 3;
                                        if (i >= low && i <= high) {
                                            String arrow = i == line ? "-->" : "   ";
                                            code.append(String.format("\n        %s #%s %s", arrow, i, t));
                                        }
                                    }
                                }
                                IOHelper.close(reader);
                            }
                            loc += ":" + mpb.getSourceLineNumber();
                        } catch (Exception e) {
                            // ignore
                        }
                        // load source code line

                    }
                    if (loc != null) {
                        sb.append(String.format("\n    Source: %s", loc));
                        if (code.length() > 0) {
                            sb.append(code);
                        }
                    }
                    sb.append(String.format("\n    Total: %s", mpb.getExchangesTotal()));
                    sb.append(String.format("\n    Failed: %s", mpb.getExchangesFailed()));
                    sb.append(String.format("\n    Inflight: %s", mpb.getExchangesInflight()));
                    sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mpb.getMeanProcessingTime())));
                    sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mpb.getMaxProcessingTime())));
                    sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mpb.getMinProcessingTime())));
                    sb.append(String.format("\n    Delta Time: %s", TimeUtils.printDuration(mpb.getDeltaProcessingTime())));
                    sb.append(String.format("\n    Total Time: %s", TimeUtils.printDuration(mpb.getTotalProcessingTime())));
                    sb.append("\n");
                });
    }

    private static boolean acceptRoute(ManagedRouteMBean mrb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getEndpointUri(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocation(), filter);
    }

    private static boolean acceptProcessor(ManagedProcessorMBean mpb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(mpb.getProcessorId(), filter)
                || PatternHelper.matchPattern(mpb.getSourceLocation(), filter);
    }

    private static int top(ManagedPerformanceCounterMBean o1, ManagedPerformanceCounterMBean o2) {
        // sort for highest mean value as we want the slowest in the top
        long m1 = o1.getMeanProcessingTime();
        long m2 = o2.getMeanProcessingTime();
        if (m1 < m2) {
            return 1;
        } else if (m1 > m2) {
            return -1;
        } else {
            return 0;
        }
    }

}
