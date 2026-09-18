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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearSystemProperty;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@code --record*} options.
 * <p>
 * The recording geometry used to be hardcoded at 200x50, which is too wide to embed in a documentation page, so it is
 * now configurable. A bad value must be rejected up front with a clear message rather than silently recording at the
 * wrong size, because the mistake would otherwise only surface once the cast is rendered.
 */
// The options are handed to TamboUI through process-wide system properties, so the tests that exercise them have to
// leave the JVM as they found it; junit-pioneer restores the original values after each test.
@ClearSystemProperty(key = "tamboui.record")
@ClearSystemProperty(key = "tamboui.record.config")
@ClearSystemProperty(key = "tamboui.record.width")
@ClearSystemProperty(key = "tamboui.record.height")
@ClearSystemProperty(key = "tamboui.record.duration")
@ClearSystemProperty(key = "tamboui.record.fps")
class CamelMonitorRecordOptionsTest {

    private final CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), getClass().getClassLoader());

    @Test
    void parsesColumnsAndRows() {
        assertThat(monitor.parseRecordSize("160x44")).containsExactly(160, 44);
    }

    @Test
    void acceptsUppercaseSeparatorAndSurroundingSpaces() {
        assertThat(monitor.parseRecordSize(" 160 X 44 ")).containsExactly(160, 44);
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThatThrownBy(() -> monitor.parseRecordSize("0x44"))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("--record-size");
    }

    @Test
    void rejectsMalformedValues() {
        assertThatThrownBy(() -> monitor.parseRecordSize("160"))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("--record-size");
        assertThatThrownBy(() -> monitor.parseRecordSize("wide x tall"))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("--record-size");
    }

    @Test
    void defaultsKeepTheHistoricRecordingGeometry() {
        // 200x50 was the previously hardcoded value; keeping it as the default means this change adds
        // an override without altering the output of an existing command line.
        assertThat(monitor.parseRecordSize(monitor.recordSize)).containsExactly(200, 50);
        assertThat(monitor.recordFps).isEqualTo(10);
        assertThat(monitor.recordDuration).isEqualTo(120000);
    }

    @Test
    void rejectsRecordingCombinedWithTheWebTerminal() {
        // tamboui.record* is JVM-wide, so a browser session started by TuiWebServer would silently inherit the
        // local session's recording and write to the same cast file. Failing up front beats that surprise.
        monitor.record = "demo.tape";
        monitor.web = true;

        assertThatThrownBy(monitor::configureRecording)
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("--record")
                .hasMessageContaining("--web");
    }

    @Test
    void configuringRecordingSetsEveryPropertyThatIsClearedAgainAfterwards() {
        // The set and the clear list have to stay in sync: a property added to configureRecording() but missing
        // from RECORD_PROPERTIES would keep recording enabled for the rest of the JVM's life.
        monitor.record = "demo.tape";
        monitor.recordSize = "160x44";

        monitor.configureRecording();

        assertThat(CamelMonitor.RECORD_PROPERTIES)
                .allSatisfy(key -> assertThat(System.getProperty(key)).as(key).isNotNull());
        assertThat(System.getProperty("tamboui.record.width")).isEqualTo("160");
        assertThat(System.getProperty("tamboui.record.height")).isEqualTo("44");

        monitor.clearRecordingProperties();

        assertThat(CamelMonitor.RECORD_PROPERTIES)
                .allSatisfy(key -> assertThat(System.getProperty(key)).as(key).isNull());
    }

    @Test
    void leavesTheRecordingPropertiesAloneWithoutTheRecordOption() {
        monitor.configureRecording();

        assertThat(CamelMonitor.RECORD_PROPERTIES)
                .allSatisfy(key -> assertThat(System.getProperty(key)).as(key).isNull());
    }
}
