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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineLogTest {

    // 2027-01-15 08:00:00 UTC
    private static final long T0 = LocalDateTime.of(2027, 1, 15, 8, 0, 0).toEpochSecond(ZoneOffset.UTC);

    @TempDir
    Path dir;

    private final Timeline timeline = new Timeline();
    private final TimelineLog log = new TimelineLog(ZoneOffset.UTC);

    private static String line(int second, String level, String message) {
        return String.format("2027-01-15 08:00:%02d.123  %-5s 4711 --- [main] org.acme.Shop : %s%n", second, level, message);
    }

    private long[] series(String label) {
        return timeline.causes("4711", T0 + 59, 60).stream().filter(c -> c.label().equals(label))
                .map(Timeline.CauseSeries::values).findFirst().orElse(new long[60]);
    }

    @Test
    void theWarnAndErrorLinesPerSecondAsTheLogGrows() throws Exception {
        Path file = dir.resolve("4711.log");
        Files.writeString(file, line(1, "INFO", "started") + line(2, "WARN", "slow") + line(2, "WARN", "slower")
                                + line(3, "ERROR", "boom")
                                + "java.lang.IllegalStateException: boom\n\tat org.acme.Shop.run(Shop.java:1)\n");
        log.read("4711", file, timeline);
        assertThat(series("log WARN")[2]).isEqualTo(2);
        assertThat(series("log ERROR")[3]).isEqualTo(1);
        // the stack trace is part of its line
        assertThat(Arrays.stream(series("log ERROR")).sum()).isEqualTo(1);

        // what is added is read the next time, a line that is not whole yet the time after
        Files.writeString(file, line(5, "ERROR", "again") + "2027-01-15 08:00:06.000  WARN 4711 --- [main] half",
                StandardOpenOption.APPEND);
        log.read("4711", file, timeline);
        assertThat(series("log ERROR")[5]).isEqualTo(1);
        assertThat(series("log WARN")[6]).isZero();
        Files.writeString(file, " a line\n", StandardOpenOption.APPEND);
        log.read("4711", file, timeline);
        assertThat(series("log WARN")[6]).isEqualTo(1);
        // read once only
        log.read("4711", file, timeline);
        assertThat(Arrays.stream(series("log WARN")).sum()).isEqualTo(3);
    }

    @Test
    void aBigLogIsReadFromItsLastPart() throws Exception {
        Path file = dir.resolve("4711.log");
        StringBuilder sb = new StringBuilder();
        String old = line(10, "ERROR", "long ago");
        while (sb.length() < TimelineLog.FIRST_READ * 2) {
            sb.append(old);
        }
        sb.append(line(20, "WARN", "now"));
        Files.writeString(file, sb.toString());
        log.read("4711", file, timeline);
        assertThat(series("log WARN")[20]).isEqualTo(1);
        // only the lines of the last part, and not the line cut in two at its start
        long errors = Arrays.stream(series("log ERROR")).sum();
        assertThat(errors).isLessThan((long) (TimelineLog.FIRST_READ / old.length()) + 1).isGreaterThan(0);
    }

    @Test
    void theLogIsNotACauseWhenAnExchangeFailed() throws Exception {
        TimelineJson t = new TimelineJson(T0 + 59, 60);
        long[] counts = new long[60];
        counts[3] = 1;
        t.route("orders", "failed", 3, 1).error("orders", "java.io.IOException", null, null, counts);
        timeline.merge("4711", t.json(), true);
        Path file = dir.resolve("4711.log");
        Files.write(file, List.of(line(3, "ERROR", "failed").trim()), StandardCharsets.UTF_8);
        log.read("4711", file, timeline);
        assertThat(timeline.causes("4711", T0 + 59, 60)).extracting(Timeline.CauseSeries::label)
                .containsExactly("IOException");
    }

    @Test
    void colouredLinesCountToo() throws Exception {
        Path file = dir.resolve("4711.log");
        Files.writeString(file, "\u001B[2m2027-01-15 08:00:04.000\u001B[0m \u001B[33m WARN\u001B[0m 4711 --- [main] x\n");
        log.read("4711", file, timeline);
        assertThat(series("log WARN")[4]).isEqualTo(1);
    }

    @Test
    void theWarnOfChaosSwitchedOnIsNotCounted() throws Exception {
        Path file = dir.resolve("4711.log");
        Files.writeString(file, line(4, "WARN", "Chaos is switched on (profile dev): rules can fail, delay, drop,"
                                                + " duplicate and corrupt the messages of this application. Do not use in"
                                                + " production. Seed: 42, rules from camel.chaos.rules: 0")
                                + line(5, "WARN", "slow"));
        log.read("4711", file, timeline);
        assertThat(series("log WARN")[4]).isZero();
        assertThat(series("log WARN")[5]).isEqualTo(1);
    }
}
