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
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared authoring tools as camel-jbang-mcp exposes them (CAMEL-24695): one thin wrapper per registry tool, with
 * the same name and the annotations the access filter reads, so an agent gets the same Camel as through the TUI.
 */
class AuthoringToolsTest {

    private final AuthoringTools tools = new AuthoringTools();

    @Test
    void everySharedToolHasAWrapperWithMatchingHints() {
        List<String> wrapped = new ArrayList<>();
        for (Method m : AuthoringTools.class.getMethods()) {
            Tool tool = m.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            ToolDescriptor td = ToolRegistry.findTool(m.getName());
            assertThat(td).as(m.getName() + " is in the shared registry").isNotNull();
            assertThat(tool.annotations().readOnlyHint()).as(m.getName() + " readOnlyHint").isEqualTo(td.isReadOnly());
            assertThat(tool.annotations().destructiveHint()).as(m.getName() + " destructiveHint")
                    .isEqualTo(td.isDestructive());
            wrapped.add(m.getName());
        }
        // camel_error_diagnose keeps its wrapper in DiagnoseTools (catalog per runtime), the rest live here
        List<String> shared = new ArrayList<>(ToolRegistry.authoringTools().stream().map(ToolDescriptor::name).toList());
        shared.remove("camel_error_diagnose");
        assertThat(wrapped).containsExactlyInAnyOrderElementsOf(shared);
    }

    @Test
    void catalogDocAnswersWithOptionsAndUriRules() {
        JsonObject timer = tools.camel_catalog_doc(null, "timer", null, "component", null, null, null, null, null, null);
        assertThat(timer.getString("kind")).isEqualTo("component");
        assertThat(timer.getString("uriSyntax")).contains("(timerName) go in the path");
        assertThat(timer.getInteger("matchedOptions")).isGreaterThan(5);
        JsonObject check
                = tools.camel_catalog_doc(null, null, "timer:tick?periodd=5s", null, null, null, null, null, null, null);
        assertThat(check.getBoolean("valid")).isFalse();
        assertThat(List.copyOf(check.getCollection("problems")).get(0).toString()).contains("periodd");
        // a lookup that finds nothing answers with an error field and suggestions, as the TUI does, not an exception
        assertThat(tools.camel_catalog_doc(null, null, null, null, null, null, null, null, null, null).getString("error"))
                .contains("required");
        JsonObject mqtt = tools.camel_catalog_doc(null, "mqtt", null, "component", null, null, null, null, null, null);
        assertThat(mqtt.getString("error")).contains("mqtt");
        assertThat(List.copyOf(mqtt.getCollection("suggestions")).toString()).contains("paho-mqtt5");
    }

    @Test
    void filesAreReadAndWrittenInTheGivenDirectory(@TempDir Path dir) throws Exception {
        String route = "- route:\n    from:\n      uri: timer:tick\n      steps:\n        - log:\n            message: hi\n";
        JsonObject written = tools.camel_write_file(dir.toString(), "demo.camel.yaml", route, null);
        assertThat(written.getString("status")).isEqualTo("created");
        assertThat(Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8)).isEqualTo(route);

        JsonObject invalid = tools.camel_write_file(dir.toString(), "demo.camel.yaml",
                route.replace("message", "mesage"), null);
        assertThat(invalid.getString("status")).isEqualTo("invalid");
        assertThat(Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8)).isEqualTo(route);

        JsonObject listed = tools.camel_get_files(dir.toString(), null);
        assertThat(listed.getInteger("totalFiles")).isEqualTo(1);
        assertThat(tools.camel_get_files(dir.toString(), "demo.camel.yaml").getString("content")).isEqualTo(route);
        assertThat(tools.camel_validate_source(null, dir.toString(), "demo.camel.yaml", null, null).getBoolean("valid"))
                .isTrue();
        assertThatThrownBy(() -> tools.camel_get_files(null, null))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("directory is required");
    }

    @Test
    void anUnknownIntegrationNameIsAnError() {
        assertThatThrownBy(() -> tools.camel_eval_expression("${body}", null, "camel", "no-such-app-xyz-1"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("no-such-app-xyz-1");
        assertThatThrownBy(() -> tools.camel_get_log("no-such-app-xyz-1", null, null, null, null))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("no-such-app-xyz-1");
    }

    @Test
    void everyArgumentIsRequiredExactlyWhenTheSharedDescriptorSaysSo() {
        // the Quarkus server rejects a call that omits an argument declared required, so an optional argument with a
        // documented default ("omitted lists the files", "default: the only one running") must not be required here
        for (Method m : AuthoringTools.class.getMethods()) {
            if (m.getAnnotation(Tool.class) == null) {
                continue;
            }
            ToolDescriptor td = ToolRegistry.findTool(m.getName());
            List<ToolDescriptor.Param> params = td.params();
            // the connection is injected by the server, not an argument of the tool
            Parameter[] args = Arrays.stream(m.getParameters())
                    .filter(p -> p.getType() != McpConnection.class).toArray(Parameter[]::new);
            assertThat(args).as(m.getName() + " has one argument per descriptor parameter").hasSize(params.size());
            for (int i = 0; i < args.length; i++) {
                ToolArg arg = args[i].getAnnotation(ToolArg.class);
                assertThat(arg).as(m.getName() + " argument " + i).isNotNull();
                assertThat(arg.required()).as(m.getName() + " argument '" + params.get(i).name() + "' required")
                        .isEqualTo(params.get(i).required());
            }
        }
    }

    @Test
    void theListingMarksAToolDeterministicExactlyWhenTheSharedDescriptorSaysSo() {
        for (Class<?> type : List.of(AuthoringTools.class, DiagnoseTools.class)) {
            for (Method m : type.getMethods()) {
                ToolDescriptor td = m.getAnnotation(Tool.class) != null ? ToolRegistry.findTool(m.getName()) : null;
                if (td == null) {
                    continue;
                }
                MetaField meta = m.getAnnotation(MetaField.class);
                boolean marked = meta != null && "camel.apache.org/".equals(meta.prefix())
                        && "deterministic".equals(meta.name()) && "true".equals(meta.value());
                assertThat(marked).as(m.getName() + " marked deterministic").isEqualTo(td.isDeterministic());
                // a tool that is deterministic for some arguments (camel_validate_source with content) counts too
                boolean connected = Arrays.stream(m.getParameterTypes()).anyMatch(t -> t == McpConnection.class);
                assertThat(connected).as(m.getName() + " gets the connection to count its repeats")
                        .isEqualTo(td.isDeterministic() || td.deterministicWhen() != null);
            }
        }
    }

    @Test
    void aThirdIdenticalCatalogCallOfAConnectionGetsAShortNote() {
        AuthoringTools counted = new AuthoringTools();
        counted.repeatedCalls = new RepeatedCallSessions();
        McpConnection agent = connection("agent-1");

        JsonObject first = counted.camel_catalog_doc(agent, "sql", null, null, null, null, null, null, null, null);
        JsonObject second = counted.camel_catalog_doc(agent, "sql", null, null, null, null, null, null, null, null);
        JsonObject third = counted.camel_catalog_doc(agent, "sql", null, null, null, null, null, null, null, null);

        assertThat(first.get("repeated")).isNull();
        assertThat(second).isEqualTo(first);
        assertThat(third.getBoolean("repeated")).isTrue();
        assertThat(third.getInteger("timesAsked")).isEqualTo(3);
        assertThat(third.getString("note")).contains("camel_catalog_doc", "2 times");
        assertThat(third.toJson().length()).isLessThan(first.toJson().length() / 4);

        // another question, or another connection, is answered in full
        assertThat(counted.camel_catalog_doc(agent, "timer", null, null, null, null, null, null, null, null)
                .get("repeated")).isNull();
        assertThat(counted.camel_catalog_doc(connection("agent-2"), "sql", null, null, null, null, null, null, null, null)
                .get("repeated")).isNull();
    }

    /** CAMEL-25371: validating the same content again is a repeat, also over the camel mcp server. */
    @Test
    void aThirdIdenticalValidationOfTheSameContentGetsAShortNote() {
        AuthoringTools counted = new AuthoringTools();
        counted.repeatedCalls = new RepeatedCallSessions();
        McpConnection agent = connection("agent-1");
        String content = "- from:\n    uri: timer:x\n    steps:\n      - to: log:x\n";

        JsonObject first = counted.camel_validate_source(agent, null, "route.camel.yaml", content, null);
        JsonObject second = counted.camel_validate_source(agent, null, "route.camel.yaml", content, null);
        JsonObject third = counted.camel_validate_source(agent, null, "route.camel.yaml", content, null);

        assertThat(first.get("repeated")).isNull();
        assertThat(second.get("repeated")).isNull();
        assertThat(third.getBoolean("repeated")).isTrue();
        assertThat(third.getString("note")).contains("camel_validate_source", "change the line the error names");
        // changed content is a new question
        assertThat(counted.camel_validate_source(agent, null, "route.camel.yaml", content + "\n", null)
                .get("repeated")).isNull();
    }

    private static McpConnection connection(String id) {
        return (McpConnection) Proxy.newProxyInstance(McpConnection.class.getClassLoader(),
                new Class<?>[] { McpConnection.class }, (proxy, method, args) -> "id".equals(method.getName()) ? id : null);
    }

    @Test
    void blankArgumentsAreLeftOutSoTheToolDefaultApplies() {
        Map<String, String> args = AuthoringTools.args("name", "timer", "kind", "", "limit", 3, "dev", true, "x", null);
        assertThat(args).containsExactly(Map.entry("name", "timer"), Map.entry("limit", "3"), Map.entry("dev", "true"));
        // a blank kind means auto-detect, not "a kind called nothing"
        assertThat(tools.camel_catalog_doc(null, "timer", "", "", null, null, null, "", "period", "").getString("kind"))
                .isEqualTo("component");
        assertThat(tools.camel_catalog_find(null, "mqtt", "", null, "").getInteger("count")).isGreaterThan(0);
    }
}
