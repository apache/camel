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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Screen-capture render tests for the OverviewTab throughput chart. Verifies that sub-1.0 msg/s rates are displayed
 * correctly in the bar chart title, preventing future regressions where integer truncation would cause the chart to
 * show "0 msg/s" instead of e.g. "0.20 msg/s".
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-23865">CAMEL-23865</a>
 */
class OverviewTabThroughputRenderTest {

    private MonitorContext ctx;
    private MetricsCollector metrics;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();

        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";
        info.state = 5; // started
        info.exchangesTotal = 10;
        info.throughput = "0.20"; // 0.20 msg/s (e.g. timer with 5s period)

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";

        metrics = new MetricsCollector();
    }

    @AfterEach
    void tearDown() {
        Theme.resetForTesting();
    }

    /**
     * Verifies that a sub-1.0 msg/s throughput rate is visible in the chart title when throughput history contains
     * scaled values from the EWMA backend.
     */
    @Test
    void chartShowsFractionalThroughputInTitle() {
        // Populate throughput history with scaled 0.20 msg/s values
        // (0.20 * THROUGHPUT_SCALE = 20)
        Map<String, LinkedList<Long>> throughputHistory = metrics.getThroughputHistory();
        LinkedList<Long> history = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            history.add(20L); // 0.20 msg/s scaled by 100
        }
        throughputHistory.put("1234", history);

        Map<String, LinkedList<Long>> failedHistory = metrics.getFailedHistory();
        LinkedList<Long> fHistory = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            fHistory.add(0L);
        }
        failedHistory.put("1234", fHistory);

        OverviewTab tab = new OverviewTab(ctx, metrics, new HashSet<>(), () -> {
        });
        tab.chartMode = OverviewTab.CHART_ALL;

        String rendered = TuiTestHelper.renderToString(tab, 160, 40);

        assertTrue(rendered.contains("0.20 msg/s"),
                "Chart title should show '0.20 msg/s' for sub-1.0 throughput, but got:\n" + rendered);
    }

    /**
     * Verifies that zero throughput displays as "0 msg/s", not "0.00 msg/s".
     */
    @Test
    void chartShowsZeroThroughput() {
        Map<String, LinkedList<Long>> throughputHistory = metrics.getThroughputHistory();
        LinkedList<Long> history = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            history.add(0L);
        }
        throughputHistory.put("1234", history);

        Map<String, LinkedList<Long>> failedHistory = metrics.getFailedHistory();
        failedHistory.put("1234", new LinkedList<>(history));

        OverviewTab tab = new OverviewTab(ctx, metrics, new HashSet<>(), () -> {
        });
        tab.chartMode = OverviewTab.CHART_ALL;

        String rendered = TuiTestHelper.renderToString(tab, 160, 40);

        assertTrue(rendered.contains("0 msg/s"),
                "Chart title should show '0 msg/s' for zero throughput, but got:\n" + rendered);
    }

    /**
     * Verifies that high throughput (>= 10 msg/s) displays as integer without decimals.
     */
    @Test
    void chartShowsIntegerThroughputForHighRates() {
        Map<String, LinkedList<Long>> throughputHistory = metrics.getThroughputHistory();
        LinkedList<Long> history = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            history.add(4200L); // 42 msg/s scaled by 100
        }
        throughputHistory.put("1234", history);

        Map<String, LinkedList<Long>> failedHistory = metrics.getFailedHistory();
        LinkedList<Long> fHistory = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            fHistory.add(0L);
        }
        failedHistory.put("1234", fHistory);

        OverviewTab tab = new OverviewTab(ctx, metrics, new HashSet<>(), () -> {
        });
        tab.chartMode = OverviewTab.CHART_ALL;

        String rendered = TuiTestHelper.renderToString(tab, 160, 40);

        assertTrue(rendered.contains("42 msg/s"),
                "Chart title should show '42 msg/s' for high throughput, but got:\n" + rendered);
    }

    /**
     * Verifies that the chart with sub-1 throughput data does NOT show the integer "0 msg/s" — this was the original
     * bug where integer truncation made the chart appear empty.
     */
    @Test
    void chartDoesNotTruncateSubOneRateToZero() {
        Map<String, LinkedList<Long>> throughputHistory = metrics.getThroughputHistory();
        LinkedList<Long> history = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            history.add(20L); // 0.20 msg/s
        }
        throughputHistory.put("1234", history);

        Map<String, LinkedList<Long>> failedHistory = metrics.getFailedHistory();
        LinkedList<Long> fHistory = new LinkedList<>();
        for (int i = 0; i < 30; i++) {
            fHistory.add(0L);
        }
        failedHistory.put("1234", fHistory);

        OverviewTab tab = new OverviewTab(ctx, metrics, new HashSet<>(), () -> {
        });
        tab.chartMode = OverviewTab.CHART_ALL;

        String rendered = TuiTestHelper.renderToString(tab, 160, 40);

        // The old bug would show "0 msg/s" here due to integer truncation
        assertFalse(rendered.contains("Throughput: 0 msg/s"),
                "Chart should NOT truncate 0.20 msg/s to '0 msg/s' — integer truncation regression detected:\n"
                                                              + rendered);
    }
}
