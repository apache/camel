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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering tests for {@link StartupTab}. These tests render the tab into a virtual terminal buffer via
 * {@link Frame#forTesting(Buffer)} and inspect the rendered cell content.
 */
class StartupTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;
        StartupTab tab = new StartupTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        StartupTab tab = new StartupTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Startup"), "Should show Startup in the block title");
    }

    @Test
    void renderShowsLoadingOrEmpty() {
        StartupTab tab = new StartupTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Loading") || rendered.contains("No startup") || rendered.contains("Startup"),
                "Should show loading or empty state");
    }

    @Test
    void renderFooterHints() {
        StartupTab tab = new StartupTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("F5") || footer.contains("refresh") || footer.contains("Esc"),
                "Footer should contain hints");
    }

    @Test
    void theStepsAreInTheOrderTheyStartedOrTheSlowestFirst() {
        // as the recorder has them: a step after its parent, in the order they ended
        StartupTab.StartupStep routes = step("Init Routes", 1, 100, 80);
        StartupTab.StartupStep route = step("stock-check Create Route", 2, 105, 73);
        StartupTab.StartupStep context = step("Init CamelContext", 0, 90, 256);
        List<StartupTab.StartupStep> steps = List.of(route, routes, context);

        assertEquals(List.of(context, routes, route), StartupTab.ordered(steps, false));
        assertEquals(List.of(context, routes, route), StartupTab.ordered(steps, true));
        // without begin times (an older Camel): as recorded
        steps.forEach(st -> st.beginTime = 0);
        assertEquals(steps, StartupTab.ordered(steps, false));
    }

    @Test
    void eachStepIsUnderItsOwnParentWhenTwoBeganInTheSameMillisecond() {
        // CAMEL-26864: "seven Create Route" (19) and "hello Init Route" (22) began in the same millisecond, and the
        // children of 19 were shown under 22
        StartupTab.StartupStep routes = step(18, 0, "Init Routes", 1, 100, 20);
        StartupTab.StartupStep create = step(19, 18, "seven Create Route", 2, 105, 5);
        StartupTab.StartupStep endpoint = step(20, 19, "direct:seven Get Endpoint", 3, 105, 1);
        StartupTab.StartupStep log = step(21, 19, "log2 Create Log Processor", 3, 106, 1);
        StartupTab.StartupStep init = step(22, 18, "hello Init Route", 2, 105, 10);
        List<StartupTab.StartupStep> steps = List.of(endpoint, log, create, init, routes);

        assertEquals(List.of(routes, create, endpoint, log, init), StartupTab.ordered(steps, false));
    }

    @Test
    void theTotalIsFromTheFirstStepToTheEndOfTheLast() {
        StartupTab.StartupStep init = step(1, 0, "Init CamelContext", 0, 1000, 222);
        StartupTab.StartupStep start = step(2, 0, "Start CamelContext", 0, 1282, 182);
        assertEquals(464, StartupTab.totalDuration(List.of(init, start)));
        // without begin times (an older Camel): the longest step
        init.beginTime = 0;
        start.beginTime = 0;
        assertEquals(222, StartupTab.totalDuration(List.of(init, start)));
    }

    @Test
    void aStepWithNoNameIsItsDescription() {
        StartupTab.StartupStep st = step("", 0, 0, 1);
        st.description = "Init CamelContext";
        assertEquals("Init CamelContext", StartupTab.label(st));
        st.name = null;
        assertEquals("Init CamelContext", StartupTab.label(st));
        st.name = "hello";
        st.description = "Init Route";
        assertEquals("hello Init Route", StartupTab.label(st));
    }

    private static StartupTab.StartupStep step(
            int id, int parentId, String name, int level, long begin, long duration) {
        StartupTab.StartupStep st = step(name, level, begin, duration);
        st.id = id;
        st.parentId = parentId;
        return st;
    }

    private static StartupTab.StartupStep step(String name, int level, long begin, long duration) {
        StartupTab.StartupStep st = new StartupTab.StartupStep();
        st.name = name;
        st.level = level;
        st.beginTime = begin;
        st.duration = duration;
        return st;
    }
}
