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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.tamboui.text.Span;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

/**
 * An integration that wrote no status of late. It writes its status file every second; when it has not for a while, and
 * its process is still there, the numbers the TUI has are those of that moment: the JVM is stuck (a heap that is full,
 * where the garbage collector runs all the time; a debugger that suspended it), or its CLI connector stopped
 * (CAMEL-26794). The screens then say so instead of showing it running at its last rate.
 */
final class StatusAge {

    /** The state of an integration without a status of late, in the STATUS column. */
    static final String NO_STATUS = "No status";

    // the status is written every second (100 ms while debugging), and a pause of the garbage collector takes seconds
    static final long AFTER_MILLIS = 15_000;
    // the CPU of a JVM that is busy without a status: the garbage collector, or a loop
    private static final double BUSY_CPU_PERCENT = 50;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US);

    private StatusAge() {
    }

    /** Whether the integration wrote no status for a while, at the time now (epoch millis). */
    static boolean stale(IntegrationInfo info, long now) {
        return !info.phantom && !info.vanishing && info.statusWrittenAt > 0 && now - info.statusWrittenAt > AFTER_MILLIS;
    }

    static boolean stale(IntegrationInfo info) {
        return stale(info, System.currentTimeMillis());
    }

    /** The state to show: {@link #NO_STATUS} when it wrote no status of late, else the state it has. */
    static String state(IntegrationInfo info, String state) {
        return stale(info) ? NO_STATUS : state;
    }

    /**
     * The CPU the process used since the last read, in percent of one core, while it writes no status: the JVM that is
     * busy (the garbage collector) is told from the one that is held (a debugger, a deadlock).
     *
     * @param now a MonotonicClock time
     */
    static void observeCpu(IntegrationInfo info, IntegrationInfo previous, ProcessHandle process, long now) {
        if (!stale(info)) {
            return;
        }
        info.cpuMillis = process.info().totalCpuDuration().map(Duration::toMillis).orElse(-1L);
        info.cpuReadAt = now;
        if (previous == null) {
            return;
        }
        if (info.cpuMillis >= 0 && previous.cpuMillis >= 0 && now > previous.cpuReadAt) {
            info.cpuPercent = 100.0 * (info.cpuMillis - previous.cpuMillis) / (now - previous.cpuReadAt);
        } else {
            info.cpuPercent = previous.cpuPercent;
        }
    }

    /** The Watch line of About for an integration that wrote no status of late, else none. */
    static List<Span> watch(IntegrationInfo info) {
        List<Span> spans = new ArrayList<>();
        if (!stale(info)) {
            return spans;
        }
        spans.add(Span.styled("✗ no status since " + clock(info.statusWrittenAt), Theme.error()));
        spans.add(Span.styled(" (" + TimeUtils.printSince(info.statusWrittenAt) + " ago)", Theme.muted()));
        String why = why(info.cpuPercent);
        if (why != null) {
            spans.add(Span.styled(": " + why, Theme.error()));
        }
        spans.add(Span.styled(" · its numbers are of then, and a stop may not reach it", Theme.muted()));
        spans.add(Span.raw("   "));
        spans.add(Span.styled(" F10 ", Theme.hintKey()));
        spans.add(Span.raw(" stop or kill"));
        return spans;
    }

    /** What the CPU of a JVM without a status says, or null when it is not known yet. */
    static String why(double cpuPercent) {
        if (cpuPercent < 0) {
            return null;
        }
        String cpu = String.format(Locale.US, "%.0f%% CPU", cpuPercent);
        return cpuPercent >= BUSY_CPU_PERCENT
                ? "busy at " + cpu + ", as when its heap is full and the garbage collector runs all the time"
                : "idle at " + cpu + ", as when a debugger holds it";
    }

    /** For the row of the integration that a tool reads: since when it wrote no status, and its CPU. */
    static void describe(IntegrationInfo info, JsonObject row) {
        if (!stale(info)) {
            return;
        }
        row.put("noStatusSince", Instant.ofEpochMilli(info.statusWrittenAt).toString());
        if (info.cpuPercent >= 0) {
            row.put("cpuPercent", Math.round(info.cpuPercent));
        }
    }

    /** The answer of a stop of an integration that wrote no status of late, else null. */
    static String stopAnswer(IntegrationInfo info) {
        if (info == null || !stale(info)) {
            return null;
        }
        return "Stopping " + info.name + ", but it wrote no status for " + TimeUtils.printSince(info.statusWrittenAt)
               + ", so it may not read the stop either: kill stops it";
    }

    private static String clock(long epochMillis) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(CLOCK);
    }
}
