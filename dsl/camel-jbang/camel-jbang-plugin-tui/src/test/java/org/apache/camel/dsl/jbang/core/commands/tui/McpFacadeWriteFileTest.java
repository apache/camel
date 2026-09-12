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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpFacadeWriteFileTest {

    /** Answers the confirm dialog without a UI, and remembers what it was asked. */
    private static class ConfirmingBridge implements McpFacade.MonitorBridge {
        final boolean answer;
        McpFacade.FileWrite request;
        int asked;

        ConfirmingBridge(boolean answer) {
            this.answer = answer;
        }

        @Override
        public boolean confirmFileWrite(McpFacade.FileWrite request) {
            this.request = request;
            asked++;
            return answer;
        }

        @Override
        public MonitorTab activeTab() {
            return null;
        }

        @Override
        public void handleTabKey(int tabIndex) {
        }

        @Override
        public void selectMoreTab(int moreIndex) {
        }

        @Override
        public boolean isSwitchPopupVisible() {
            return false;
        }

        @Override
        public boolean isMorePopupVisible() {
            return false;
        }

        @Override
        public void renderOverviewFooter(List<dev.tamboui.text.Span> spans) {
        }

        @Override
        public void insertFKeyHints(List<dev.tamboui.text.Span> spans) {
        }

        @Override
        public void sendRouteCommand(String pid, String routeId, String command) {
        }

        @Override
        public void restartProcess() {
        }

        @Override
        public void stopProcess(boolean forceKill) {
        }

        @Override
        public void stopAll() {
        }

        @Override
        public void resetIntegrationTabState() {
        }
    }

    private static McpFacade facade(Path sourceDir, boolean devMode, ConfirmingBridge bridge) {
        IntegrationInfo info = new IntegrationInfo();
        info.name = "demo";
        info.pid = "1";
        info.devMode = devMode;
        ConfigurationTab.ConfigProperty cp = new ConfigurationTab.ConfigProperty();
        cp.key = "camel.main.routesIncludePattern";
        cp.value = "file:" + sourceDir.resolve("demo.camel.yaml");
        info.configProperties.add(cp);
        return new McpFacade(
                null, new AtomicReference<>(List.of(info)), null, null, null, null, null, null, null, null, null,
                null, bridge);
    }

    @Test
    void writesAfterConfirmationAndDescribesTheDirectory(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        ConfirmingBridge bridge = new ConfirmingBridge(true);
        McpFacade facade = facade(dir, true, bridge);

        JsonObject result = facade.writeFile("demo", "demo.camel.yaml", "- route:\n    id: x\n", true);

        assertEquals("overwritten", result.getString("status"));
        assertEquals(1, bridge.asked);
        assertEquals("demo.camel.yaml", bridge.request.file());
        assertEquals("- route: {}\n", bridge.request.oldContent());
        assertEquals("- route:\n    id: x\n", bridge.request.newContent());
        assertTrue(bridge.request.devMode());
        assertTrue(bridge.request.temporary());
        assertTrue(facade.consumeConfirmWaitMs() >= 0);
        assertEquals("- route:\n    id: x\n", Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8));
        assertEquals(2, result.getInteger("lines"));
        assertTrue(result.getBoolean("devMode"));
        assertTrue(result.getBoolean("temporary"), "a JUnit temp dir is under java.io.tmpdir");
        assertTrue(result.getString("editing").contains("reloaded"), result.getString("editing"));
        assertEquals(dir.toString(), result.getString("directory"));

        JsonObject created = facade.writeFile("demo", "other.camel.yaml", "- route: {}", true);
        assertEquals("created", created.getString("status"));
        assertEquals(null, bridge.request.oldContent(), "a new file has no old content");
        assertTrue(Files.exists(dir.resolve("other.camel.yaml")));
    }

    @Test
    void leavesTheFileAloneWhenTheUserDeclines(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        ConfirmingBridge bridge = new ConfirmingBridge(false);
        McpFacade facade = facade(dir, false, bridge);

        JsonObject result = facade.writeFile("demo", "demo.camel.yaml", "broken", true);

        assertEquals("rejected", result.getString("status"));
        assertTrue(result.getString("message").contains("nothing is pending"), result.getString("message"));
        assertTrue(result.getString("message").contains("do not retry"), result.getString("message"));
        assertEquals("- route: {}\n", Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8));
    }

    @Test
    void writesWithoutAskingWhenConfirmIsOff(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        ConfirmingBridge bridge = new ConfirmingBridge(false);
        McpFacade facade = facade(dir, false, bridge);

        // confirm=false is only honoured once the user allowed it (/write auto); a model retrying a rejected
        // write without confirmation still gets the dialog
        JsonObject asked = facade.writeFile("demo", "demo.camel.yaml", "- route:\n    id: y\n", false);
        assertEquals("rejected", asked.getString("status"));
        assertEquals(1, bridge.asked);
        assertTrue(asked.getString("message").contains("confirm=false does not skip"), asked.getString("message"));

        facade.setWriteMode(McpFacade.WriteMode.AUTO);
        JsonObject result = facade.writeFile("demo", "demo.camel.yaml", "- route:\n    id: y\n", false);

        assertEquals("overwritten", result.getString("status"));
        assertEquals(1, bridge.asked, "no dialog once the user allowed unconfirmed writes");
        assertFalse(result.getBoolean("devMode"));
        // a temporary copy outranks the restart hint, and without dev mode nothing reloads
        assertTrue(result.getString("editing").contains("lost when the integration stops."),
                result.getString("editing"));
        assertFalse(result.getString("editing").contains("reloaded"));
    }

    @Test
    void invalidContentIsNotWrittenUnlessValidationIsOff(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        ConfirmingBridge bridge = new ConfirmingBridge(true);
        McpFacade facade = facade(dir, true, bridge);
        facade.setSourceValidator((file, content) -> {
            if (file.endsWith(".yaml") && content.contains("logLevel")) {
                return List.of("log: unknown option logLevel");
            }
            if (file.endsWith(".properties") && content.contains("camel.main.nme")) {
                return List.of("Line 1: Unknown Camel property: camel.main.nme");
            }
            return List.of();
        });

        JsonObject result = facade.writeFile("demo", "demo.camel.yaml", "- log:\n    logLevel: WARN\n", true);

        assertEquals("invalid", result.getString("status"));
        assertEquals(List.of("log: unknown option logLevel"), result.getCollection("errors"));
        assertEquals(0, bridge.asked, "nothing to confirm when the content is invalid");
        assertEquals("- route: {}\n", Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8));

        // properties files are validated too, other file types are not
        assertEquals("invalid", facade.writeFile("demo", "application.properties", "camel.main.nme=x", true)
                .getString("status"));
        assertEquals("created", facade.writeFile("demo", "notes.txt", "logLevel", true).getString("status"));
        // and validation can be switched off (the bridge confirms)
        assertEquals("overwritten",
                facade.writeFile("demo", "demo.camel.yaml", "- log:\n    logLevel: WARN\n", true, false)
                        .getString("status"));

        // the standalone validation reports the same errors, for content and for a file in the directory
        JsonObject check = facade.validateSource("demo", "new.camel.yaml", "- log:\n    logLevel: WARN\n");
        assertFalse(check.getBoolean("valid"));
        assertEquals(1, check.getCollection("errors").size());
        JsonObject fileCheck = facade.validateSource("demo", "demo.camel.yaml", null);
        assertFalse(fileCheck.getBoolean("valid"), "the file now contains the invalid content");
        assertEquals("demo.camel.yaml", fileCheck.getString("file"));
        assertTrue(facade.validateSource("demo", null, "- route: {}").getBoolean("valid"), "content alone is YAML");
        assertEquals("error", facade.validateSource("demo", "missing.yaml", null).getString("status"));
        assertEquals("error", facade.validateSource("demo", "notes.txt", "x").getString("status"));
    }

    @Test
    void liveModePausedForAQuestionReturnsTheEditorStateWithoutWriting(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        ConfirmingBridge bridge = new ConfirmingBridge(true) {
            @Override
            public McpFacade.ReplayOutcome replayFileWrite(McpFacade.FileWrite request) {
                this.request = request;
                return new McpFacade.ReplayOutcome(
                        false, 1, List.of(), "- route:\n    id: typed\n", "About edit 1 of 3: why an id?", 2);
            }
        };
        McpFacade facade = facade(dir, true, bridge);
        facade.setWriteMode(McpFacade.WriteMode.LIVE);

        JsonObject result = facade.writeFile("demo", "demo.camel.yaml", "- route:\n    id: typed\n    from: x\n", true);

        assertEquals("paused", result.getString("status"));
        assertEquals("About edit 1 of 3: why an id?", result.getString("question"));
        assertEquals(1, result.getInteger("appliedHunks"));
        assertEquals(2, result.getInteger("pendingHunks"));
        assertEquals("- route:\n    id: typed\n", result.getString("content"));
        assertTrue(result.getString("message").contains("why an id?"));
        assertTrue(result.getString("message").contains("call camel_write_file again"));
        assertEquals(0, bridge.asked, "no confirm dialog: the replay is parked in the editor");
        assertEquals("- route: {}\n", Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8),
                "nothing is written while the question is open");
        assertNotNull(bridge.request);
        assertEquals("- route: {}\n", bridge.request.oldContent());
    }

    @Test
    void liveModeDefersOtherFilesWhileAnEditIsParkedInTheEditor(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        Files.writeString(dir.resolve("application.properties"), "a=1\n");
        ConfirmingBridge bridge = new ConfirmingBridge(true) {
            @Override
            public String parkedReplayFile() {
                return "demo.camel.yaml";
            }

            @Override
            public McpFacade.ReplayOutcome replayFileWrite(McpFacade.FileWrite request) {
                this.request = request;
                return new McpFacade.ReplayOutcome(true, 1, List.of(), request.newContent());
            }
        };
        McpFacade facade = facade(dir, true, bridge);
        facade.setWriteMode(McpFacade.WriteMode.LIVE);

        JsonObject other = facade.writeFile("demo", "application.properties", "a=2\n", true);
        assertEquals("deferred", other.getString("status"));
        assertTrue(other.getString("message").contains("demo.camel.yaml"));
        assertTrue(other.getString("message").contains("end your turn"));
        assertEquals("a=1\n", Files.readString(dir.resolve("application.properties"), StandardCharsets.UTF_8));
        assertEquals(0, bridge.asked, "no confirm dialog on top of the parked edit");
        // a new file is deferred as well, not confirmed
        assertEquals("deferred", facade.writeFile("demo", "new.camel.yaml", "- route: {}\n", true).getString("status"));
        assertFalse(Files.exists(dir.resolve("new.camel.yaml")));

        // the parked file itself continues in the editor
        JsonObject same = facade.writeFile("demo", "demo.camel.yaml", "- route:\n    id: a\n", true);
        assertEquals("written", same.getString("status"));
        assertEquals("demo.camel.yaml", bridge.request.file());
    }

    @Test
    void rejectsPathsOutsideTheSourceDirectoryAndUnknownIntegrations(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        ConfirmingBridge bridge = new ConfirmingBridge(true);
        McpFacade facade = facade(dir, true, bridge);

        assertEquals("error", facade.writeFile("demo", "../escape.yaml", "x", true).getString("status"));
        assertEquals("error", facade.writeFile("demo", "sub/dir.yaml", "x", true).getString("status"));
        assertEquals("error", facade.writeFile("demo", "", "x", true).getString("status"));
        assertEquals("error", facade.writeFile("demo", "demo.camel.yaml", null, true).getString("status"));
        assertEquals("error", facade.writeFile("nope", "demo.camel.yaml", "x", true).getString("status"));
        assertEquals(0, bridge.asked);
        assertFalse(Files.exists(dir.getParent().resolve("escape.yaml")));

        // reading reports the same directory knowledge the agent needs before writing
        JsonObject files = facade.getFiles("demo", null);
        assertNotNull(files);
        assertEquals(dir.toString(), files.getString("directory"));
        assertTrue(files.getBoolean("devMode"));
        assertTrue(files.getString("editing").contains("camel_write_file"));
    }
}
