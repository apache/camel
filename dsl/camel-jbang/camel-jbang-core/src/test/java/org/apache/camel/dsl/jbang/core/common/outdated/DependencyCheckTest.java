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
package org.apache.camel.dsl.jbang.core.common.outdated;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ReleaseModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CAMEL-27351: the version picking, the Camel release summary and the status of the dependencies.
 */
class DependencyCheckTest {

    @Test
    void stableVersions() {
        assertThat(Versions.isStable("2.13.4")).isTrue();
        assertThat(Versions.isStable("4.2.20.Final")).isTrue();
        assertThat(Versions.isStable("33.4.0-jre")).isTrue();
        assertThat(Versions.isStable("1.0-source")).isTrue();
        assertThat(Versions.isStable("4.24.0-SNAPSHOT")).isFalse();
        assertThat(Versions.isStable("3.9.0-RC1")).isFalse();
        assertThat(Versions.isStable("4.0.0.CR2")).isFalse();
        assertThat(Versions.isStable("5.10.0-M1")).isFalse();
        assertThat(Versions.isStable("2.0.0-beta3")).isFalse();
        assertThat(Versions.isStable("1.0.0-alpha")).isFalse();
    }

    @Test
    void newerPatchAndLatest() {
        List<String> available = List.of("2.12.7.1", "2.13.2", "2.13.4", "2.13.5", "2.14.0", "3.0.0", "3.1.0-rc1");
        assertThat(Versions.newerPatch("2.13.2", available)).isEqualTo("2.13.5");
        assertThat(Versions.latest("2.13.2", available)).isEqualTo("3.0.0");
        assertThat(Versions.majorStep("2.13.2", "3.0.0")).isTrue();
        assertThat(Versions.majorStep("2.13.2", "2.14.0")).isFalse();
        assertThat(Versions.newerPatch("3.0.0", available)).isNull();
        assertThat(Versions.latest("3.0.0", available)).isNull();
        assertThat(Versions.lineOf("2.13.4.1")).isEqualTo("2.13");
        assertThat(Versions.lineOf("1.33")).isEqualTo("1.33");
    }

    @Test
    void newerVersionsKeepTheQualifier() {
        // byte-buddy also publishes -jdk5 builds, netty .Final ones, guava -jre and -android ones
        assertThat(Versions.latest("1.18.16", List.of("1.18.16", "1.18.17", "1.18.17-jdk5"))).isEqualTo("1.18.17");
        assertThat(Versions.newerPatch("4.2.20.Final", List.of("4.2.21.Final", "4.2.22.Alpha1")))
                .isEqualTo("4.2.21.Final");
        assertThat(Versions.latest("33.4.0-jre", List.of("33.5.0-android", "33.5.0-jre"))).isEqualTo("33.5.0-jre");
        // a release candidate gets the release
        assertThat(Versions.newerPatch("3.9.0-RC1", List.of("3.9.0", "3.9.1"))).isEqualTo("3.9.1");
    }

    private static ReleaseModel release(String version, String date, String kind, String eol) {
        ReleaseModel r = new ReleaseModel();
        r.setVersion(version);
        r.setDate(date);
        r.setKind(kind);
        r.setEol(eol);
        r.setJdk("17,21");
        return r;
    }

    private static List<ReleaseModel> releases() {
        return List.of(
                release("4.18.0", "2026-02-10", "lts", "2027-02-10"),
                release("4.18.1", "2026-04-01", "lts", "2027-02-10"),
                release("4.18.3", "2026-08-01", "lts", "2027-02-10"),
                release("4.21.0", "2026-07-01", null, "2026-10-01"),
                release("4.22.0", "2026-08-15", "lts", "2027-08-15"),
                release("4.22.2", "2026-10-15", "lts", "2027-08-15"),
                release("4.23.0", "2026-10-10", null, "2027-01-10"));
    }

    @Test
    void camelReleaseOfAnLtsLine() {
        CamelRelease r = CamelRelease.of("4.18.1", releases(), LocalDate.parse("2026-11-21"));
        assertThat(r.released()).isTrue();
        assertThat(r.lts()).isTrue();
        assertThat(r.eol()).isEqualTo("2027-02-10");
        assertThat(r.eolPassed()).isFalse();
        assertThat(r.newerPatch()).isEqualTo("4.18.3");
        assertThat(r.latestLts()).isEqualTo("4.22.2");
        assertThat(r.latest()).isEqualTo("4.23.0");
        assertThat(r.summary()).isEqualTo(
                "Camel 4.18.1 LTS, end of life 2027-02-10 · newer on this line: 4.18.3 · latest LTS: 4.22.2"
                                          + " · latest: 4.23.0");
    }

    @Test
    void camelReleasePastItsEndOfLife() {
        CamelRelease r = CamelRelease.of("4.21.0", releases(), LocalDate.parse("2026-11-21"));
        assertThat(r.lts()).isFalse();
        assertThat(r.eolPassed()).isTrue();
        assertThat(r.summary()).startsWith("Camel 4.21.0, end of life since 2026-10-01");
    }

    @Test
    void camelReleaseOfTheLatestAndOfASnapshot() {
        assertThat(CamelRelease.of("4.23.0", releases(), LocalDate.parse("2026-11-21")).summary())
                .isEqualTo("Camel 4.23.0, end of life 2027-01-10 · the latest release");
        CamelRelease snapshot = CamelRelease.of("4.24.0-SNAPSHOT", releases(), LocalDate.parse("2026-11-21"));
        assertThat(snapshot.released()).isFalse();
        assertThat(snapshot.latest()).isNull();
        assertThat(snapshot.summary()).isEqualTo("Camel 4.24.0-SNAPSHOT (not released)");
        assertThat(CamelRelease.of(null, releases(), LocalDate.now())).isNull();
    }

    @Test
    void statusOfEachDependency() {
        List<DependencyRow> rows = new ArrayList<>(
                List.of(
                        new DependencyRow("org.apache.camel", "camel-kafka", "4.18.1", false, null),
                        new DependencyRow("org.apache.camel", "camel-zipfile", "4.18.0", false, null),
                        new DependencyRow("org.apache.camel.kamelets", "camel-kamelets", "4.18.0", false, null),
                        new DependencyRow("org.apache.kafka", "kafka-clients", "3.9.1", true, "camel-kafka"),
                        new DependencyRow("org.yaml", "snakeyaml", "1.33", false, null),
                        new DependencyRow("org.apache.commons", "commons-lang3", "3.11", true, "commons-text"),
                        new DependencyRow("org.apache.commons", "commons-text", "1.9", false, null)));
        Map<String, String> brings = Map.of(
                "org.apache.kafka:kafka-clients", "3.9.1",
                "org.yaml:snakeyaml", "2.5",
                "org.apache.commons:commons-lang3", "3.18.0");

        DependencyCheck.classify(rows, "4.18.1", brings, null);
        assertThat(rows).extracting(r -> r.status().label())
                .containsExactly("camel", "mixed", "camel", "managed", "override", "", "");
        assertThat(rows.get(2).explain()).isEqualTo("an Apache Camel JAR with its own versions (4.18.0)");
        assertThat(rows.get(4).camelVersion()).isEqualTo("2.5");
        assertThat(rows.get(4).explain()).isEqualTo("declared as 1.33, the Camel dependencies bring 2.5");
        // a transitive one in another version than Camel's is not the project's choice
        assertThat(rows.get(5).camelVersion()).isEqualTo("3.18.0");

        // loaded twice, or in another version than declared
        DependencyCheck.classify(rows, "4.18.1", brings, Map.of(
                "org.yaml:snakeyaml", Set.of("1.33", "2.5"),
                "org.apache.commons:commons-text", Set.of("1.10.0")));
        assertThat(rows.get(4).status()).isEqualTo(DependencyRow.Status.CONFLICT);
        assertThat(rows.get(4).explain()).isEqualTo("loaded in 1.33 and 2.5");
        assertThat(rows.get(6).status()).isEqualTo(DependencyRow.Status.CONFLICT);
        assertThat(rows.get(6).explain()).isEqualTo("loaded in 1.10.0, declared 1.9");
    }

    @Test
    void newerVersionsAndWhatIsOutdated() {
        DependencyRow text = new DependencyRow("org.apache.commons", "commons-text", "1.9", false, null);
        DependencyRow netty = new DependencyRow("io.netty", "netty-common", "4.2.20.Final", true, "camel-netty");
        DependencyRow camel = new DependencyRow("org.apache.camel", "camel-core", "4.18.1", false, null);
        List<DependencyRow> rows = List.of(text, netty, camel);
        DependencyCheck.classify(rows, "4.18.1", Map.of(), null);
        DependencyCheck.applyAvailable(rows, Map.of(
                "org.apache.commons:commons-text", List.of("1.9", "1.10.0", "1.15.0"),
                "io.netty:netty-common", List.of("4.2.21.Final", "5.0.0.Alpha2"),
                "org.apache.camel:camel-core", List.of("4.18.3")));

        assertThat(text.newer()).isNull();
        assertThat(text.latest()).isEqualTo("1.15.0");
        assertThat(text.latestIsMajor()).isFalse();
        assertThat(text.outdated(true)).isTrue();
        assertThat(text.outdated(false)).isFalse();
        assertThat(netty.newer()).isEqualTo("4.2.21.Final");
        assertThat(netty.outdated(false)).isTrue();
        // the Camel JARs move together, with the Camel version
        assertThat(camel.newer()).isNull();
        assertThat(camel.outdated(true)).isFalse();
        assertThat(text.toJson().getString("latest")).isEqualTo("1.15.0");
    }

    @Test
    void loadedVersionsOfAClasspath() {
        Map<String, Set<String>> loaded = DependencyCheck.loadedVersions(List.of(
                "/home/me/.m2/repository/org/yaml/snakeyaml/1.33/snakeyaml-1.33.jar",
                "/home/me/.m2/repository/org/yaml/snakeyaml/2.5/snakeyaml-2.5.jar",
                "C:\\Users\\me\\.m2\\repository\\org\\apache\\commons\\commons-text\\1.9\\commons-text-1.9.jar",
                "/opt/app/lib/app.jar",
                "/home/me/.m2/repository/org/yaml/snakeyaml/2.5/other-2.5.jar"));
        assertThat(loaded).containsOnlyKeys("org.yaml:snakeyaml", "org.apache.commons:commons-text");
        assertThat(loaded.get("org.yaml:snakeyaml")).containsExactly("1.33", "2.5");
    }

    @Test
    void theLookupsStopAtTheDeadline() throws Exception {
        CountDownLatch slowEnded = new CountDownLatch(1);
        MavenDownloader downloader = mock(MavenDownloader.class);
        when(downloader.resolveAvailableVersions(eq("org.yaml"), eq("snakeyaml"), any(), anyBoolean()))
                .thenReturn(List.of(MavenGav.fromCoordinates("org.yaml", "snakeyaml", "2.5", null, null)));
        when(downloader.resolveAvailableVersions(eq("org.slow"), eq("slow"), any(), anyBoolean())).thenAnswer(i -> {
            try {
                // until it is interrupted
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                // a lookup takes a while to stop, as a download does
                new CountDownLatch(1).await(300, TimeUnit.MILLISECONDS);
            } finally {
                slowEnded.countDown();
            }
            return List.of();
        });
        AtomicReference<String> error = new AtomicReference<>();

        Map<String, List<String>> found = DependencyCheck.availableVersions(
                downloader, List.of("org.yaml:snakeyaml", "org.slow:slow"), error, Duration.ofMillis(200));

        assertThat(found).containsOnlyKeys("org.yaml:snakeyaml");
        assertThat(found.get("org.yaml:snakeyaml")).containsExactly("2.5");
        assertThat(error.get()).isEqualTo("timeout looking up the available versions");
        // the lookup past the deadline has ended when the method returns
        assertThat(slowEnded.getCount()).isZero();
    }

    @Test
    void theLoadedVersionsCannotBeChangedFromOutside() {
        List<DependencyRow> rows = List.of(new DependencyRow("org.yaml", "snakeyaml", "1.33", false, null));
        DependencyCheck.classify(rows, "4.18.1", Map.of(), Map.of("org.yaml:snakeyaml", Set.of("2.5", "1.33")));

        assertThat(rows.get(0).loadedVersions()).containsExactly("1.33", "2.5");
        assertThatThrownBy(() -> rows.get(0).loadedVersions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
