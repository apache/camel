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
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Result;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Selected;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Unavailable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginVersionResolverTest {

    private static final String GROUP = "org.example.plugins";
    private static final String ARTIFACT = "camel-jbang-plugin-fixture";
    private static final String CUSTOM_REPO = "https://repo.example.org/maven2";

    @Test
    void selectsNewestPatchOfTheLine() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.1", "4.22.2", null)
                .release("2.0.0", "4.22.0", null)
                .release("1.0.0", "4.18.5", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.22.0", null);

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> {
            assertThat(s.version()).isEqualTo("2.0.1");
            assertThat(s.compat().source()).isEqualTo(Source.INFERRED);
            assertThat(s.compat().range()).isEqualTo("[4.22.0,4.23.0)");
            assertThat(s.compat().builtFor()).isEqualTo("4.22.2");
        });
        assertThat(downloader.descriptorsRead).containsExactly("2.0.1");
    }

    @Test
    void fallsBackToAnOlderRelease() {
        // the newest releases are for newer Camel versions
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("2.0.2", "4.22.2", null)
                .release("2.0.1", "4.22.1", null)
                .release("1.0.0", "4.18.5", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.22.2", null);

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("2.0.2"));
        assertThat(downloader.descriptorsRead).containsExactly("3.0.0", "2.0.2");

        result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.18.3", null);
        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("1.0.0"));
    }

    @Test
    void selectsDeclaredRange() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("2.0.2", "4.22.2", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.23.0-SNAPSHOT", null);

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> {
            assertThat(s.version()).isEqualTo("3.0.0");
            assertThat(s.compat().source()).isEqualTo(Source.DECLARED);
            assertThat(s.compat().range()).isEqualTo("[4.23.0,4.24.0)");
        });
    }

    @Test
    void noMatchWhenNewestDeclaresARange() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("2.0.2", "4.22.2", null)
                .release("2.0.1", "4.22.1", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.24.0", null);

        assertThat(result).isInstanceOfSatisfying(NoMatch.class, n -> {
            assertThat(n.newestVersion()).isEqualTo("3.0.0");
            assertThat(n.newest().source()).isEqualTo(Source.DECLARED);
            assertThat(n.newestPerLine()).containsExactly(Map.entry("4.23", "3.0.0"), Map.entry("4.22", "2.0.2"));
        });
    }

    @Test
    void noMatchWhenInferred() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.2", "4.22.2", null)
                .release("1.0.0", "4.18.5", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.23.0", null);

        assertThat(result).isInstanceOfSatisfying(NoMatch.class, n -> {
            assertThat(n.newestVersion()).isEqualTo("2.0.2");
            assertThat(n.newest().source()).isEqualTo(Source.INFERRED);
            assertThat(n.newest().builtFor()).isEqualTo("4.22.2");
        });
    }

    @Test
    void noMatchWhenUnknown() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.0", null, null)
                .release("1.0.0", null, null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.23.0", null);

        assertThat(result).isInstanceOfSatisfying(NoMatch.class, n -> {
            assertThat(n.newestVersion()).isEqualTo("2.0.0");
            assertThat(n.newest().source()).isEqualTo(Source.UNKNOWN);
            assertThat(n.newestPerLine()).isEmpty();
        });
    }

    @Test
    void readsAtMostTenPoms() {
        FakePluginDownloader downloader = new FakePluginDownloader();
        for (int i = 20; i > 0; i--) {
            downloader.release("1." + i + ".0", "4.23.0", null);
        }
        // only the oldest release supports the CLI
        downloader.release("1.0.0", "4.18.0", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.18.0", null);

        assertThat(result).isInstanceOf(NoMatch.class);
        assertThat(downloader.descriptorsRead).hasSize(PluginVersionResolver.MAX_DESCRIPTORS);
        assertThat(downloader.descriptorsRead.get(0)).isEqualTo("1.20.0");
    }

    @Test
    void skipsSnapshotsAndQualifiers() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("3.0.0-SNAPSHOT", "4.22.2", null)
                .release("2.1.0-RC1", "4.22.2", null)
                .release("2.0.0.Final", "4.22.2", null)
                .release("1.0.0", "4.22.2", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.22.2", null);

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("1.0.0"));
        assertThat(downloader.descriptorsRead).containsExactly("1.0.0");
    }

    @Test
    void sortsByMavenVersionOrder() {
        assertThat(PluginVersionResolver.newestFirst(List.of("1.9.0", "1.10.0", "1.2", "1.10.0", "2.0.0-SNAPSHOT")))
                .containsExactly("1.10.0", "1.9.0", "1.2");
    }

    @Test
    void mergesTheReleasesOfAllRepositories() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.0", "4.22.0", null)
                .releaseIn(CUSTOM_REPO, "2.1.0", "4.22.1", null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.22.1", "custom=" + CUSTOM_REPO);

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("2.1.0"));
    }

    @Test
    void skipsARepositoryThatIsDown() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .releaseIn(CUSTOM_REPO, "2.1.0", "4.22.1", null);
        downloader.down.add(null);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.22.1", CUSTOM_REPO);

        assertThat(result).isInstanceOfSatisfying(Selected.class, s -> assertThat(s.version()).isEqualTo("2.1.0"));
    }

    @Test
    void unavailableWhenAllRepositoriesAreDown() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.0", "4.22.0", null);
        downloader.down.add(null);
        downloader.down.add(CUSTOM_REPO);

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.22.1", CUSTOM_REPO);

        assertThat(result).isInstanceOfSatisfying(Unavailable.class,
                u -> assertThat(u.reason()).contains("cannot be reached"));
    }

    @Test
    void unavailableWhenNoRelease() {
        // such as offline, when the metadata cannot be downloaded
        Result result = new PluginVersionResolver(new FakePluginDownloader()).resolve(GROUP, ARTIFACT, "4.22.1", null);

        assertThat(result).isInstanceOf(Unavailable.class);
    }

    @Test
    void unavailableWhenNoPomCanBeRead() {
        FakePluginDownloader downloader = new FakePluginDownloader()
                .release("2.0.0", "4.22.0", null);
        downloader.descriptorsDown = true;

        Result result = new PluginVersionResolver(downloader).resolve(GROUP, ARTIFACT, "4.22.1", null);

        assertThat(result).isInstanceOf(Unavailable.class);
    }
}
