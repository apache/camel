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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.TimeUtils;

/**
 * The question the AI panel is opened with to fix a health check that is not UP (Shift+F8 on the Health tab,
 * CAMEL-25362): the check, what being DOWN means for the integration, its message, details and error, and the source of
 * its route. The cause may be in the routes or the configuration, or outside the integration (a broker that is not
 * running), so the AI is asked to tell which before it changes a file. It is put in the input of the panel, for the
 * user to send with Enter or change first.
 */
final class HealthAiPrompt {

    /** The most lines of a stack trace that are quoted, so a deep trace does not flood the question. */
    static final int MAX_TRACE_LINES = 30;
    /** The frames quoted under each exception of a stack trace (the exception itself and each Caused by). */
    static final int FRAMES_PER_EXCEPTION = 3;

    /** The details every check has (counts and times): the page already says them, the AI needs none of them. */
    private static final Set<String> BOOKKEEPING = Set.of(
            "invocation.count", "invocation.time", "invocation.attempted",
            "failure.count", "failure.start.time", "failure.time", "failure.error.count", "failure.error.message",
            "success.count", "success.start.time", "success.time");

    private HealthAiPrompt() {
    }

    /**
     * @param checks the checks to ask about, those that are not UP
     * @param now    the time now (epoch millis), to say how long a check has been DOWN
     */
    static String of(IntegrationInfo info, List<HealthCheckInfo> checks, long now) {
        StringBuilder sb = new StringBuilder();
        String name = info.name != null ? info.name : info.pid;
        if (checks.size() == 1) {
            HealthCheckInfo hc = checks.get(0);
            sb.append("The health check ").append(label(hc)).append(" of the running integration ").append(name)
                    .append(" is ").append(hc.state).append(".\n");
            check(sb, info, hc, now);
        } else {
            sb.append("These health checks of the running integration ").append(name).append(" are not UP:\n");
            for (HealthCheckInfo hc : checks) {
                sb.append("\nThe check ").append(label(hc)).append(" is ").append(hc.state).append(".\n");
                check(sb, info, hc, now);
            }
            sb.append('\n');
        }
        sb.append("Find the cause first. When it is in the project's routes or configuration, fix it with")
                .append(" camel_edit_file and check the file with camel_validate_source; dev mode reloads the file")
                .append(" when it is saved. When it is outside the integration, such as a broker, database or server")
                .append(" that is not running or cannot be reached, say how to start or reach it, and make no changes")
                .append(" to the files.");
        return sb.toString();
    }

    private static void check(StringBuilder sb, IntegrationInfo info, HealthCheckInfo hc, long now) {
        if (hc.readiness && hc.liveness) {
            sb.append("It is a readiness and liveness check: the integration is taken out of service and restarted.\n");
        } else if (hc.readiness) {
            sb.append("It is a readiness check: while it is not UP, the integration is taken out of service.\n");
        } else if (hc.liveness) {
            sb.append("It is a liveness check: while it is not UP, the integration is restarted.\n");
        }
        if (hc.message != null && !hc.message.isBlank()) {
            sb.append("Its message: ").append(hc.message.strip()).append('\n');
        }
        if (hc.since > 0 || hc.failures > 0) {
            sb.append("It has been ").append(hc.state);
            if (hc.since > 0) {
                sb.append(" for ").append(TimeUtils.printDuration(Math.max(0, now - hc.since), false));
            }
            if (hc.failures > 0) {
                sb.append(", and failed ").append(hc.failures).append(" of ").append(hc.invocations)
                        .append(" times it ran");
            }
            sb.append(".\n");
        }
        StringBuilder details = new StringBuilder();
        for (Map.Entry<String, String> e : hc.details.entrySet()) {
            if (!BOOKKEEPING.contains(e.getKey()) && !e.getKey().startsWith("check.")) {
                details.append(details.isEmpty() ? "" : ", ").append(e.getKey()).append('=').append(e.getValue());
            }
        }
        if (!details.isEmpty()) {
            sb.append("Its details: ").append(details).append('\n');
        }
        String route = routeOf(hc);
        if (route != null) {
            for (RouteInfo r : info.routes) {
                if (route.equals(r.routeId) && r.source != null) {
                    sb.append("Its route ").append(route).append(" is at ").append(r.source).append(".\n");
                    break;
                }
            }
        }
        String trace = trace(hc.stackTrace);
        if (trace != null) {
            sb.append("The error:\n```\n").append(trace).append("```\n");
        }
    }

    /** The group and the name of a check: consumers/consumer:orders. */
    private static String label(HealthCheckInfo hc) {
        String name = hc.name != null ? hc.name : "";
        return hc.group != null && !hc.group.isEmpty() ? hc.group + "/" + name : name;
    }

    /** The route of a check: its route.id detail, else the name of a route or consumer check (route:orders). */
    static String routeOf(HealthCheckInfo hc) {
        String route = hc.details.get("route.id");
        if (route != null) {
            return route;
        }
        String name = hc.name;
        if (name != null) {
            for (String prefix : List.of("route:", "consumer:")) {
                if (name.startsWith(prefix)) {
                    return name.substring(prefix.length());
                }
            }
        }
        return null;
    }

    /**
     * The stack trace, shortened: each exception (the first line and each Caused by) with its first few frames, at most
     * {@link #MAX_TRACE_LINES} lines; null when there is none.
     */
    static String trace(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int frames = 0;
        boolean skipped = false;
        for (String line : lines) {
            String text = line.stripTrailing();
            if (text.isBlank()) {
                continue;
            }
            boolean frame = text.stripLeading().startsWith("at ") || text.stripLeading().startsWith("...");
            if (frame) {
                if (++frames > FRAMES_PER_EXCEPTION) {
                    skipped = true;
                    continue;
                }
            } else {
                frames = 0;
                if (skipped) {
                    sb.append("\t...\n");
                    count++;
                    skipped = false;
                }
            }
            if (count >= MAX_TRACE_LINES) {
                sb.append("\t...\n");
                return sb.toString();
            }
            sb.append(text).append('\n');
            count++;
        }
        if (skipped) {
            sb.append("\t...\n");
        }
        return sb.toString();
    }
}
