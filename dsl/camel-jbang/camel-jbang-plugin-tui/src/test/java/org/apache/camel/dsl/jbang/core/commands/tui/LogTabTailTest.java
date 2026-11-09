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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Log tab opens a log at its end, which starts inside a line: that line is left out of the first read, and comes
 * back whole with the older lines.
 */
class LogTabTailTest {

    @TempDir
    Path dir;

    @Test
    void theFirstReadStartsAtAWholeLine() throws Exception {
        List<String> written = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; sb.length() < 3 * LogTab.INITIAL_TAIL_BYTES; i++) {
            String line = "2026-11-09 15:13:22.234  INFO 292054 --- [timer://stock] route : Stock check " + i
                          + " é in stock at the supplier";
            written.add(line);
            sb.append(line).append('\n');
        }
        Path log = dir.resolve("1234.log");
        Files.writeString(log, sb, StandardCharsets.UTF_8);

        LogTab tab = new LogTab(new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
        List<String> lines = new ArrayList<>();
        tab.readNewLogLines("1234", log, lines);

        assertThat(lines).isNotEmpty();
        assertThat(written).containsSequence(lines);
        assertThat(lines.get(lines.size() - 1)).isEqualTo(written.get(written.size() - 1));

        // the older lines end with the line just before the first one read: none is lost or cut
        List<String> older = new ArrayList<>();
        tab.readOlderLogLines(log, true, older);
        List<String> all = new ArrayList<>(older);
        all.addAll(lines);
        assertThat(all).isEqualTo(written);
    }

    @Test
    void aShortLogIsReadWhole() throws Exception {
        Path log = dir.resolve("1234.log");
        Files.writeString(log, "first\nsecond\n", StandardCharsets.UTF_8);

        LogTab tab = new LogTab(new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
        List<String> lines = new ArrayList<>();
        tab.readNewLogLines("1234", log, lines);

        assertThat(lines).containsExactly("first", "second");
    }
}
