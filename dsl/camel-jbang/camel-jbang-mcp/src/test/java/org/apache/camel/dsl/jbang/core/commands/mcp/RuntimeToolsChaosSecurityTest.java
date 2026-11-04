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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.Tool;
import org.apache.camel.dsl.jbang.core.commands.ai.AppFeatures;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolGroups;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The marks of the chaos tools and what they mean: adding and changing a rule is destructive and asked first
 * ({@link ToolConfirms}), the list and the report only read, and each access level of the security layer
 * ({@link McpAccessFilter} decides from the annotations) offers the tools by their marks.
 */
class RuntimeToolsChaosSecurityTest {

    /** The tools of the chaos group, with readOnlyHint and destructiveHint as agreed for the group. */
    private static final Map<String, boolean[]> MARKS = new LinkedHashMap<>();

    static {
        MARKS.put("camel_runtime_chaos_list", new boolean[] { true, false });
        MARKS.put("camel_runtime_chaos_report", new boolean[] { true, false });
        MARKS.put("camel_runtime_chaos_add", new boolean[] { false, true });
        MARKS.put("camel_runtime_chaos_change", new boolean[] { false, true });
        MARKS.put("camel_runtime_chaos_remove", new boolean[] { false, false });
        MARKS.put("camel_runtime_chaos_clear", new boolean[] { false, false });
    }

    private static Tool.Annotations annotations(String name) {
        return Arrays.stream(RuntimeTools.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name) && m.isAnnotationPresent(Tool.class))
                .findFirst().orElseThrow(() -> new AssertionError("No tool " + name))
                .getAnnotation(Tool.class).annotations();
    }

    private static List<String> offered(McpSecurityConfig.AccessLevel level) {
        return MARKS.keySet().stream()
                .filter(name -> level.permits(annotations(name).readOnlyHint(), annotations(name).destructiveHint()))
                .toList();
    }

    @Test
    void everyToolOfTheGroupHasItsMarks() {
        assertThat(ToolGroups.select(AppFeatures.fromStatus(new JsonObject(Map.of("chaos", Map.of("enabled", true)))))
                .mcpTools())
                .containsExactlyElementsOf(MARKS.keySet());
        MARKS.forEach((name, marks) -> {
            Tool.Annotations a = annotations(name);
            assertThat(a.readOnlyHint()).as(name + " readOnlyHint").isEqualTo(marks[0]);
            assertThat(a.destructiveHint()).as(name + " destructiveHint").isEqualTo(marks[1]);
            assertThat(a.openWorldHint()).as(name + " openWorldHint").isFalse();
            // the shared registry, which camel ask uses, has the same marks
            ToolDescriptor shared = ToolRegistry.findTool(name.substring("camel_runtime_".length()));
            assertThat(shared).as(name).isNotNull();
            assertThat(shared.isReadOnly()).as(shared.name()).isEqualTo(marks[0]);
            assertThat(shared.isDestructive()).as(shared.name()).isEqualTo(marks[1]);
        });
    }

    @Test
    void exactlyTheDestructiveToolsAreInToolConfirms() {
        MARKS.forEach((name, marks) -> {
            String command = name.substring("camel_runtime_chaos_".length());
            ToolConfirms.Call call = ToolConfirms.chaos(command, "shop", "r1", "endpoint=kafka:* error rate=0.1");
            assertThat(call != null).as(name + " is asked first").isEqualTo(marks[1]);
            if (call != null) {
                // the user sees the rule
                assertThat(call.target()).isEqualTo("endpoint=kafka:* error rate=0.1");
            }
        });
    }

    @Test
    void theAccessLevelsOfferTheToolsByTheirMarks() {
        assertThat(offered(McpSecurityConfig.AccessLevel.READ_ONLY))
                .containsExactly("camel_runtime_chaos_list", "camel_runtime_chaos_report");
        // as for route_control, sql, send and receive: no destructive tool at read-write
        assertThat(offered(McpSecurityConfig.AccessLevel.READ_WRITE)).containsExactly(
                "camel_runtime_chaos_list", "camel_runtime_chaos_report", "camel_runtime_chaos_remove",
                "camel_runtime_chaos_clear");
        assertThat(offered(McpSecurityConfig.AccessLevel.ADMIN)).containsExactlyElementsOf(MARKS.keySet());
        // with the security layer on, the interceptor of @McpSecured audits each call (and redacts the answer when
        // camel.mcp.security.redaction.enabled=true)
        assertThat(RuntimeTools.class.isAnnotationPresent(McpSecured.class)).isTrue();
    }
}
