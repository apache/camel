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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TuiPromptHistoryTest {

    @Test
    void disabledHistoryDoesNotStoreEntries(@TempDir Path tempDir) {
        Path file = tempDir.resolve("prompt.history");
        TuiPromptHistory history = TuiPromptHistory.load(0, file);

        history.remember("hello");

        assertThat(history.entriesForTesting()).isEmpty();
        assertThat(Files.exists(file)).isFalse();
        assertThat(history.previous("draft")).isEmpty();
    }

    @Test
    void rememberTrimPersistAndTrimToMax(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("prompt.history");
        TuiPromptHistory history = TuiPromptHistory.load(2, file);

        history.remember("  first  ");
        history.remember("second");
        history.remember("third");

        assertThat(history.entriesForTesting()).containsExactly("second", "third");
        assertThat(Files.readString(file)).contains("second").contains("third").doesNotContain("first");
    }

    @Test
    void upDownRecallAndRestoreDraft(@TempDir Path tempDir) {
        Path file = tempDir.resolve("prompt.history");
        TuiPromptHistory history = TuiPromptHistory.load(10, file);
        history.remember("alpha");
        history.remember("beta");

        assertThat(history.previous("draft")).contains("beta");
        assertThat(history.previous("ignored")).contains("alpha");
        assertThat(history.previous("ignored")).isEmpty();

        assertThat(history.next("ignored")).contains("beta");
        assertThat(history.next("ignored")).contains("draft");
        assertThat(history.next("ignored")).isEmpty();
    }

    @Test
    void consecutiveDuplicateIsNotStoredTwice(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("prompt.history");
        TuiPromptHistory history = TuiPromptHistory.load(10, file);

        history.remember("same");
        history.remember("same");

        assertThat(history.entriesForTesting()).containsExactly("same");
        assertThat(Files.readString(file).lines().count()).isEqualTo(1);
    }

    @Test
    void reloadsExistingFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("prompt.history");
        Files.writeString(file, "one\ntwo\n");

        TuiPromptHistory history = TuiPromptHistory.load(10, file);

        assertThat(history.previous("")).contains("two");
        assertThat(history.previous("")).contains("one");
    }
}
