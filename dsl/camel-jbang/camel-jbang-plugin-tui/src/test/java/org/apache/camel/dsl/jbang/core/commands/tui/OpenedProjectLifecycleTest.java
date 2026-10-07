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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An opened Maven project is shown as Stopped; while it runs its app stands in for it (under the app's own name), and
 * when the run ends the project is back, still selected.
 */
class OpenedProjectLifecycleTest {

    private final MonitorContext ctx
            = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));

    @Test
    void theProjectComesBackWhenItsRunEnds() {
        IntegrationInfo project = new IntegrationInfo();
        project.name = "metrics";
        project.sourceDir = "/work/metrics";
        ctx.addPhantom(project);
        ctx.selectedPid = project.pid;

        // not running: the project is listed
        List<IntegrationInfo> infos = merge();
        assertTrue(infos.contains(project));

        // it runs, as MyCamel: the app stands in for the project, and is selected
        IntegrationInfo app = running("4242", "MyCamel", "/work/metrics");
        infos = merge(app);
        assertFalse(infos.contains(project));
        assertEquals("4242", ctx.selectedPid);
        assertEquals("metrics", app.openedAs);

        // the run ends: first the app vanishes, the project is not shown twice
        app.vanishing = true;
        ctx.selectedPid = null;
        assertFalse(merge(app).contains(project));

        // then it is gone: the project is back, and selected again
        infos = merge();
        assertTrue(infos.contains(project));
        assertEquals(project.pid, ctx.selectedPid);
    }

    @Test
    void aFolderRunWithF10IsFoundByItsProcess() {
        // CAMEL-25417: a folder of route files runs from the working directory of the monitor (or from a copy in
        // .camel-jbang-run), so its app does not report the project folder: it is found by the launched process
        IntegrationInfo project = new IntegrationInfo();
        project.name = "custom-kamelet";
        project.sourceDir = "/work/custom-kamelet";
        project.startingSince = System.currentTimeMillis();
        project.launchedProcess = ProcessHandle.current();
        ctx.addPhantom(project);
        ctx.selectedPid = project.pid;

        IntegrationInfo app = running(Long.toString(ProcessHandle.current().pid()), "custom-kamelet",
                "/home/me/.camel-jbang-run/1791375674390");
        List<IntegrationInfo> infos = merge(app);
        assertFalse(infos.contains(project));
        assertEquals(app.pid, ctx.selectedPid);
        assertEquals("custom-kamelet", app.openedAs);
        assertEquals(0, project.startingSince);

        // another app with the same name, not launched for the project, does not stand in for it
        IntegrationInfo other = running("4242", "custom-kamelet", "/elsewhere");
        project.launchedProcess = null;
        assertTrue(merge(other).contains(project));
    }

    @Test
    void aProjectWhoseRunEndedWithoutAnAppIsStoppedAtOnce() throws Exception {
        IntegrationInfo project = new IntegrationInfo();
        project.name = "orders";
        project.sourceDir = "/work/orders";
        project.startingSince = System.currentTimeMillis();
        // camel run pom.xml ended (its Maven build failed) before the app showed up
        Process run = new ProcessBuilder("true").start();
        run.waitFor();
        project.launchedProcess = run.toHandle();
        ctx.addPhantom(project);

        merge();

        assertEquals(0, project.startingSince);
    }

    @Test
    void aProjectIsStartingUntilItsAppShowsUp() {
        IntegrationInfo project = new IntegrationInfo();
        project.name = "metrics";
        project.sourceDir = "/work/metrics";
        ctx.addPhantom(project);
        long now = System.currentTimeMillis();
        assertEquals("✖ Stopped", OverviewTab.projectStatus(project, now).content());

        project.startingSince = now;
        assertTrue(merge().contains(project), "still listed while it starts");
        assertEquals("⚙ Starting", OverviewTab.projectStatus(project, now + 1000).content());
        assertEquals("✖ Stopped", OverviewTab.projectStatus(project, now + OverviewTab.PROJECT_START_MS).content(),
                "an app that never shows up does not start forever");

        merge(running("4242", "MyCamel", "/work/metrics"));
        assertEquals(0, project.startingSince);
    }

    @Test
    void aFailureLogOpensAtWhyItFailed() {
        List<String> log = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            log.add("INFO starting " + i);
        }
        log.add("***************************");
        log.add("APPLICATION FAILED TO START");
        log.add("***************************");
        log.add("Web server failed to start. Port 8080 was already in use.");
        assertEquals(49, DocViewerPopup.failureLine(log));
        assertEquals(0, DocViewerPopup.failureLine(List.of("Exception in thread main")));
    }

    @Test
    void aStoppedRunSelectsTheProjectAgain() {
        // stopped from the TUI: the app is gone at once (no fading), while it is still the selection
        IntegrationInfo project = new IntegrationInfo();
        project.name = "metrics";
        project.sourceDir = "/work/metrics";
        ctx.addPhantom(project);
        ctx.selectedPid = project.pid;
        merge(running("4242", "MyCamel", "/work/metrics"));
        assertEquals("4242", ctx.selectedPid);

        assertTrue(merge().contains(project));
        assertEquals(project.pid, ctx.selectedPid);
    }

    @Test
    void anotherSelectionIsKept() {
        IntegrationInfo project = new IntegrationInfo();
        project.name = "metrics";
        project.sourceDir = "/work/metrics";
        ctx.addPhantom(project);
        merge(running("4242", "MyCamel", "/work/metrics"));

        ctx.selectedPid = "99";
        merge();
        assertEquals("99", ctx.selectedPid, "the user picked another integration meanwhile");
    }

    @Test
    void openingAProjectPutsAwayTheFailureOfAnEarlierRun() {
        AtomicInteger closed = new AtomicInteger();
        ctx.onProjectOpened = closed::incrementAndGet;
        ctx.addPhantom(new IntegrationInfo());
        assertEquals(1, closed.get());
    }

    private List<IntegrationInfo> merge(IntegrationInfo... live) {
        List<IntegrationInfo> infos = new ArrayList<>(List.of(live));
        DataRefreshService.mergePhantoms(infos, ctx.phantomIntegrations, ctx);
        return infos;
    }

    private static IntegrationInfo running(String pid, String name, String dir) {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = pid;
        info.name = name;
        info.directory = dir;
        return info;
    }
}
