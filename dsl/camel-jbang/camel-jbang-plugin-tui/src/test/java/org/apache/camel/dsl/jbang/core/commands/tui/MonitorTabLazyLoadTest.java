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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.TuiRunner;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25438: the integration answers one action at a time, so a tab that is not shown must not ask it for data when
 * the selected integration changes, and the tabs that need the classpath share one answer.
 */
// a TUI runner is created: not alongside the other tests that create one
@Isolated
class MonitorTabLazyLoadTest {

    @Test
    void hiddenTabsDoNotLoadWhenTheIntegrationChanges() throws Exception {
        CountingContext ctx = new CountingContext();
        try (TuiRunner runner = TuiBackendHelper.createTuiRunner(new NoopBackend())) {
            ctx.runner = runner;
            ctx.selectedPid = "1234";
            List<MonitorTab> tabs = List.of(new ClasspathTab(ctx), new CveAuditTab(ctx), new MavenDependenciesTab(ctx),
                    new CatalogTab(ctx));

            tabs.forEach(MonitorTab::onIntegrationChanged);
            Thread.sleep(500);
            assertThat(ctx.actions).isEmpty();

            // shown: the tab loads
            tabs.get(0).onTabSelected();
            long deadline = System.currentTimeMillis() + 5000;
            while (ctx.actions.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(ctx.actions).containsExactly("jvm");
        }
    }

    @Test
    void theTabsShareTheJvmAnswer() {
        CountingContext ctx = new CountingContext();

        JsonObject first = ctx.jvmInfo("1234", 5000);
        JsonObject second = ctx.jvmInfo("1234", 5000);

        assertThat(second).isSameAs(first);
        assertThat(ctx.actions).containsExactly("jvm");
        // another integration has its own
        ctx.jvmInfo("5678", 5000);
        assertThat(ctx.actions).containsExactly("jvm", "jvm");
    }

    private static final class CountingContext extends MonitorContext {

        final List<String> actions = new CopyOnWriteArrayList<>();

        CountingContext() {
            super(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        }

        @Override
        JsonObject executeAction(String pid, JsonObject request, long timeoutMs) {
            actions.add(request.getString("action"));
            JsonObject answer = new JsonObject();
            answer.put("classpath", new JsonArray());
            return answer;
        }
    }
}
