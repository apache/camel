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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowHelperTest {

    @Test
    void compactSizeFitsTheFourColumnAxisLabel() {
        Map<Long, String> expected = Map.of(
                0L, "0",
                512L, "512",
                999L, "999",
                1024L, "1.0K",
                1536L, "1.5K",
                12288L, "12K",
                1023488L, "1.0M", // 999.5 KB rounds up into the megabyte range rather than to "1000K"
                1048576L, "1.0M",
                1258291L, "1.2M",
                15728640L, "15M");

        expected.forEach((size, label) -> {
            assertThat(FlowHelper.compactSize(size)).as("size %d", size).isEqualTo(label);
            assertThat(label.length()).isLessThanOrEqualTo(4);
        });
    }

    @Test
    void throughputAxisLabelConvertsScaledRatesBackToMessagesPerSecond() {
        // the chart data stays scaled by THROUGHPUT_SCALE, so the axis label has to undo the scaling
        assertThat(MetricsCollector.formatThroughput(0)).isEqualTo("0");
        assertThat(MetricsCollector.formatThroughput(20)).isEqualTo("0.20");
        assertThat(MetricsCollector.formatThroughput(150)).isEqualTo("1.5");
        assertThat(MetricsCollector.formatThroughput(700)).isEqualTo("7.0");
        assertThat(MetricsCollector.formatThroughput(1500)).isEqualTo("15");
        assertThat(MetricsCollector.formatThroughput(1234500)).isEqualTo("12K");
    }
}
