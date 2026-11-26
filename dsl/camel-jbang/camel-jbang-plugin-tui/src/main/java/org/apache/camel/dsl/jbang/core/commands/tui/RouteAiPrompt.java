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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Comparator;
import java.util.List;

/**
 * The questions the AI panel is opened with on the Routes tab (Shift+F8, CAMEL-25362): fix a route whose exchanges
 * fail, with the steps they fail at and the last error; or explain where the time goes in a route, with its slowest
 * steps. It is put in the input of the panel, for the user to send with Enter or change first.
 */
final class RouteAiPrompt {

    /** The most steps that are listed, so a big route does not flood the question. */
    static final int MAX_STEPS = 8;
    /** The slowest steps that are listed when explaining the time of a route. */
    static final int SLOWEST_STEPS = 5;

    private RouteAiPrompt() {
    }

    /** Whether exchanges failed in the route, or failures were handled by its error handler. */
    static boolean hasFailures(RouteInfo route) {
        return route.failed > 0 || route.failuresHandled > 0;
    }

    static String ofFailures(IntegrationInfo info, RouteInfo route) {
        StringBuilder sb = new StringBuilder();
        sb.append("Exchanges fail in route ").append(route.routeId).append(" of the running integration ")
                .append(name(info)).append(": ").append(route.failed).append(" of ").append(route.total)
                .append(" failed");
        if (route.failuresHandled > 0) {
            sb.append(", ").append(route.failuresHandled).append(" failures handled by the error handler");
        }
        if (route.redeliveries > 0) {
            sb.append(", ").append(route.redeliveries).append(" redeliveries");
        }
        if (route.sinceLastFailed != null && !route.sinceLastFailed.isBlank()) {
            sb.append("; the last failure ").append(route.sinceLastFailed).append(" ago");
        }
        sb.append(".\n");
        where(sb, route);
        List<ProcessorInfo> failing = route.processors.stream()
                .filter(p -> p.failed > 0 || p.failuresHandled > 0).limit(MAX_STEPS).toList();
        if (!failing.isEmpty()) {
            sb.append("The steps they fail at (an EIP counts the failures of the steps inside it too):\n");
            for (ProcessorInfo p : failing) {
                step(sb, p);
                sb.append(": ").append(p.failed).append(" failed");
                if (p.failuresHandled > 0) {
                    sb.append(", ").append(p.failuresHandled).append(" handled");
                }
                sb.append('\n');
            }
        }
        ErrorInfo last = lastError(info, route.routeId);
        if (last != null) {
            sb.append("The last error of the route: ").append(RuntimeFailures.failureOf(last));
            String at = RuntimeFailures.sourceOf(info.routes, last);
            if (last.nodeId != null) {
                sb.append(" (at ").append(last.nodeId).append(at != null ? ", " + at : "").append(')');
            }
            sb.append('\n');
        }
        sb.append("Find the cause from the integration's errors and log, then fix it with camel_edit_file (the fix may")
                .append(" belong on another line or in another route) and check the file with camel_validate_source.")
                .append(" Dev mode reloads the file when it is saved.");
        return sb.toString();
    }

    static String ofPerformance(IntegrationInfo info, RouteInfo route) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain where the time goes in route ").append(route.routeId).append(" of the running integration ")
                .append(name(info)).append(": ").append(route.total).append(" exchanges, mean ")
                .append(ChaosTab.shortTime(route.meanTime)).append(", max ").append(ChaosTab.shortTime(route.maxTime));
        if (route.p95Time >= 0) {
            sb.append(", p95 ").append(ChaosTab.shortTime(route.p95Time));
        }
        if (route.p99Time >= 0) {
            sb.append(", p99 ").append(ChaosTab.shortTime(route.p99Time));
        }
        if (route.throughput != null && !route.throughput.isBlank()) {
            sb.append(", ").append(route.throughput).append(" msg/s");
        }
        if (route.inflight > 0) {
            sb.append(", ").append(route.inflight).append(" in flight now");
        }
        sb.append(".\n");
        where(sb, route);
        List<ProcessorInfo> slowest = route.processors.stream().filter(p -> p.total > 0)
                .sorted(Comparator.comparingLong((ProcessorInfo p) -> p.meanTime).reversed())
                .limit(SLOWEST_STEPS).toList();
        if (!slowest.isEmpty()) {
            sb.append("The slowest steps by mean time (the time of an EIP includes the steps inside it):\n");
            for (ProcessorInfo p : slowest) {
                step(sb, p);
                sb.append(": mean ").append(ChaosTab.shortTime(p.meanTime)).append(", max ")
                        .append(ChaosTab.shortTime(p.maxTime)).append(", ").append(p.total).append(" calls\n");
            }
        }
        sb.append("Tell which steps take the time and why (such as a remote call, a slow query, a delay or a large")
                .append(" body), and suggest what could make the route faster, if anything. tui_get_table on the")
                .append(" Routes tab has the numbers. Make no changes to the files unless I agree.");
        return sb.toString();
    }

    private static void where(StringBuilder sb, RouteInfo route) {
        if (route.from != null) {
            sb.append("It starts from ").append(route.from);
            sb.append(route.source != null ? " and is at " + route.source : "").append(".\n");
        } else if (route.source != null) {
            sb.append("It is at ").append(route.source).append(".\n");
        }
    }

    private static void step(StringBuilder sb, ProcessorInfo p) {
        sb.append("  ").append(p.id != null ? p.id : "?");
        if (p.processor != null) {
            sb.append(" (").append(p.processor).append(')');
        }
        if (p.uri != null) {
            sb.append(" to ").append(p.uri);
        }
        if (p.source != null) {
            sb.append(" at ").append(p.source);
        }
    }

    /** The newest error of the route in the error registry; null when there is none. */
    static ErrorInfo lastError(IntegrationInfo info, String routeId) {
        ErrorInfo last = null;
        for (ErrorInfo e : info.errors) {
            if (routeId != null && routeId.equals(e.routeId) && (last == null || e.timestamp > last.timestamp)) {
                last = e;
            }
        }
        return last;
    }

    private static String name(IntegrationInfo info) {
        return info.name != null ? info.name : info.pid;
    }
}
