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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cli.connector.DefaultCliConnectorFactory;
import org.apache.camel.cli.connector.LocalCliConnector;
import org.apache.camel.component.chaos.ChaosManager;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;

/**
 * The chaos tools against a running integration: a CamelContext with camel-chaos and the CLI connector in this JVM,
 * reached over the files of the CLI connector as the MCP server reaches any integration. Only the lookup of the process
 * is faked, as the real one never takes this JVM for an integration. So a rule goes through the chaos action of the
 * connector, the ChaosManager and the parser of camel.chaos.rules, and the list and the report come from the chaos
 * console in the status the connector writes.
 * <p>
 * Isolated because it changes {@code user.home}, where the connector and the tools exchange their files.
 */
@Isolated
class RuntimeToolsChaosTest {

    private static final long PID = ProcessHandle.current().pid();
    private static final String RULE_ERROR = "endpoint=direct:target error rate=1";

    @TempDir
    Path home;

    private String userHome;
    private Path cliHome;
    private CamelContext context;
    private LocalCliConnector connector;
    private ProducerTemplate template;
    private final RuntimeTools tools = new RuntimeTools();

    @BeforeEach
    void useTheTemporaryHome() {
        userHome = System.getProperty("user.home");
        cliHome = CommandLineHelper.getHomeDir();
        System.setProperty("user.home", home.toString());
        CommandLineHelper.useHomeDir(home.toString());
        tools.runtimeService = new RuntimeService() {
            @Override
            public ProcessInfo findSingleProcess(String nameOrPid) {
                // the integration of the test runs in this JVM, which the real lookup never finds
                return "shop".equals(nameOrPid) ? new ProcessInfo(PID, "shop", "shop") : super.findSingleProcess(nameOrPid);
            }
        };
    }

    @AfterEach
    void stop() {
        try {
            if (template != null) {
                template.stop();
            }
            if (connector != null) {
                connector.stop();
            }
            if (context != null) {
                context.stop();
            }
        } finally {
            System.setProperty("user.home", userHome);
            CommandLineHelper.useHomeDir(cliHome.toString());
        }
    }

    private void start(String profile, Map<String, String> properties) throws Exception {
        context = new DefaultCamelContext();
        // only the connector of the test, which polls faster
        DefaultCliConnectorFactory disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        context.getCamelContextExtension().setProfile(profile);
        properties.forEach(context.getPropertiesComponent()::addInitialProperty);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("direct:target");
                from("direct:target").routeId("target").setBody(constant("done"));
            }
        });
        context.start();
        template = context.createProducerTemplate();
        connector = new LocalCliConnector(new DefaultCliConnectorFactory());
        connector.setCamelContext(context);
        connector.start();
        connector.updateDelay(100);
        // the tools read the status the connector writes
        awaitThat("the status with the chaos console", this::statusHasChaos);
    }

    private boolean statusHasChaos() {
        try {
            return tools.camel_runtime_chaos_list("shop").containsKey("enabled");
        } catch (ToolCallException e) {
            return false;
        }
    }

    private static void awaitThat(String what, Callable<Boolean> condition) {
        await(what).atMost(20, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS).until(condition);
    }

    /** The status is written every tick: the list once it shows what the condition wants. */
    private JsonObject awaitList(Predicate<JsonObject> condition) {
        JsonObject[] last = new JsonObject[1];
        awaitThat("the rules", () -> condition.test(last[0] = tools.camel_runtime_chaos_list("shop")));
        return last[0];
    }

    private static List<String> rules(JsonObject list) {
        return list.<List<Map<String, Object>>> getCollection("rules").stream()
                .map(r -> r.get("id") + " v" + r.get("version") + " " + r.get("rule")).toList();
    }

    private Exception send() {
        return template.send("direct:start", e -> e.getMessage().setBody("order")).getException();
    }

    @Test
    void everyToolAgainstARunningIntegration() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        assertThat(awaitList(l -> l.getBoolean("enabled")).<List<Object>> getCollection("rules")).isEmpty();

        JsonObject added = tools.camel_runtime_chaos_add("shop", RULE_ERROR);
        assertThat(added.getString("id")).isEqualTo("r1");
        assertThat(added.getInteger("version")).isEqualTo(1);
        // the rule in plain words: route start sends to direct:target
        assertThat(added.getString("description")).isEqualTo("fail every send to direct:target");
        // the fault is in place
        assertThat(send()).isNotNull();

        JsonObject changed = tools.camel_runtime_chaos_change("shop", "r1", "endpoint=direct:target delay duration=1ms");
        assertThat(changed.getString("id")).isEqualTo("r1");
        assertThat(changed.getInteger("version")).isEqualTo(2);
        assertThat(send()).isNull();

        assertThat(tools.camel_runtime_chaos_add("shop", "step=* delay duration=1ms").getString("id")).isEqualTo("r2");
        assertThat(rules(awaitList(l -> rules(l).size() == 2)))
                .containsExactly("r1 v2 endpoint=direct:target delay duration=1ms", "r2 v1 step=* delay duration=1ms");

        JsonObject removed = tools.camel_runtime_chaos_remove("shop", "r2");
        assertThat(removed.getString("id")).isEqualTo("r2");
        assertThat(removed.getInteger("removed")).isEqualTo(1);
        assertThat(rules(awaitList(l -> rules(l).size() == 1)))
                .containsExactly("r1 v2 endpoint=direct:target delay duration=1ms");

        assertThat(tools.camel_runtime_chaos_clear("shop", null).getInteger("removed")).isEqualTo(1);
        assertThat(rules(awaitList(l -> rules(l).isEmpty()))).isEmpty();

        // the report keeps every version of every rule
        JsonObject[] report = new JsonObject[1];
        awaitThat("the report with the three rules", () -> {
            report[0] = tools.camel_runtime_chaos_report("shop");
            return report[0].getMap("report").get("rules") instanceof List<?> l && l.size() == 3;
        });
        assertThat(report[0].getBoolean("enabled")).isTrue();
        assertThat(report[0].<Map<String, Object>> getMap("report")).containsKey("verdict");
        assertThat(((List<?>) report[0].getMap("report").get("rules")).stream().map(Map.class::cast)
                .map(r -> r.get("id") + " v" + r.get("version") + " " + r.get("state")))
                .containsExactly("r1 v2 removed", "r1 v1 replaced", "r2 v1 removed");

        // reset=true also starts a new report
        JsonObject reset = tools.camel_runtime_chaos_clear("shop", true);
        assertThat(reset.getInteger("removed")).isZero();
        assertThat(reset.getBoolean("reset")).isTrue();
        awaitThat("the report without rules", () -> tools.camel_runtime_chaos_report("shop").getMap("report")
                .get("rules") instanceof List<?> l && l.isEmpty());
    }

    @Test
    void aRuleThatIsNotValidIsRefusedWithTheReasonOfTheParser() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        String rule = "endpoint=direct:target explode rate=2";
        // what the parser of camel.chaos.rules says about it
        String reason = parserReason(rule);
        assertThat(reason).isNotBlank();

        assertThatThrownBy(() -> tools.camel_runtime_chaos_add("shop", rule))
                .isInstanceOf(ToolCallException.class)
                .hasMessage(reason);
        tools.camel_runtime_chaos_add("shop", RULE_ERROR);
        assertThatThrownBy(() -> tools.camel_runtime_chaos_change("shop", "r1", rule))
                .isInstanceOf(ToolCallException.class)
                .hasMessage(reason);
        // nothing changed
        assertThat(rules(awaitList(l -> !rules(l).isEmpty()))).containsExactly("r1 v1 " + RULE_ERROR);
    }

    private String parserReason(String rule) {
        try {
            ChaosManager.get(context).addRule(rule);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Test
    void anIdThatDoesNotExistIsSaid() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        assertThatThrownBy(() -> tools.camel_runtime_chaos_change("shop", "r9", RULE_ERROR))
                .isInstanceOf(ToolCallException.class)
                .hasMessage("There is no chaos rule r9");
        assertThatThrownBy(() -> tools.camel_runtime_chaos_remove("shop", "r9"))
                .isInstanceOf(ToolCallException.class)
                .hasMessage("There is no chaos rule r9");
    }

    @Test
    void withChaosOffEveryChangeIsRefusedAndTheListSaysWhy() throws Exception {
        // camel-chaos is on the classpath, but without profile dev or camel.chaos.enabled=true chaos is off
        start(null, Map.of());

        JsonObject list = tools.camel_runtime_chaos_list("shop");
        assertThat(list.getBoolean("enabled")).isFalse();
        assertThat(list.getString("reason")).startsWith("Chaos is off");
        assertThat(list).doesNotContainKey("rules");
        assertThat(tools.camel_runtime_chaos_report("shop").getString("reason")).startsWith("Chaos is off");

        for (Runnable change : List.<Runnable> of(
                () -> tools.camel_runtime_chaos_add("shop", RULE_ERROR),
                () -> tools.camel_runtime_chaos_change("shop", "r1", RULE_ERROR),
                () -> tools.camel_runtime_chaos_remove("shop", "r1"),
                () -> tools.camel_runtime_chaos_clear("shop", null))) {
            assertThatThrownBy(change::run)
                    .isInstanceOf(ToolCallException.class)
                    .hasMessageStartingWith("Chaos is off in this integration");
        }
        assertThat(send()).isNull();
    }

    @Test
    void profileProdKeepsChaosOff() throws Exception {
        start("prod", Map.of("camel.chaos.enabled", "true"));

        assertThat(tools.camel_runtime_chaos_list("shop").getString("reason"))
                .isEqualTo("Chaos is off: it stays off with profile prod");
        assertThatThrownBy(() -> tools.camel_runtime_chaos_add("shop", RULE_ERROR))
                .isInstanceOf(ToolCallException.class)
                .hasMessageStartingWith("Chaos is off in this integration");
    }

    @Test
    void anArgumentThatIsMissingIsSaidBeforeAnIntegrationIsLookedUp() {
        assertThatThrownBy(() -> tools.camel_runtime_chaos_add("no-such-app", " "))
                .isInstanceOf(ToolCallException.class).hasMessage("rule is required");
        assertThatThrownBy(() -> tools.camel_runtime_chaos_change("no-such-app", null, RULE_ERROR))
                .isInstanceOf(ToolCallException.class).hasMessage("id is required");
        assertThatThrownBy(() -> tools.camel_runtime_chaos_change("no-such-app", "r1", ""))
                .isInstanceOf(ToolCallException.class).hasMessage("rule is required");
        assertThatThrownBy(() -> tools.camel_runtime_chaos_remove("no-such-app", null))
                .isInstanceOf(ToolCallException.class).hasMessage("id is required");
    }

    @Test
    void anIntegrationThatIsNotFoundOrNotTheOnlyOneIsRefused() throws Exception {
        // two integrations, as the real lookup finds them: a status file of a live process each
        ProcessHandle parent = ProcessHandle.current().parent().orElse(null);
        ProcessHandle grandparent = parent != null ? parent.parent().orElse(null) : null;
        assumeThat(grandparent).as("two live processes to stand for two integrations").isNotNull();
        Path dir = CommandLineHelper.getCamelDir();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(parent.pid() + "-status.json"),
                "{\"context\":{\"name\":\"orders-a\",\"state\":\"Started\"}}");
        Files.writeString(dir.resolve(grandparent.pid() + "-status.json"),
                "{\"context\":{\"name\":\"orders-b\",\"state\":\"Started\"}}");
        assumeThat(new RuntimeService().discoverProcesses()).as("the two integrations are found").hasSize(2);

        // rule ids are per integration: a change must reach exactly one
        assertThatThrownBy(() -> tools.camel_runtime_chaos_add("orders", RULE_ERROR))
                .isInstanceOf(ToolCallException.class)
                .hasMessageStartingWith("No unique Camel process found matching 'orders'");
        // without a name, neither has chaos switched on: the agent gets the list to name one
        assertThatThrownBy(() -> tools.camel_runtime_chaos_clear(null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageStartingWith("2 Camel integrations are running and none has chaos switched on: ")
                // named as camel ps names them (here from the command line of the process), with their pid
                .hasMessageContaining("pid " + parent.pid())
                .hasMessageContaining("pid " + grandparent.pid())
                .hasMessageContaining("chaos off)")
                .hasMessageEndingWith(". Give the name or pid of one.");
        assertThatThrownBy(() -> tools.camel_runtime_chaos_list("billing"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageStartingWith("No unique Camel process found matching 'billing'");

        // the only one with chaos switched on is the one without a name
        Files.writeString(dir.resolve(grandparent.pid() + "-status.json"),
                "{\"context\":{\"name\":\"orders-b\",\"state\":\"Started\"},\"chaos\":{\"enabled\":true,\"rules\":[]}}");
        JsonObject list = tools.camel_runtime_chaos_list(null);
        assertThat(list.getBoolean("enabled")).isTrue();
        assertThat(list.<List<Object>> getCollection("rules")).isEmpty();
    }

    @Test
    void withoutANameThisJvmIsNeverTheIntegration() throws Exception {
        // the integration of the test runs in this JVM, which the lookup without a name never takes
        start(null, Map.of("camel.chaos.enabled", "true"));

        assertThatThrownBy(() -> tools.camel_runtime_chaos_add(null, RULE_ERROR))
                .isInstanceOf(ToolCallException.class)
                .hasMessageStartingWith("No running Camel integrations");
    }
}
