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

/**
 * The question the AI panel is opened with to explain the timeline (Shift+F8 on the Timeline screen, CAMEL-26691): what
 * went wrong in the window and when, and what it did to each route, so the AI can tell the story of cause and effect in
 * plain words. It is put in the input of the panel, for the user to send with Enter or change first.
 */
final class TimelineAiPrompt {

    private TimelineAiPrompt() {
    }

    static String of(
            IntegrationInfo info, List<Timeline.CauseSeries> causes, List<Timeline.RouteSeries> routes, int seconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain what happened in the last ").append(TimelineTab.axisTime(seconds))
                .append(" of the running integration ").append(info.name != null ? info.name : info.pid)
                .append(", as its timeline shows it: what went wrong and when, and what it did to the routes.\n");
        boolean anyCause = false;
        for (Timeline.CauseSeries c : causes) {
            long total = total(c.values());
            if (total == 0) {
                continue;
            }
            if (!anyCause) {
                sb.append("The causes, per second:\n");
                anyCause = true;
            }
            sb.append("  ").append(c.rule() != null ? "chaos rule " : "error ").append(c.label()).append(": ")
                    .append(total).append(" in ").append(active(c.values())).append(" seconds, ")
                    .append(when(c.values())).append('\n');
        }
        if (!anyCause) {
            sb.append("No errors and no chaos faults in that time.\n");
        }
        sb.append("The routes:\n");
        for (Timeline.RouteSeries r : routes) {
            long completed = r.total(r.completed());
            long recovered = r.total(r.recovered());
            long handled = r.total(r.handled());
            long failed = r.total(r.failed());
            sb.append("  ").append(r.route()).append(": ").append(completed).append(" completed, ").append(recovered)
                    .append(" recovered by redelivery, ").append(handled).append(" handled by the error handler, ")
                    .append(failed).append(" failed");
            if (handled + failed > 0) {
                long[] trouble = new long[r.handled().length];
                for (int i = 0; i < trouble.length; i++) {
                    trouble[i] = Math.max(0, r.handled()[i]) + Math.max(0, r.failed()[i]);
                }
                sb.append(" (handled or failed ").append(when(trouble)).append(')');
            }
            long down = r.total(r.down());
            if (down > 0) {
                sb.append(", down ").append(down).append("s by a chaos rule");
            }
            long maxMean = 0;
            for (long m : r.mean()) {
                maxMean = Math.max(maxMean, m);
            }
            if (maxMean > 0) {
                sb.append(", slowest second ").append(ChaosTab.shortTime(maxMean)).append(" mean");
            }
            sb.append('\n');
        }
        sb.append("Tell it as a short story of cause and effect, from left to right in time: which errors or faults led")
                .append(" to which outcome in which route, whether a message was lost, and whether it recovered by")
                .append(" itself. Then suggest what to change if the routes did not cope well. tui_get_table on the")
                .append(" Timeline tab has the series per second. Make no changes to the files unless I agree.");
        return sb.toString();
    }

    /** When the non-zero seconds were, as seconds ago: from 42s to 30s ago. */
    private static String when(long[] values) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first < 0) {
            return "never";
        }
        int fromAgo = values.length - 1 - first;
        int toAgo = values.length - 1 - last;
        if (fromAgo == toAgo) {
            return toAgo == 0 ? "in the last second" : toAgo + "s ago";
        }
        return "from " + fromAgo + "s to " + (toAgo == 0 ? "now" : toAgo + "s ago");
    }

    private static int active(long[] values) {
        int n = 0;
        for (long v : values) {
            if (v > 0) {
                n++;
            }
        }
        return n;
    }

    private static long total(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += Math.max(0, v);
        }
        return sum;
    }
}
