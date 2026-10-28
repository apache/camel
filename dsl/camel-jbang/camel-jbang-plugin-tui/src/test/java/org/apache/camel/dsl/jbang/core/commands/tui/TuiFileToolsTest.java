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
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The file tools write to the source directory of an integration, after the user confirmed the change, also when the
 * agent passes the directory itself.
 */
class TuiFileToolsTest {

    private static final String OLD = "- route: {}\n";
    private static final String NEW = "- route:\n    id: x\n";

    @TempDir
    Path tempDir;

    @Test
    void theDirectoryOfTheIntegrationIsWrittenAfterTheUserConfirms() throws Exception {
        Path project = project();
        McpFacadeWriteFileTest.ConfirmingBridge bridge = new McpFacadeWriteFileTest.ConfirmingBridge(false);
        TuiToolRegistry registry = new TuiToolRegistry(McpFacadeWriteFileTest.facade(project, false, bridge));

        JsonObject result = call(registry, "camel_write_file",
                Map.of("directory", project.toString(), "file", "demo.camel.yaml", "content", NEW));

        assertThat(result.getString("status")).isEqualTo("rejected");
        assertThat(bridge.asked).isEqualTo(1);
        assertThat(Files.readString(project.resolve("demo.camel.yaml"))).isEqualTo(OLD);
    }

    @Test
    void theSameDirectoryWrittenAnotherWayIsTheIntegrations() throws Exception {
        Path project = project();
        Files.createDirectories(project.resolve("sub"));
        McpFacadeWriteFileTest.ConfirmingBridge bridge = new McpFacadeWriteFileTest.ConfirmingBridge(true);
        TuiToolRegistry registry = new TuiToolRegistry(McpFacadeWriteFileTest.facade(project, false, bridge));

        JsonObject result = call(registry, "camel_write_file",
                Map.of("directory", project.resolve("sub/..").toString(), "file", "demo.camel.yaml", "content", NEW));

        assertThat(result.getString("status")).isEqualTo("overwritten");
        assertThat(bridge.asked).isEqualTo(1);
        assertThat(Files.readString(project.resolve("demo.camel.yaml"))).isEqualTo(NEW);
    }

    @Test
    void anEditIsConfirmedAsAWriteIs() throws Exception {
        Path project = project();
        McpFacadeWriteFileTest.ConfirmingBridge bridge = new McpFacadeWriteFileTest.ConfirmingBridge(false);
        TuiToolRegistry registry = new TuiToolRegistry(McpFacadeWriteFileTest.facade(project, false, bridge));

        JsonObject result = call(registry, "camel_edit_file",
                Map.of("directory", project.toString(), "file", "demo.camel.yaml", "find", "route: {}",
                        "replace", "route: {id: y}"));

        assertThat(result.getString("status")).isEqualTo("rejected");
        assertThat(bridge.asked).isEqualTo(1);
        assertThat(Files.readString(project.resolve("demo.camel.yaml"))).isEqualTo(OLD);
    }

    @Test
    void anotherDirectoryIsNotWritten() throws Exception {
        Path project = project();
        Path elsewhere = Files.createDirectories(tempDir.resolve("elsewhere"));
        McpFacadeWriteFileTest.ConfirmingBridge bridge = new McpFacadeWriteFileTest.ConfirmingBridge(true);
        TuiToolRegistry registry = new TuiToolRegistry(McpFacadeWriteFileTest.facade(project, false, bridge));

        JsonObject result = call(registry, "camel_write_file",
                Map.of("directory", elsewhere.toString(), "file", "demo.camel.yaml", "content", NEW));

        assertThat(result.getString("status")).isEqualTo("error");
        assertThat(result.getString("error")).contains("is not the source directory");
        assertThat(bridge.asked).isZero();
        assertThat(elsewhere.resolve("demo.camel.yaml")).doesNotExist();
    }

    private Path project() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.writeString(project.resolve("demo.camel.yaml"), OLD);
        return project;
    }

    private static JsonObject call(TuiToolRegistry registry, String tool, Map<String, Object> args) {
        return Jsoner.deserialize(registry.executeSharedWithTuiExtras(tool, args), new JsonObject());
    }
}
