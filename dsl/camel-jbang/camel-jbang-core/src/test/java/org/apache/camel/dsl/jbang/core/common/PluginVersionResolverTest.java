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
package org.apache.camel.dsl.jbang.core.common;

import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Source;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.NoMatch;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Older;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Reason;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Result;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Selected;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Unavailable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginVersionResolverTest {

    private static final String GROUP = "org.example.plugins";
    private static final String ARTIFACT = "camel-jbang-plugin-fixture";

    @Test
    void selectsNewestPatchOfTheLine() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.1", "4.22.2", null)
                .release("2.0.0", "4.22.0", null)
                .release("1.0.0", "4.18.5", null);

        Result result = resolve(downloader, "4.22.0");

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> {
            assertThat(s.version()).isEqualTo("2.0.1");
            assertThat(s.compat().source()).isEqualTo(Source.INFERRED);
            assertThat(s.compat().range()).isEqualTo("[4.22.0,4.23.0)");
            assertThat(s.compat().builtFor()).isEqualTo("4.22.2");
        });
        assertThat(downloader.descriptorsRead).containsExactly("2.0.1");
    }

    @Test
    void listsTheReleasesFresh() {
        FakePluginDownloader downloader = new FakePluginDownloader().release("1.0.0", "4.22.2", null);

        resolve(downloader, "4.22.0");

        // a new release must be found when plugin add runs again; only the listing is fresh
        assertThat(downloader.listedFresh).isTrue();
    }

    @Test
    void readsOnlyTheNewestReleaseOfEachLine() {
        // the releases of Forage on Maven Central, and one more line for 4.23
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("1.7.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("1.6.2", "4.22.2", null)
                .release("1.6.1", "4.22.1", null)
                .release("1.6.0", "4.22.0", null)
                .release("1.5.0", "4.20.0", null)
                .release("1.4.2", "4.18.5", null)
                .release("1.4.1", "4.18.4", null)
                .release("1.4.0", "4.18.3", null)
                .release("1.3", "4.16.0", null)
                .release("1.2", "4.14.0", null)
                .release("1.1", "4.12.0", null)
                .release("1.0", "4.10.0", null);

        Result result = resolve(downloader, "4.18.2");

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("1.4.2"));
        assertThat(downloader.descriptorsRead).containsExactly("1.7.0", "1.6.2", "1.5.0", "1.4.2");
    }

    @Test
    void findsADeepLineAmongManyReleases() {
        // 30 releases: 3 patches of 10 lines, and the CLI's line is the oldest one
        FakePluginDownloader downloader = new FakePluginDownloader();
        for (int line = 10; line >= 1; line--) {
            for (int patch = 2; patch >= 0; patch--) {
                downloader.release("1." + line + "." + patch, "4." + (12 + line) + "." + patch, null);
            }
        }

        Result result = resolve(downloader, "4.13.1");

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("1.1.2"));
        assertThat(downloader.descriptorsRead).hasSize(10).startsWith("1.10.2").endsWith("1.1.2");
    }

    @Test
    void fallsBackToTheNextReleaseOfTheLineWhenAPomCannotBeRead() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.0", "4.22.0", null)
                .release("1.0.0", "4.18.5", null);
        // listed, but its POM cannot be read
        downloader.versions.add(0, "2.0.1");

        Result result = resolve(downloader, "4.22.3");

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("2.0.0"));
        assertThat(downloader.descriptorsRead).containsExactly("2.0.1", "2.0.0");
    }

    @Test
    void readsAtMostTenLines() {
        FakePluginDownloader downloader = new FakePluginDownloader();
        for (int line = 12; line >= 1; line--) {
            downloader.release("1." + line + ".0", "4." + (12 + line) + ".0", null);
        }

        // only the oldest line (1.1, for Camel 4.13) supports the CLI, and it is not read
        Result result = resolve(downloader, "4.13.0");

        assertThat(downloader.descriptorsRead).hasSize(PluginVersionResolver.MAX_RELEASE_LINES);
        assertThat(downloader.descriptorsRead.get(0)).isEqualTo("1.12.0");
        assertThat(result).isInstanceOfSatisfying(NoMatch.class,
                n -> assertThat(n.reason()).isEqualTo(Reason.CLI_OLDER));
    }

    @Test
    void selectsDeclaredRange() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("2.0.2", "4.22.2", null);

        Result result = resolve(downloader, "4.23.0-SNAPSHOT");

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> {
            assertThat(s.version()).isEqualTo("3.0.0");
            assertThat(s.compat().source()).isEqualTo(Source.DECLARED);
            assertThat(s.compat().range()).isEqualTo("[4.23.0,4.24.0)");
        });
    }

    @Test
    void selectsTheNearestOlderLineWhenBetweenLines() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("2.1.0", "4.20.1", null)
                .release("2.0.0", "4.18.5", null);

        // no release for 4.21 or 4.22
        Result result = resolve(downloader, "4.21.0");

        assertThat(result).isInstanceOfSatisfying(Older.class, o -> {
            assertThat(o.version()).isEqualTo("2.1.0");
            assertThat(o.compat().range()).isEqualTo("[4.20.0,4.21.0)");
            assertThat(o.newestPerLine())
                    .containsExactly(Map.entry("4.23", "3.0.0"), Map.entry("4.20", "2.1.0"), Map.entry("4.18", "2.0.0"));
        });
    }

    @Test
    void selectsTheNewestReleaseOfTheNearestOlderCamelVersion() {
        // two release lines of the plugin for the same Camel version
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", "4.23.0", null)
                .release("2.1.0", "4.18.5", null)
                .release("2.0.0", "4.18.2", null);

        Result result = resolve(downloader, "4.20.0");

        assertThat(result).isInstanceOfSatisfying(Older.class, o -> assertThat(o.version()).isEqualTo("2.1.0"));
    }

    @Test
    void noMatchWhenCliIsNewerThanAllReleases() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("2.0.2", "4.22.2", null)
                .release("2.0.1", "4.22.1", null);

        Result result = resolve(downloader, "4.24.0");

        assertThat(result).isInstanceOfSatisfying(NoMatch.class, n -> {
            assertThat(n.reason()).isEqualTo(Reason.CLI_NEWER);
            assertThat(n.newestVersion()).isEqualTo("3.0.0");
            assertThat(n.newest().source()).isEqualTo(Source.DECLARED);
            assertThat(n.newestPerLine()).containsExactly(Map.entry("4.23", "3.0.0"), Map.entry("4.22", "2.0.2"));
        });
    }

    @Test
    void noMatchWhenCliIsOlderThanAllReleases() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.2", "4.22.2", null)
                .release("1.0.0", "4.18.5", null);

        Result result = resolve(downloader, "4.14.0");

        assertThat(result).isInstanceOfSatisfying(NoMatch.class, n -> {
            assertThat(n.reason()).isEqualTo(Reason.CLI_OLDER);
            assertThat(n.newestVersion()).isEqualTo("2.0.2");
            assertThat(n.newest().builtFor()).isEqualTo("4.22.2");
        });
    }

    @Test
    void noMatchWhenUnknown() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.0", null, null)
                .release("1.0.0", null, null);

        Result result = resolve(downloader, "4.23.0");

        assertThat(result).isInstanceOfSatisfying(NoMatch.class, n -> {
            assertThat(n.reason()).isEqualTo(Reason.UNKNOWN);
            assertThat(n.newestVersion()).isEqualTo("2.0.0");
            assertThat(n.newest().source()).isEqualTo(Source.UNKNOWN);
            assertThat(n.newestPerLine()).isEmpty();
        });
    }

    @Test
    void releasesWithUnknownCompatibilityAreSkipped() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", null, null)
                .release("2.0.0", "4.23.0", null)
                .release("1.0.0", "4.18.0", null);

        Result result = resolve(downloader, "4.20.0");

        assertThat(result).isInstanceOfSatisfying(Older.class, o -> assertThat(o.version()).isEqualTo("1.0.0"));
    }

    @Test
    void skipsSnapshotsAndQualifiers() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0-SNAPSHOT", "4.22.2", null)
                .release("2.1.0-RC1", "4.22.2", null)
                .release("2.0.0.Final", "4.22.2", null)
                .release("1.0.0", "4.22.2", null);

        Result result = resolve(downloader, "4.22.2");

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("1.0.0"));
        assertThat(downloader.descriptorsRead).containsExactly("1.0.0");
    }

    @Test
    void sortsByMavenVersionOrder() {
        assertThat(PluginVersionResolver.newestFirst(List.of("1.9.0", "1.10.0", "1.2", "1.10.0", "2.0.0-SNAPSHOT")))
                .containsExactly("1.10.0", "1.9.0", "1.2");
    }

    @Test
    void releaseLine() {
        assertThat(PluginVersionResolver.releaseLine("1.6.2")).isEqualTo("1.6");
        assertThat(PluginVersionResolver.releaseLine("1.0")).isEqualTo("1.0");
        assertThat(PluginVersionResolver.releaseLine("2")).isEqualTo("2");
    }

    @Test
    void unavailableWhenTheReleasesCannotBeListed() {
        FakePluginDownloader downloader = new FakePluginDownloader().release("2.0.0", "4.22.0", null);
        downloader.listingDown = true;

        Result result = resolve(downloader, "4.22.1");

        assertThat(result).isInstanceOfSatisfying(Unavailable.class,
                u -> assertThat(u.reason()).contains("Cannot resolve available versions"));
    }

    @Test
    void unavailableWhenNoRelease() {
        Result result = resolve(new FakePluginDownloader(), "4.22.1");

        assertThat(result).isInstanceOf(Unavailable.class);
    }

    @Test
    void unavailableWhenNoPomCanBeRead() {
        FakePluginDownloader downloader = new FakePluginDownloader().release("2.0.0", "4.22.0", null);
        downloader.descriptorsDown = true;

        Result result = resolve(downloader, "4.22.1");

        assertThat(result).isInstanceOf(Unavailable.class);
    }

    private static Result resolve(FakePluginDownloader downloader, String cliVersion) {
        return new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, cliVersion);
    }
}
