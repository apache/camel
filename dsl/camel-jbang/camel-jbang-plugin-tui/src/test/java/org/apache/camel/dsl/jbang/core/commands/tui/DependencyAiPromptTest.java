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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.outdated.DependencyCheck;
import org.apache.camel.dsl.jbang.core.common.outdated.DependencyRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CAMEL-27351: the question Shift+F8 asks on the Maven Dependencies tab.
 */
class DependencyAiPromptTest {

    @Test
    void nothingWorthALook() {
        List<DependencyRow> rows = List.of(new DependencyRow("org.apache.camel", "camel-timer", "4.18.1", false, null));
        DependencyCheck.classify(rows, "4.18.1", Map.of(), null);
        String prompt = DependencyAiPrompt.of(null, null, "4.18.1", rows, null, null, false);
        assertTrue(prompt.startsWith("Review the dependencies of the running integration.\nCamel 4.18.1.\n"), prompt);
        assertTrue(prompt.contains("None of its 1 dependencies overrides the version Camel brings or is loaded twice.\n"),
                prompt);
        assertTrue(prompt.contains("The newer releases were not looked up"), prompt);
    }

    @Test
    void theListIsCappedAndTheCvesAreIncluded() {
        List<DependencyRow> rows = new ArrayList<>();
        for (int i = 0; i < DependencyAiPrompt.MAX_ROWS + 5; i++) {
            rows.add(new DependencyRow("com.example", "lib" + i, "1.0", true, "camel-foo"));
        }
        Map<String, List<String>> available = new HashMap<>();
        rows.forEach(r -> available.put(r.ga(), List.of("1.0.1")));
        DependencyCheck.classify(rows, "4.18.1", Map.of(), null);
        DependencyCheck.applyAvailable(rows, available);
        Map<String, CveAuditTab.CveCount> cves = Map.of("com.example:lib0:1.0", new CveAuditTab.CveCount(2, "HIGH", "1.0.1"));

        String prompt = DependencyAiPrompt.of(null, null, null, rows, cves, null, true);
        assertTrue(prompt.contains("- com.example:lib0:1.0 (comes in with camel-foo): Camel does not bring this library;"
                                   + " newer on this line: 1.0.1; 2 CVEs (worst HIGH), fixed in 1.0.1\n"),
                prompt);
        assertTrue(prompt.contains("- and 5 more\n"), prompt);
        assertFalse(prompt.contains("not looked up"), prompt);
    }
}
