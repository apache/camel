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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI tools ask the user, through the monitor, before a call that reaches beyond the integration, may destroy data
 * or runs code (ToolConfirms), and not while a tape drives the monitor.
 */
class McpFacadeConfirmTest {

    /** Answers the confirmation without a UI, and remembers what it was asked. */
    static final class AnsweringBridge extends McpFacadeWriteFileTest.ConfirmingBridge {
        final List<ToolConfirms.Call> calls = new ArrayList<>();

        AnsweringBridge(boolean answer) {
            super(answer);
        }

        @Override
        public boolean confirmToolCall(ToolConfirms.Call call) {
            calls.add(call);
            return answer;
        }
    }

    private static MonitorContext context() {
        IntegrationInfo info = new IntegrationInfo();
        info.name = "shop";
        info.pid = "1";
        EndpointInfo kafka = new EndpointInfo();
        kafka.uri = "kafka://orders?brokers=localhost%3A9092";
        info.endpoints.add(kafka);
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1";
        return ctx;
    }

    private static McpFacade facade(MonitorContext ctx, AnsweringBridge bridge) {
        return new McpFacade(
                ctx, new AtomicReference<>(List.of()), null, null, null, null, null, null, null, null, null, null,
                bridge);
    }

    @Test
    void aMessageOutsideTheIntegrationIsSentOnlyWhenTheUserConfirmsIt() {
        AnsweringBridge bridge = new AnsweringBridge(false);
        McpFacade facade = facade(context(), bridge);

        JsonObject result = facade.sendMessage("file:/Users/me?fileName=.zshrc", "x", null);

        assertThat(result.getString("status")).isEqualTo("rejected");
        assertThat(result.getString("message")).contains("The user did not confirm this in the TUI, so it did not run");
        assertThat(bridge.calls).singleElement().extracting(ToolConfirms.Call::target)
                .isEqualTo("file:/Users/me?fileName=.zshrc");
        // the endpoints of the integration come from its status
        assertThat(facade.selectedEndpointUris()).containsExactly("kafka://orders?brokers=localhost%3A9092");
        assertThat(facade.confirm(ToolConfirms.sendMessage("kafka:orders?brokers=localhost:9092",
                facade.selectedEndpointUris(), null))).isNull();
        assertThat(bridge.calls).hasSize(1);
    }

    @Test
    void aBodyReadFromAFileOutsideTheProjectAsksEvenForTheLog(@TempDir Path project) {
        AnsweringBridge bridge = new AnsweringBridge(false);
        MonitorContext ctx = context();
        // the integration runs in the project folder, which is also its source directory
        ctx.findSelectedIntegration().directory = project.toString();
        McpFacade facade = facade(ctx, bridge);

        JsonObject result = facade.sendMessage("log:demo", "file:/etc/hosts", null);

        assertThat(result.getString("status")).isEqualTo("rejected");
        assertThat(bridge.calls).singleElement().satisfies(call -> {
            assertThat(call.title()).isEqualTo("send a file from outside the project");
            assertThat(call.target()).startsWith("log:demo\nbody: file:").endsWith("hosts");
        });
    }

    @Test
    void sqlThatIsNotASelectAndARowUpdateAskFirst() {
        AnsweringBridge bridge = new AnsweringBridge(false);
        McpFacade facade = facade(context(), bridge);

        assertThat(facade.executeSql("DROP TABLE orders", null, 10, 5).getString("status")).isEqualTo("rejected");
        assertThat(facade.updateRow("orders", null, "{\"id\":1}", "{\"status\":\"x\"}").getString("status"))
                .isEqualTo("rejected");

        assertThat(bridge.calls).extracting(ToolConfirms.Call::title)
                .containsExactly("run SQL that is not a SELECT", "update a row of orders");
    }

    @Test
    void groovyAsksFirstThroughTheTool() throws Exception {
        AnsweringBridge bridge = new AnsweringBridge(false);
        TuiToolRegistry registry = new TuiToolRegistry(facade(context(), bridge));

        JsonObject result = (JsonObject) Jsoner.deserialize(registry.execute("camel_eval_expression",
                new JsonObject(Map.of("language", "groovy", "expression", "'touch /tmp/x'.execute()"))));

        assertThat(result.getString("status")).isEqualTo("rejected");
        assertThat(bridge.calls).singleElement().extracting(ToolConfirms.Call::title)
                .isEqualTo("evaluate a groovy expression");
    }

    @Test
    void whatTheUserConfirmsGoesAhead() {
        AnsweringBridge bridge = new AnsweringBridge(true);
        McpFacade facade = facade(context(), bridge);

        assertThat(facade.confirm(ToolConfirms.sql("DELETE FROM orders"))).isNull();
        assertThat(bridge.calls).hasSize(1);
    }

    @Test
    void nothingAsksWhileATapeDrivesTheMonitor() {
        // CAMEL-25444: a model records a tape, or --record plays one, and no one is there to answer
        AnsweringBridge bridge = new AnsweringBridge(false);
        MonitorContext ctx = context();
        ctx.tapeDriven = () -> true;
        McpFacade facade = facade(ctx, bridge);

        assertThat(facade.confirm(ToolConfirms.sql("DROP TABLE orders"))).isNull();
        assertThat(facade.confirm(ToolConfirms.evalExpression("groovy", "1"))).isNull();
        assertThat(bridge.calls).isEmpty();
    }
}
