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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25676: camel ask gives a local model the core tools and a hosted one the full set, and asks before the calls
 * that reach beyond the integration, may destroy data or run code; a call the user does not confirm does not run.
 */
class AskToolsTest {

    private static final String ROUTE = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - log:
                        message: hello
            """;

    @TempDir
    Path dir;

    private final List<ToolConfirms.Call> asked = new ArrayList<>();

    private AskTools tools(boolean confirm) {
        return new AskTools(-1, call -> {
            asked.add(call);
            return confirm;
        });
    }

    @Test
    void aLocalModelGetsTheCoreTools() {
        List<String> core = tools(true).toolNames(true);

        assertThat(core).contains("camel_get_log", "camel_get_errors", "camel_edit_file", "camel_write_file",
                "get_context", "get_routes", "get_history", "send_message", "cli_exec")
                .doesNotContain("get_errors", "stop_application", "get_thread_dump", "cli_list_commands");
        assertThat(core).hasSizeLessThan(30);
    }

    @Test
    void aHostedModelGetsTheFullSetWithoutTheReplacedTools() {
        AskTools tools = tools(true);
        List<String> full = tools.toolNames(false);

        assertThat(full).contains("stop_application", "get_thread_dump", "camel_catalog_doc", "cli_list_commands",
                "cli_command_help", "cli_exec")
                .doesNotContainAnyElementsOf(AskTools.SUPERSEDED)
                .doesNotContain("write_file", "read_file", "list_files");
        assertThat(tools.buildToolDefinitions(false)).extracting(LlmClient.ToolDef::name).containsExactlyElementsOf(full);
    }

    @Test
    void aWriteIsAskedWithItsChangeAndNotWrittenWhenRefused() throws IOException {
        Path file = dir.resolve("tick.camel.yaml");
        Files.writeString(file, ROUTE);
        JsonObject args = new JsonObject(
                Map.of("directory", dir.toString(), "file", "tick.camel.yaml",
                        "content", ROUTE.replace("message: hello", "message: bye")));

        AskTools refusing = tools(false);
        refusing.context().setDefaultDirectory(dir);
        assertThat(refusing.executeTool("camel_write_file", args)).contains("\"status\":\"rejected\"");
        assertThat(asked).singleElement().satisfies(call -> {
            assertThat(call.title()).isEqualTo("change tick.camel.yaml");
            assertThat(call.target()).contains("-            message: hello").contains("+            message: bye");
        });
        assertThat(file).content().contains("message: hello");

        tools(true).executeTool("camel_write_file", args);
        assertThat(file).content().contains("message: bye");
    }

    @Test
    void aWriteOutsideTheProjectShowsTheWholePath() throws IOException {
        Path project = Files.createDirectory(dir.resolve("project"));
        Path elsewhere = Files.createDirectory(dir.resolve("elsewhere"));
        AskTools tools = tools(false);
        tools.context().setDefaultDirectory(project);

        tools.executeTool("camel_write_file", new JsonObject(
                Map.of("directory", elsewhere.toString(), "file", "tick.camel.yaml", "content", ROUTE)));

        assertThat(asked).singleElement().extracting(ToolConfirms.Call::title)
                .isEqualTo("create " + elsewhere.resolve("tick.camel.yaml").toAbsolutePath().normalize());
    }

    @Test
    void aMessageToAnEndpointOutsideTheIntegrationIsNotSentWhenRefused() {
        String result = tools(false).executeTool("send_message",
                new JsonObject(Map.of("endpoint", "kafka:orders", "body", "an order")));

        assertThat(result).contains("rejected").contains("send a message to an endpoint the integration does not have");
        assertThat(asked).hasSize(1);
        // an internal endpoint stays inside the integration: nothing is asked
        asked.clear();
        tools(false).executeTool("send_message", new JsonObject(Map.of("endpoint", "direct:start", "body", "an order")));
        assertThat(asked).isEmpty();
    }

    @Test
    void aCamelCommandThatChangesSomethingIsAskedFirst() {
        assertThat(tools(false).executeTool("cli_exec", new JsonObject(Map.of("command", "camel stop orders"))))
                .contains("rejected");
        assertThat(asked).singleElement().extracting(ToolConfirms.Call::target).isEqualTo("camel stop orders");

        asked.clear();
        tools(false).executeTool("cli_exec", new JsonObject(Map.of("command", "get route")));
        assertThat(asked).isEmpty();
    }

    @Test
    void aCommandThatRunsUntilItIsStoppedIsRefusedWithTheFormThatReturns() {
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("run hello.yaml"))).contains("add --background");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("run hello.yaml --background"))).isNull();
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("run hello.yaml --max-seconds=10"))).isNull();
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("log orders"))).contains("add --follow=false");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("log orders --follow=false"))).isNull();
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("log orders --duration=5s"))).isNull();
        // trace and receive follow when they dump
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("trace orders"))).isNull();
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("trace orders --action=dump"))).contains("--follow=false");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("cmd receive --action dump"))).contains("--follow=false");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("get route --watch"))).contains("--duration");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("infra run kafka"))).contains("add --background");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("shell"))).contains("needs a terminal of its own");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("export --clean-dir --dir=out"))).contains("add --yes");
        assertThat(AskTools.blockingHint(Ask.tokenizeCommand("get route --json"))).isNull();

        String answer = tools(false).executeTool("cli_exec", new JsonObject(Map.of("command", "camel log orders")));
        assertThat(answer).contains("\"status\":\"refused\"").contains("--follow=false");
        assertThat(asked).isEmpty();
    }

    @Test
    void codeSqlThatChangesDataAndStoppingTheIntegrationAreAsked() {
        AskTools tools = tools(false);

        assertThat(tools.executeTool("camel_eval_expression",
                new JsonObject(Map.of("language", "groovy", "expression", "1 + 1")))).contains("rejected");
        assertThat(tools.executeTool("execute_sql", new JsonObject(Map.of("query", "DELETE FROM orders"))))
                .contains("rejected");
        assertThat(tools.executeTool("camel_control", new JsonObject(Map.of("action", "stop")))).contains("rejected");
        assertThat(tools.executeTool("stop_application", new JsonObject())).contains("rejected");
        assertThat(asked).hasSize(4);
    }

    @Test
    void theToolGroupsSayTheNamesOfTheSharedTools() {
        assertThat(AskTools.sharedToolNames(
                "HTTP: GET /orders; camel_runtime_http_request calls it, camel_runtime_http_endpoints lists the operations."))
                .isEqualTo("HTTP: GET /orders; http_request calls it, get_http_endpoints lists the operations.");
    }

    @Test
    void thePromptHasTheGuidelinesAndTheCallsThatAreAsked() {
        String prompt = AskTools.buildSystemPrompt(-1, null, true, List.of("SQL: one datasource"));

        assertThat(prompt).contains("YAML DSL shape").contains("read the log (camel_get_log)")
                .contains("Some calls ask the user first").contains("\nThe integration:\n- SQL: one datasource\n")
                .doesNotContain("stop_route").doesNotContain("do not use markdown");
    }
}
