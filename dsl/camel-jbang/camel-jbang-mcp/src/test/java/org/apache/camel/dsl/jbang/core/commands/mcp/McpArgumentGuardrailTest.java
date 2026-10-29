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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolInputGuardrail.ToolInputContext;
import io.quarkiverse.mcp.server.ToolManager.ToolArgument;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolOutputGuardrail.ToolOutputContext;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.json.JsonObject;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolArgumentAliases;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26399: the guardrail every tool declares takes the argument names models carry over from another tool for the
 * declared ones before the server checks them, answers a call that still misses a required argument with the arguments
 * of the tool, and notes the declared names in a JSON object answer. The tools are described from their real
 * declarations, as the server describes them.
 */
class McpArgumentGuardrailTest {

    private final McpArgumentGuardrail guardrail = guardrail();

    private static McpArgumentGuardrail guardrail() {
        McpArgumentGuardrail guardrail = new McpArgumentGuardrail();
        guardrail.callNote = new McpArgumentGuardrail.CallNote();
        return guardrail;
    }

    @Test
    void everyToolDeclaresTheGuardrail() throws Exception {
        List<Method> tools = McpToolClasses.toolMethods();

        // guard against a scan that finds nothing and makes the check below vacuous
        assertThat(tools).hasSizeGreaterThan(80);
        assertThat(tools)
                .filteredOn(m -> !declaresGuardrail(m))
                .as("tools without @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)")
                .extracting(m -> m.getDeclaringClass().getSimpleName() + "#" + m.getName())
                .isEmpty();
    }

    private static boolean declaresGuardrail(Method method) {
        ToolGuardrails guardrails = method.getAnnotation(ToolGuardrails.class);
        return guardrails != null && Arrays.asList(guardrails.input()).contains(McpArgumentGuardrail.class)
                && Arrays.asList(guardrails.output()).contains(McpArgumentGuardrail.class);
    }

    @Test
    void theAliasesNameArgumentsOfTheToolsWhichStayRequired() throws Exception {
        for (String tool : List.of("camel_catalog_find", "camel_catalog_example_file", "camel_validate_source")) {
            List<ToolArgument> arguments = McpToolClasses.toolInfo(McpToolClasses.toolMethod(tool)).arguments();
            List<String> names = arguments.stream().map(ToolArgument::name).toList();
            assertThat(ToolArgumentAliases.aliases(tool)).as(tool).isNotEmpty().allSatisfy((alias, name) -> {
                assertThat(names).as(tool + " declares " + name).contains(name);
                assertThat(names).as(tool + " does not declare " + alias).doesNotContain(alias);
            });
        }
        // the schemas the clients get still say which argument a call needs
        assertThat(required("camel_catalog_find")).containsExactly("term");
        assertThat(required("camel_catalog_example_file")).containsExactly("example", "file");
        assertThat(required("camel_validate_source")).containsExactly("file");
    }

    private static List<String> required(String tool) throws Exception {
        return McpToolClasses.toolInfo(McpToolClasses.toolMethod(tool)).arguments().stream()
                .filter(ToolArgument::required).map(ToolArgument::name).toList();
    }

    @Test
    void theCallsOfTheIssueRunWithTheDeclaredNames() throws Exception {
        assertThat(input("camel_catalog_find", "query", "activemq classic 6 jms").getArguments().getMap())
                .containsExactly(Map.entry("term", "activemq classic 6 jms"));
        assertThat(input("camel_catalog_find", "name", "groovy").getArguments().getMap())
                .containsExactly(Map.entry("term", "groovy"));
        assertThat(input("camel_catalog_example_file", "name", "contracts/rest-code-first",
                "file", "rest-code-first.camel.yaml").getArguments().getMap())
                .containsExactly(Map.entry("example", "contracts/rest-code-first"),
                        Map.entry("file", "rest-code-first.camel.yaml"));
        assertThat(input("camel_validate_source", "name", "hr-api-call.yaml", "content", "- route:")
                .getArguments().getMap())
                .containsExactly(Map.entry("file", "hr-api-call.yaml"), Map.entry("content", "- route:"));
        assertThat(input("camel_validate_source", "filename", "routes.yaml", "source", "- route:")
                .getArguments().getMap())
                .containsExactly(Map.entry("file", "routes.yaml"), Map.entry("content", "- route:"));
        // the path wins over the file name, which is ignored; resolving an absolute path is the tool's business
        assertThat(input("camel_validate_source", "path", "/workspace/camel/salesleads.yaml",
                "filename", "salesleads.yaml").getArguments().getMap())
                .containsEntry("file", "/workspace/camel/salesleads.yaml");
    }

    @Test
    void theDeclaredArgumentWinsAndAToolOwnNameIsLeftAlone() throws Exception {
        assertThat(input("camel_catalog_find", "term", "mqtt", "query", "s3").getArguments().getMap())
                .containsEntry("term", "mqtt").containsEntry("query", "s3");
        // camel_catalog_doc and camel_catalog_sample take name themselves
        assertThat(input("camel_catalog_doc", "name", "kafka").getArguments().getMap())
                .containsExactly(Map.entry("name", "kafka"));
        assertThat(input("camel_catalog_sample", "name", "split").getArguments().getMap())
                .containsExactly(Map.entry("name", "split"));
        assertThat(guardrail.callNote.take()).isNull();
    }

    @Test
    void aMissingArgumentIsAnsweredWithTheArgumentsOfTheTool() {
        assertThatThrownBy(() -> input("camel_catalog_find", "q", "mqtt"))
                .isInstanceOf(ToolCallException.class)
                .hasMessage("camel_catalog_find takes term (required), kind, limit, camelVersion; got q");
        assertThatThrownBy(() -> input("camel_catalog_example_file", "name", "timer-log"))
                .hasMessage("camel_catalog_example_file takes example (required), file (required); got name");
        // any tool, not only the ones with aliases
        assertThatThrownBy(() -> input("camel_write_file", "file", "routes.yaml"))
                .hasMessage("camel_write_file takes directory, file (required), content (required), camelVersion;"
                            + " got file");
        assertThatThrownBy(() -> input("camel_catalog_sample"))
                .hasMessage("camel_catalog_sample takes name (required), kind, limit, camelVersion; got no arguments");
    }

    @Test
    void theAnswerOfAnAliasedCallSaysTheDeclaredNames() throws Exception {
        input("camel_catalog_find", "query", "mqtt");

        ToolResponse response = output(ToolResponse.success("{\"term\":\"mqtt\",\"matches\":[{\"name\":\"paho\"}]}"));

        JsonObject answer = new JsonObject(response.firstContent().asText().text());
        assertThat(answer.fieldNames()).containsExactly(ToolArgumentAliases.NOTE_KEY, "term", "matches");
        assertThat(answer.getString(ToolArgumentAliases.NOTE_KEY)).isEqualTo("term is the argument name (got query)");
        assertThat(answer.getJsonArray("matches").getJsonObject(0).getString("name")).isEqualTo("paho");
        assertThat(response.isError()).isFalse();
    }

    @Test
    void anUnknownArgumentIsNotedOnAnOtherwiseValidCall() throws Exception {
        input("camel_catalog_find", "term", "mqtt", "limit", 1, "bogus", "x");

        ToolResponse response = output(ToolResponse.success("{\"term\":\"mqtt\"}"));

        assertThat(new JsonObject(response.firstContent().asText().text()).getString(ToolArgumentAliases.NOTE_KEY))
                .isEqualTo("ignored: bogus; camel_catalog_find takes term (required), kind, limit, camelVersion");
    }

    @Test
    void aCallWithTheDeclaredNamesIsAnsweredAsItIs() throws Exception {
        ToolInputContext context = input("camel_catalog_find", "term", "mqtt", "limit", 1);
        assertThat(context.getArguments().getMap()).containsExactly(Map.entry("term", "mqtt"), Map.entry("limit", 1));

        ToolResponse answer = ToolResponse.success("{\"term\":\"mqtt\"}");
        assertThat(output(answer)).isSameAs(answer);
    }

    @Test
    void onlyAJsonObjectAnswerGetsTheNote() {
        String note = "term is the argument name (got query)";
        assertThat(McpArgumentGuardrail.withNote(ToolResponse.error("No such file in the directory: x.yaml"), note))
                .isNull();
        assertThat(McpArgumentGuardrail.withNote(ToolResponse.success("No running Camel processes found"), note))
                .isNull();
        assertThat(McpArgumentGuardrail.withNote(ToolResponse.success("[\"a\"]"), note)).isNull();
        assertThat(McpArgumentGuardrail.withNote(ToolResponse.success("{not json"), note)).isNull();
        assertThat(McpArgumentGuardrail.withNote(ToolResponse.success(new TextContent("{}"), new TextContent("{}")),
                note)).isNull();
        assertThat(McpArgumentGuardrail.withNote(ToolResponse.structuredSuccess(Map.of("a", 1)), note)).isNull();
        assertThat(McpArgumentGuardrail.withNote(ToolResponse.success("{}"), note).firstContent().asText().text())
                .isEqualTo("{\"argumentNote\":\"term is the argument name (got query)\"}");
    }

    @Test
    void theNoteOfOneCallDoesNotReachTheNext() throws Exception {
        input("camel_catalog_find", "query", "mqtt");
        output(ToolResponse.success("{}"));

        ToolResponse answer = ToolResponse.success("{\"term\":\"s3\"}");
        assertThat(output(answer)).isSameAs(answer);
    }

    /** Runs the input guardrail on a call of the tool with these arguments; the context has the arguments it set. */
    private ToolInputContext input(String tool, Object... pairs) throws Exception {
        JsonObject arguments = new JsonObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            arguments.put((String) pairs[i], pairs[i + 1]);
        }
        InputContext context = new InputContext(McpToolClasses.toolInfo(McpToolClasses.toolMethod(tool)), arguments);
        guardrail.apply(context);
        return context;
    }

    /** Runs the output guardrail on the response of the call last given to {@link #input}. */
    private ToolResponse output(ToolResponse response) {
        OutputContext context = new OutputContext(response);
        guardrail.apply(context);
        return context.response;
    }

    private static final class InputContext implements ToolInputContext {

        private final ToolInfo tool;
        private JsonObject arguments;

        InputContext(ToolInfo tool, JsonObject arguments) {
            this.tool = tool;
            this.arguments = arguments;
        }

        @Override
        public ToolInfo getTool() {
            return tool;
        }

        @Override
        public McpConnection getConnection() {
            return null;
        }

        @Override
        public RequestId getRequestId() {
            return null;
        }

        @Override
        public JsonObject getArguments() {
            return arguments;
        }

        @Override
        public Meta getMeta() {
            return null;
        }

        @Override
        public void setArguments(JsonObject arguments) {
            this.arguments = arguments;
        }
    }

    private static final class OutputContext implements ToolOutputContext {

        private ToolResponse response;

        OutputContext(ToolResponse response) {
            this.response = response;
        }

        @Override
        public ToolInfo getTool() {
            return null;
        }

        @Override
        public McpConnection getConnection() {
            return null;
        }

        @Override
        public RequestId getRequestId() {
            return null;
        }

        @Override
        public Meta getMeta() {
            return null;
        }

        @Override
        public ToolResponse getResponse() {
            return response;
        }

        @Override
        public void setResponse(ToolResponse response) {
            this.response = response;
        }
    }
}
