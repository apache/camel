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
package org.apache.camel.cli.connector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trace file starts again when it is full, instead of growing while tracing is on (CAMEL-26929).
 */
class TraceFileSizeTest {

    @TempDir
    Path dir;

    @Test
    void aFileThatWouldGrowPastTheMaximumStartsAgain() throws Exception {
        File file = Files.writeString(dir.resolve("1-trace.json"), "x".repeat(90)).toFile();

        assertThat(FileCliConnectorTransport.startNewFileIfFull(file, 20, 100)).isTrue();
        assertThat(file).doesNotExist();
    }

    @Test
    void aFileWithRoomIsAppendedTo() throws Exception {
        File file = Files.writeString(dir.resolve("1-trace.json"), "x".repeat(50)).toFile();

        assertThat(FileCliConnectorTransport.startNewFileIfFull(file, 20, 100)).isFalse();
        assertThat(file).hasContent("x".repeat(50));
    }

    @Test
    void anEmptyFileTakesDataLargerThanTheMaximum() throws Exception {
        // a single snapshot larger than the maximum is still written
        File file = dir.resolve("1-trace.json").toFile();

        assertThat(FileCliConnectorTransport.startNewFileIfFull(file, 200, 100)).isFalse();
    }

    @Test
    void theMaximumIsTenMegabytes() {
        assertThat(FileCliConnectorTransport.MAX_TRACE_FILE_SIZE).isEqualTo(10L * 1024 * 1024);
    }
}
