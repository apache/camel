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
package org.apache.camel.maven.packaging;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pure-logic helpers in {@link PrepareDocSymlinksMojo}. Full-pipeline equivalence to the previous
 * gulp-based build is verified separately by a byte-for-byte diff of the symlink/nav tree produced by the mojo — these
 * tests guard against regressions in the helpers that diff alone could miss.
 *
 * <p>
 * Reproducing the equivalence diff (run once on {@code main}, once on this branch, then {@code diff} the two snapshots
 * — empty diff means no regression):
 *
 * <pre>{@code
 * find docs/components docs/main core/camel-core-engine/src/main/docs/modules/eips \
 *     \( -type l -o -name nav.adoc \) -print0 \
 *   | LC_ALL=C sort -z \
 *   | while IFS= read -r -d '' f; do
 *       printf '=== %s ===\n' "$f"
 *       if [ -L "$f" ]; then readlink "$f"; else cat "$f"; fi
 *     done > /tmp/snapshot.txt
 * }</pre>
 *
 * Use {@code -print0}/{@code sort -z}/{@code read -d ''} (NUL-delimited) so the pipeline is robust to large file counts
 * and unusual filenames, and {@code LC_ALL=C} so the ordering is deterministic across macOS/Linux.
 */
class PrepareDocSymlinksMojoTest {

    // Note: glob matching itself is delegated to the JDK's FileSystem.getPathMatcher("glob:...") since the refactor
    // to Ant-style include/exclude patterns. No custom glob tests are needed here; PathMatcher semantics are
    // covered by the JDK's own tests and verified end-to-end by the byte-equivalence diff under verification.

    // ----- replaceBlock ---------------------------------------------------------------------------------------------

    @Test
    void replaceBlock_replacesEntireBlockIncludingMarkerLines() {
        String tpl = "<!-- generated:txt -->\n<!-- endinject -->\n* xref:idx[Idx]\n";
        String out = PrepareDocSymlinksMojo.replaceBlock(tpl, "<!-- generated:txt -->", "<!-- endinject -->",
                "// generated\n\n");
        assertThat(out).isEqualTo("// generated\n\n* xref:idx[Idx]\n");
    }

    @Test
    void replaceBlock_preservesPrefixAndSuffix() {
        String tpl = "head\n<!-- inject:adoc -->\n<!-- endinject -->\ntail\n";
        String out = PrepareDocSymlinksMojo.replaceBlock(tpl, "<!-- inject:adoc -->", "<!-- endinject -->",
                "** xref:a[A]\n** xref:b[B]\n");
        assertThat(out).isEqualTo("head\n** xref:a[A]\n** xref:b[B]\ntail\n");
    }

    @Test
    void replaceBlock_returnsTemplateUnchangedWhenOpenMarkerMissing() {
        String tpl = "no markers here\n";
        assertThat(PrepareDocSymlinksMojo.replaceBlock(tpl, "<!-- foo -->", "<!-- endinject -->", "x"))
                .isEqualTo(tpl);
    }

    @Test
    void replaceBlock_returnsTemplateUnchangedWhenEndMarkerMissing() {
        String tpl = "<!-- foo -->\n(no end marker)\n";
        assertThat(PrepareDocSymlinksMojo.replaceBlock(tpl, "<!-- foo -->", "<!-- endinject -->", "x"))
                .isEqualTo(tpl);
    }

    // ----- extractTitle ---------------------------------------------------------------------------------------------

    @Test
    void extractTitle_prefersDoctitleOverHeading() {
        String content = "= H1 Heading\n:doctitle: The Real Title\nbody\n";
        assertThat(PrepareDocSymlinksMojo.extractTitle(Paths.get("x.adoc"), content)).isEqualTo("The Real Title");
    }

    @Test
    void extractTitle_fallsBackToEqualsHeading() {
        assertThat(PrepareDocSymlinksMojo.extractTitle(Paths.get("x.adoc"), "= My Title\nbody\n"))
                .isEqualTo("My Title");
    }

    @Test
    void extractTitle_fallsBackToHashHeading() {
        assertThat(PrepareDocSymlinksMojo.extractTitle(Paths.get("x.adoc"), "# My Title\nbody\n"))
                .isEqualTo("My Title");
    }

    @Test
    void extractTitle_throwsWhenNoTitlePresent() {
        assertThatThrownBy(() -> PrepareDocSymlinksMojo.extractTitle(Paths.get("x.adoc"), "just body\n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.adoc");
    }

    // ----- NavComparator --------------------------------------------------------------------------------------------

    @Test
    void navComparator_sortsByTitleWhenNeitherHasGroup() {
        Path a = Paths.get("a.adoc");
        Path b = Paths.get("b.adoc");
        Map<Path, String> titles = Map.of(a, "Zebra", b, "Apple");
        PrepareDocSymlinksMojo.NavComparator cmp = new PrepareDocSymlinksMojo.NavComparator(titles, Map.of());
        List<Path> entries = new ArrayList<>(List.of(a, b));
        entries.sort(cmp);
        assertThat(entries).containsExactly(b, a);
    }

    @Test
    void navComparator_summaryFilePlacedBeforeItsGroupChildren() {
        // Mirrors the "ai-summary" / "AI" interaction in components/nav.adoc:
        // summary file has no :group: but its title equals the children's group ⇒ summary first.
        Path summary = Paths.get("ai-summary.adoc");
        Path child = Paths.get("openai-component.adoc");
        Map<Path, String> titles = Map.of(summary, "AI", child, "OpenAI");
        Map<Path, String> groups = Map.of(child, "AI");
        PrepareDocSymlinksMojo.NavComparator cmp = new PrepareDocSymlinksMojo.NavComparator(titles, groups);
        List<Path> entries = new ArrayList<>(List.of(child, summary));
        entries.sort(cmp);
        assertThat(entries).containsExactly(summary, child);
    }

    @Test
    void navComparator_groupedEntriesSortByGroupThenTitle() {
        Path a1 = Paths.get("a1.adoc");
        Path a2 = Paths.get("a2.adoc");
        Path b1 = Paths.get("b1.adoc");
        Map<Path, String> titles = Map.of(a1, "Alpha", a2, "Beta", b1, "Gamma");
        Map<Path, String> groups = Map.of(a1, "Group A", a2, "Group A", b1, "Group B");
        PrepareDocSymlinksMojo.NavComparator cmp = new PrepareDocSymlinksMojo.NavComparator(titles, groups);
        List<Path> entries = new ArrayList<>(List.of(b1, a2, a1));
        entries.sort(cmp);
        assertThat(entries).containsExactly(a1, a2, b1);
    }

    @Test
    void navComparator_isReflexivelyConsistentForRandomInput() {
        // Sort-then-sort-reverse-then-sort-again must converge and not throw IllegalArgumentException;
        // protects against accidental transitivity bugs in the 4-branch compare().
        Map<Path, String> titles = new HashMap<>();
        Map<Path, String> groups = new HashMap<>();
        List<Path> entries = new ArrayList<>();
        String[] groupAssignments = { null, "G1", "G2", null, "G1" };
        String[] titleAssignments = { "G1", "Bravo", "Alpha", "Delta", "Charlie" };
        for (int i = 0; i < titleAssignments.length; i++) {
            Path p = Paths.get("file" + i + ".adoc");
            entries.add(p);
            titles.put(p, titleAssignments[i]);
            if (groupAssignments[i] != null) {
                groups.put(p, groupAssignments[i]);
            }
        }
        PrepareDocSymlinksMojo.NavComparator cmp = new PrepareDocSymlinksMojo.NavComparator(titles, groups);
        List<Path> sorted = new ArrayList<>(entries);
        sorted.sort(cmp);
        Collections.reverse(sorted);
        sorted.sort(cmp);
        // After two sorts the order must be stable, proving the comparator is transitive enough for List.sort.
        List<Path> sortedAgain = new ArrayList<>(sorted);
        sortedAgain.sort(cmp);
        assertThat(sortedAgain).containsExactlyElementsOf(sorted);
    }
}
