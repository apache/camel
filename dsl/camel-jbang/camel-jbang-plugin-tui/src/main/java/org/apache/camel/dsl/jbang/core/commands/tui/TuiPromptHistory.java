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

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bounded, persisted recall list for the AI prompt input line. Entries are stored oldest-to-newest; Up/Down walks from
 * the newest backward, restoring a draft when returning past the newest entry.
 */
final class TuiPromptHistory {

    private static final Logger LOG = System.getLogger(TuiPromptHistory.class.getName());

    private final int maxSize;
    private final Path file;
    private final List<String> entries = new ArrayList<>();
    private int browseIndex = -1;
    private String draft = "";

    private TuiPromptHistory(int maxSize, Path file, List<String> entries) {
        this.maxSize = maxSize;
        this.file = file;
        this.entries.addAll(entries);
    }

    static TuiPromptHistory load(int maxSize, Path file) {
        if (maxSize <= 0) {
            return new TuiPromptHistory(0, file, List.of());
        }
        List<String> loaded = readEntries(file, maxSize);
        return new TuiPromptHistory(maxSize, file, loaded);
    }

    boolean isEnabled() {
        return maxSize > 0;
    }

    List<String> entriesForTesting() {
        return Collections.unmodifiableList(entries);
    }

    void remember(String line) {
        if (!isEnabled() || line == null) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (!entries.isEmpty() && entries.get(entries.size() - 1).equals(trimmed)) {
            resetNavigation();
            return;
        }
        entries.add(trimmed);
        trimToMax();
        resetNavigation();
        persist();
    }

    Optional<String> previous(String currentDraft) {
        if (!isEnabled() || entries.isEmpty()) {
            return Optional.empty();
        }
        if (browseIndex < 0) {
            draft = currentDraft;
            browseIndex = entries.size();
        }
        if (browseIndex == 0) {
            return Optional.empty();
        }
        browseIndex--;
        return Optional.of(entries.get(browseIndex));
    }

    Optional<String> next(String currentDraft) {
        if (!isEnabled() || browseIndex < 0) {
            return Optional.empty();
        }
        if (entries.isEmpty()) {
            browseIndex = -1;
            return Optional.of(draft == null ? "" : draft);
        }
        if (browseIndex >= entries.size() - 1) {
            browseIndex = -1;
            return Optional.of(draft == null ? "" : draft);
        }
        browseIndex++;
        return Optional.of(entries.get(browseIndex));
    }

    void resetNavigation() {
        browseIndex = -1;
        draft = "";
    }

    private void trimToMax() {
        while (entries.size() > maxSize) {
            entries.remove(0);
        }
    }

    private static List<String> readEntries(Path file, int maxSize) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> result = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    result.add(line.trim());
                }
            }
            if (result.size() > maxSize) {
                return new ArrayList<>(result.subList(result.size() - maxSize, result.size()));
            }
            return result;
        } catch (IOException e) {
            LOG.log(Level.DEBUG, "Failed to read AI prompt history from {0}: {1}", file, e.getMessage());
            return List.of();
        }
    }

    private void persist() {
        if (!isEnabled()) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, entries, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.DEBUG, "Failed to write AI prompt history to {0}: {1}", file, e.getMessage());
        }
    }
}
