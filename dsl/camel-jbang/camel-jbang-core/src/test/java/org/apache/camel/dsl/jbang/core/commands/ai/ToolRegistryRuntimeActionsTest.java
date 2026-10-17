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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The requests the runtime tools send to the CLI connector, recorded instead of sent: the connector's route-dump and
 * route-structure actions read the routes from {@code filter} (as {@code camel cmd route-dump} sends it).
 */
class ToolRegistryRuntimeActionsTest {

    private final List<JsonObject> requests = new ArrayList<>();

    private final ToolContext ctx = new ToolContext() {
        @Override
        public String executeAction(String action, Consumer<JsonObject> configure) {
            JsonObject request = new JsonObject();
            request.put("action", action);
            if (configure != null) {
                configure.accept(request);
            }
            requests.add(request);
            return "{}";
        }
    };

    ToolRegistryRuntimeActionsTest() {
        ctx.selectProcess(4711);
    }

    @Test
    void routeDumpSendsTheRouteIdAsFilter() {
        ToolRegistry.execute("get_route_dump", ctx, Map.of("routeId", "orders"));

        assertThat(requests).singleElement().satisfies(r -> {
            assertThat(r.getString("action")).isEqualTo("route-dump");
            assertThat(r.getString("filter")).isEqualTo("orders");
            assertThat(r).doesNotContainKey("id");
        });
    }

    @Test
    void routeStructureSendsTheRouteIdAsFilterAndBrief() {
        ToolRegistry.execute("get_route_structure", ctx, Map.of("routeId", "orders", "brief", "true"));
        ToolRegistry.execute("get_route_structure", ctx, Map.of());

        assertThat(requests).extracting(r -> r.getString("filter")).containsExactly("orders", "*");
        assertThat(requests).extracting(r -> r.getString("brief")).containsExactly("true", "false");
    }

    @Test
    void routeAnalysisFiltersBothActions() {
        ToolRegistry.execute("get_route_analysis", ctx, Map.of("routeId", "orders"));

        assertThat(requests).extracting(r -> r.getString("action")).containsExactly("route-structure", "route-dump");
        assertThat(requests).extracting(r -> r.getString("filter")).containsOnly("orders");
    }

    @Test
    void heapDumpRefusesANameWithADirectory() {
        assertThatThrownBy(() -> ToolRegistry.execute("write_heap_dump", ctx, Map.of("name", "../../tmp/x")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("without a directory");
        assertThatThrownBy(() -> ToolRegistry.execute("write_heap_dump", ctx, Map.of("name", "..")))
                .isInstanceOf(ToolExecutionException.class);
        assertThat(requests).isEmpty();

        ToolRegistry.execute("write_heap_dump", ctx, Map.of("name", "before-fix"));
        assertThat(requests).singleElement().satisfies(r -> assertThat(r.getString("name")).isEqualTo("before-fix"));
    }

    @Test
    void sendingAMessageIsDestructive() {
        ToolDescriptor send = ToolRegistry.findTool("send_message");
        assertThat(send.isReadOnly()).isFalse();
        assertThat(send.isDestructive()).isTrue();
    }
}
