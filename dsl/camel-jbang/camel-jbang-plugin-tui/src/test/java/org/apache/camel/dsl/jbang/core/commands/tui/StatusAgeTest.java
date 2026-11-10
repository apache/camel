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

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The memory-leak example after a few hours: its heap is full, the garbage collector runs all the time, and it wrote
 * its status file the last time at 18:36. The TUI showed it running at 8.52 msg/s since; now it says it has no status.
 */
class StatusAgeTest {

    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "500874";
        info.name = "memory-leak";
        info.state = 5;
        info.throughput = "8.52";
        info.statusWrittenAt = System.currentTimeMillis();
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = info.pid;
    }

    @AfterEach
    void tearDown() {
        Theme.resetForTesting();
    }

    @Test
    void aStatusOfAFewSecondsAgoIsNoReason() {
        long now = System.currentTimeMillis();
        info.statusWrittenAt = now - 5_000;
        assertThat(StatusAge.stale(info, now)).isFalse();
        info.statusWrittenAt = now - StatusAge.AFTER_MILLIS - 1;
        assertThat(StatusAge.stale(info, now)).isTrue();
        // not known, or an opened project that does not run
        info.statusWrittenAt = 0;
        assertThat(StatusAge.stale(info, now)).isFalse();
        info.statusWrittenAt = now - 60_000;
        info.phantom = true;
        assertThat(StatusAge.stale(info, now)).isFalse();
    }

    @Test
    void theOverviewSaysItHasNoStatusInsteadOfItsLastRate() {
        stuckFor(4 * 60_000);

        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        }).showIntegrations();
        String row = TuiTestHelper.renderToString(tab, 160, 30).lines()
                .filter(l -> l.contains("memory-leak")).findFirst().orElseThrow();
        assertThat(row).contains(StatusAge.NO_STATUS).doesNotContain("Running").doesNotContain("8.52");

        JsonObject json = (JsonObject) ((JsonArray) tab.getTableDataAsJson().get("rows")).get(0);
        assertThat(json).containsEntry("noStatusSince", Instant.ofEpochMilli(info.statusWrittenAt).toString())
                .containsEntry("cpuPercent", 642L).containsEntry("throughput", null);
    }

    @Test
    void theWatchLineSaysSinceWhenAndWhatItsCpuSays() {
        stuckFor(4 * 60_000);

        String watch = text(StatusAge.watch(info));
        assertThat(watch).startsWith("✗ no status since ").contains("(4m")
                .contains("busy at 642% CPU, as when its heap is full")
                .contains("F10  stop or kill");

        info.cpuPercent = 0.4;
        assertThat(text(StatusAge.watch(info))).contains("idle at 0% CPU, as when a debugger holds it");
        // its CPU is not known before the second read
        info.cpuPercent = -1;
        assertThat(text(StatusAge.watch(info))).doesNotContain("CPU");
        // and a running integration has no such line
        info.statusWrittenAt = System.currentTimeMillis();
        assertThat(StatusAge.watch(info)).isEmpty();
    }

    @Test
    void aStopSaysItMayNotReachIt() {
        assertThat(StatusAge.stopAnswer(info)).isNull();
        stuckFor(4 * 60_000);
        assertThat(StatusAge.stopAnswer(info))
                .isEqualTo("Stopping memory-leak, but it wrote no status for 4m0s, so it may not read the stop either:"
                           + " kill stops it");
    }

    @Test
    void theCpuIsMeasuredBetweenTwoReadsWhileItHasNoStatus() {
        ProcessHandle self = ProcessHandle.current();
        StatusAge.observeCpu(info, null, self, 1_000);
        // it wrote its status: nothing to measure
        assertThat(info.cpuMillis).isEqualTo(-1);

        info.statusWrittenAt = System.currentTimeMillis() - 60_000;
        IntegrationInfo previous = new IntegrationInfo();
        previous.cpuMillis = 0;
        previous.cpuReadAt = 1_000;
        StatusAge.observeCpu(info, previous, self, 2_000);
        assertThat(info.cpuReadAt).isEqualTo(2_000);
        // the CPU of this JVM since it started, over one second
        assertThat(info.cpuPercent).isGreaterThan(0);
    }

    private void stuckFor(long millis) {
        info.statusWrittenAt = System.currentTimeMillis() - millis;
        info.cpuPercent = 642;
    }

    private static String text(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
