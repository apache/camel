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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The answers of the MCP server name its own tools, not those of camel ask (CAMEL-26932).
 */
class McpToolNamesTest {

    @Test
    void theToolsOfCamelAskAreNamedAsTheToolsOfTheServer() {
        assertThat(McpToolNames.of("No running Camel process matches 'orders'; list_processes shows what runs"))
                .isEqualTo("No running Camel process matches 'orders'; camel_runtime_processes shows what runs");
        assertThat(McpToolNames.of("Example not found: nope. Use list_examples to see the examples."))
                .isEqualTo("Example not found: nope. Use camel_catalog_examples to see the examples.");
        assertThat(McpToolNames.of("No process selected. Use select_process to connect to one."))
                .isEqualTo("No process selected. Pass nameOrPid to the tool to pick one.");
        // only whole names
        assertThat(McpToolNames.of("my_list_processes_x")).isEqualTo("my_list_processes_x");
    }

    @Test
    void anAnswer() {
        JsonObject answer = new JsonObject();
        answer.put("hint", "list_processes shows it once it is up");

        assertThat(McpToolNames.of(answer).getString("hint")).isEqualTo("camel_runtime_processes shows it once it is up");
    }

    @Test
    void anUnknownExampleNamesTheExampleToolOfTheServer() {
        // CAMEL-26932: the answer said "Use list_examples", a tool the MCP server does not have
        assertThatThrownBy(() -> AuthoringTools.call("get_example_file",
                AuthoringTools.args("example", "no-such-example-26932", "file", "x")))
                .isInstanceOf(ToolCallException.class)
                .hasMessageNotContaining("list_examples");
    }
}
