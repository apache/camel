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
import java.util.List;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an opened Maven project shows before it runs: its routes in the Source pane, and what it is in the Overview.
 */
class OpenedProjectViewsTest {

    @TempDir
    Path dir;

    @Test
    void theSourcePaneListsTheRoutesOfTheProject() {
        List<SourceTab.RouteEntry> routes = List.of(
                new SourceTab.RouteEntry(
                        "yaml-route", "timer:from-yaml", dir.resolve(
                                "src/main/resources/camel/my-routes.yaml").toString(),
                        20),
                new SourceTab.RouteEntry(
                        null, "timer:foo", dir.resolve(
                                "src/main/java/org/acme/TimerRoute.java").toString(),
                        27));

        List<String> text = text(SourceTab.emptySourceLines(routes, dir));
        assertThat(text).anyMatch(l -> l.contains("2 routes in this project"));
        assertThat(text).anyMatch(l -> l.contains("yaml-route") && l.contains("src/main/resources/camel/my-routes.yaml:20"));
        // a route without an id by its endpoint
        assertThat(text).anyMatch(l -> l.contains("timer:foo") && l.contains("TimerRoute.java:27"));
        assertThat(text).anyMatch(l -> l.contains(" g ") && l.contains("opens a route"));
    }

    @Test
    void withoutRoutesTheHintStays() {
        assertThat(text(SourceTab.emptySourceLines(List.of(), dir)))
                .anyMatch(l -> l.contains("Select a file and press Enter"));
    }

    @Test
    void aStoppedProjectSaysWhatItIsAndHowToRunIt() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        IntegrationInfo project = new IntegrationInfo();
        project.name = "metrics";
        project.platform = "Spring Boot";
        project.sourceDir = dir.toString();
        project.phantom = true;

        List<String> text = text(OverviewTab.projectLines(project, 32));
        assertThat(text).anyMatch(l -> l.contains("Project: metrics"));
        assertThat(text).anyMatch(l -> l.contains("Spring Boot"));
        assertThat(text).anyMatch(l -> l.contains("Maven (pom.xml)"));
        assertThat(text).anyMatch(l -> l.contains("F10") && l.contains("runs it"));
        assertThat(text).noneMatch(l -> l.contains("Profile"));
    }

    private static List<String> text(List<Line> lines) {
        return lines.stream().map(l -> l.spans().stream().map(Span::content).reduce("", String::concat)).toList();
    }
}
