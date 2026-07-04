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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering tests for {@link ConfigurationTab}. These tests render the tab into a virtual terminal buffer via
 * {@link Frame#forTesting(Buffer)} and inspect the rendered cell content.
 */
class ConfigurationTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderShowsPropertyKeyAndValue() {
        addProperty("camel.main.name", "my-app", "application.properties");

        ConfigurationTab tab = new ConfigurationTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("camel.main.name"), "Should render property key");
        assertTrue(rendered.contains("my-app"), "Should render property value");
    }

    @Test
    void renderPropertyKeyInCyan() {
        addProperty("camel.main.name", "my-app", "application.properties");

        ConfigurationTab tab = new ConfigurationTab(ctx);

        Rect area = new Rect(0, 0, 120, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "c", Color.CYAN),
                "Property key should be rendered in CYAN");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        ConfigurationTab tab = new ConfigurationTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 10);

        assertTrue(rendered.contains("No configuration properties available"),
                "Should show placeholder when no properties exist");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        ConfigurationTab tab = new ConfigurationTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 10);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsPropertyCount() {
        addProperty("camel.main.name", "my-app", "application.properties");
        addProperty("camel.main.shutdownTimeout", "30", "application.properties");

        ConfigurationTab tab = new ConfigurationTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("[2]"), "Title should show property count");
    }

    @Test
    void renderMultiplePropertiesAllAppear() {
        addProperty("camel.main.name", "my-app", "application.properties");
        addProperty("camel.main.shutdownTimeout", "30", "application.properties");

        ConfigurationTab tab = new ConfigurationTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("camel.main.name"), "Should render first property key");
        assertTrue(rendered.contains("my-app"), "Should render first property value");
        assertTrue(rendered.contains("camel.main.shutdownTimeout"), "Should render second property key");
        assertTrue(rendered.contains("30"), "Should render second property value");
    }

    @Test
    void renderSecretValueInDarkGray() {
        addProperty("camel.vault.password", "xxxxxx", "application.properties");

        ConfigurationTab tab = new ConfigurationTab(ctx);

        Rect area = new Rect(0, 0, 120, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "x", Color.DARK_GRAY),
                "Secret value 'xxxxxx' should be rendered in DARK_GRAY");
    }

    // ---- Helper methods ----

    private ConfigurationTab.ConfigProperty addProperty(String key, String value, String source) {
        ConfigurationTab.ConfigProperty prop = new ConfigurationTab.ConfigProperty();
        prop.key = key;
        prop.value = value;
        prop.source = source;
        info.configProperties.add(prop);
        return prop;
    }
}
