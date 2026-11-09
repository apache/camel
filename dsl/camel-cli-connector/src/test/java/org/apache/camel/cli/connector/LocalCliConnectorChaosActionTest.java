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
package org.apache.camel.cli.connector;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chaos.ChaosManager;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The chaos action of the CLI connector, as camel cmd chaos-add, chaos-change, chaos-remove and chaos-clear send it: it
 * adds, changes and removes the rules of camel-chaos only when chaos is switched on, never with profile prod, with the
 * reason of a rule that is not valid; and the status has the chaos dev console, for camel get chaos.
 * <p>
 * Isolated because one test changes {@code user.home}, and every connector in this JVM writes files named after the
 * same pid.
 */
@Isolated
class LocalCliConnectorChaosActionTest {

    private CamelContext context;
    private LocalCliConnector connector;

    @AfterEach
    void stop() {
        if (connector != null) {
            connector.stop();
        }
        if (context != null) {
            context.stop();
        }
    }

    private void start(String profile, Map<String, String> properties) throws Exception {
        context = new DefaultCamelContext();
        // the actions are dispatched here: a connector of the context would poll the {pid} files of other tests
        DefaultCliConnectorFactory disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        context.getCamelContextExtension().setProfile(profile);
        properties.forEach(context.getPropertiesComponent()::addInitialProperty);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target");
            }
        });
        context.start();
        connector = new LocalCliConnector(new DefaultCliConnectorFactory());
        connector.setCamelContext(context);
    }

    /** Dispatches the action as both transports do, and returns its outcome. */
    private CliActionOutcome chaos(String command, String id, String rule) throws Exception {
        return chaos(command, id, rule, false);
    }

    private CliActionOutcome chaos(String command, String id, String rule, boolean reset) throws Exception {
        JsonObject action = new JsonObject();
        action.put("action", "chaos");
        action.put("command", command);
        if (reset) {
            action.put("reset", true);
        }
        if (id != null) {
            action.put("id", id);
        }
        if (rule != null) {
            action.put("rule", rule);
        }
        // as the action file carries it: JSON text
        return CliActionOutcome.dispatch(connector, (JsonObject) Jsoner.deserialize(action.toJson()))
                .get(10, TimeUnit.SECONDS);
    }

    private JsonArray reportRules() {
        return (JsonArray) ChaosManager.get(context).report().toJson().get("rules");
    }

    @Test
    void addChangeRemoveAndClear() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        CliActionOutcome added = chaos("add", null, "endpoint=mock:target error rate=1");
        assertThat(added.error()).isNull();
        assertThat(added.result().getString("id")).isEqualTo("r1");
        assertThat(added.result().getInteger("version")).isEqualTo(1);
        // in plain words, for the tool to show
        assertThat(added.result().getString("description")).isEqualTo("fail every send to mock:target");
        assertThat(context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"))
                .getException()).isNotNull();

        CliActionOutcome changed = chaos("change", "r1", "endpoint=mock:target drop");
        assertThat(changed.error()).isNull();
        assertThat(changed.result().getString("id")).isEqualTo("r1");
        assertThat(changed.result().getInteger("version")).isEqualTo(2);
        assertThat(changed.result().getString("description")).isEqualTo("drop every send to mock:target");
        assertThat(context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"))
                .getException()).isNull();

        assertThat(chaos("add", null, "step=* delay duration=1ms").result().getString("id")).isEqualTo("r2");
        CliActionOutcome removed = chaos("remove", "r2", null);
        assertThat(removed.error()).isNull();
        assertThat(removed.result().getString("id")).isEqualTo("r2");

        CliActionOutcome cleared = chaos("clear", null, null);
        assertThat(cleared.error()).isNull();
        assertThat(cleared.result().getInteger("removed")).isEqualTo(1);
        assertThat(reportRules().stream().map(JsonObject.class::cast)
                .map(r -> r.getString("id") + " v" + r.getInteger("version") + " " + r.getString("state")))
                .containsExactly("r1 v2 removed", "r1 v1 replaced", "r2 v1 removed");

        // a reset also starts a new report
        assertThat(chaos("add", null, "step=* delay duration=1ms").result().getString("id")).isEqualTo("r3");
        CliActionOutcome reset = chaos("clear", null, null, true);
        assertThat(reset.error()).isNull();
        assertThat(reset.result().getInteger("removed")).isEqualTo(1);
        assertThat(reset.result().getBoolean("reset")).isTrue();
        assertThat(reportRules()).isEmpty();
    }

    @Test
    void checkSaysWhatARuleWouldHitWithoutAddingIt() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        CliActionOutcome checked = chaos("check", null, "endpoint=mock:target error rate=0.5");
        assertThat(checked.error()).isNull();
        JsonObject rule = (JsonObject) checked.result().getCollection("rules").iterator().next();
        assertThat(rule.getString("description")).isEqualTo("fail half of the sends to mock:target");
        JsonObject send = (JsonObject) ((JsonObject) rule.get("hits")).getCollection("sends").iterator().next();
        assertThat(send.getString("route")).isEqualTo("start");
        assertThat(send.getString("endpoint")).isEqualTo("mock://target");
        // nothing was added, and the messages go through
        assertThat(reportRules()).isEmpty();
        assertThat(context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"))
                .getException()).isNull();

        // refused with the reason, as add would refuse it
        assertThat(chaos("check", null, "endpoint=mock:target error rate=2").error())
                .contains("rate=2 is not a probability above 0 and at most 1");
        assertThat(chaos("check", null, " ").error()).isEqualTo("A rule is required");
    }

    private CliActionOutcome load(String name, String text) throws Exception {
        JsonObject action = new JsonObject();
        action.put("action", "chaos");
        action.put("command", "load");
        action.put("name", name);
        action.put("text", text);
        return CliActionOutcome.dispatch(connector, (JsonObject) Jsoner.deserialize(action.toJson()))
                .get(10, TimeUnit.SECONDS);
    }

    @Test
    void loadAScenarioInPlaceOfTheRules() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        chaos("add", null, "step=* delay duration=1ms");

        CliActionOutcome loaded
                = load("outage.chaos", "# down\nseed=1\nendpoint=mock:target error\nendpoint=mock:nowhere drop\n");
        assertThat(loaded.error()).isNull();
        assertThat((JsonArray) loaded.result().get("ids")).containsExactly("r2", "r3");
        assertThat(loaded.result().getBoolean("reset")).isTrue();
        assertThat((JsonArray) loaded.result().get("descriptions"))
                .containsExactly("fail every send to mock:target", "drop every send and poll of mock:nowhere");
        assertThat((JsonArray) loaded.result().get("warnings")).singleElement().asString().startsWith("r3: ");
        // the rule there was is gone with its report
        assertThat(reportRules()).hasSize(2);

        // a line that is not valid, with its number: the rules stay
        assertThat(load("bad.chaos", "endpoint=mock:target bang").error())
                .startsWith("Chaos scenario bad.chaos, line 1: Invalid chaos rule");
        assertThat(load("empty.chaos", "# nothing\n").error()).isEqualTo("The chaos scenario empty.chaos has no rules");
        assertThat(load("none.chaos", " ").error()).isEqualTo("The scenario is required");
        assertThat(reportRules()).hasSize(2);
    }

    @Test
    void suggestWhatToTry() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        CliActionOutcome suggested = chaos("suggest", null, null);
        assertThat(suggested.error()).isNull();
        // the route of the test only calls local endpoints: nothing to suggest, and nothing was added
        assertThat((JsonArray) suggested.result().get("suggestions")).isNotNull();
        assertThat(reportRules()).isEmpty();
    }

    @Test
    void anExplorationStartsAndFinishes() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        JsonObject action = new JsonObject();
        action.put("action", "chaos");
        action.put("command", "explore-start");
        action.put("budget", "5m");
        action.put("focus", "start");

        CliActionOutcome started = CliActionOutcome.dispatch(connector, action).get(10, TimeUnit.SECONDS);
        assertThat(started.error()).isNull();
        assertThat(started.result().getBoolean("running")).isTrue();
        assertThat(started.result().getLong("budget")).isEqualTo(300_000L);
        assertThat(started.result().getString("focus")).isEqualTo("start");
        // one at a time: the reason goes to the tool
        assertThat(CliActionOutcome.dispatch(connector, action).get(10, TimeUnit.SECONDS).error())
                .startsWith("An exploration is running");

        assertThat(chaos("add", null, "endpoint=mock:target error").error()).isNull();
        CliActionOutcome finished = chaos("explore-finish", null, null);
        assertThat(finished.error()).isNull();
        assertThat(finished.result().getString("finishedBy")).isEqualTo("finished");
        assertThat(finished.result().getLong("removed")).isEqualTo(1L);
        assertThat(chaos("explore-finish", null, null).error()).isEqualTo("No exploration is running");
    }

    @Test
    void rulesAreAddedTogetherWithWarningsForTheOnesThatMatchNothing() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        CliActionOutcome added = chaos("add", null, "endpoint=mock:target error at=dlq; step=tpyo delay duration=1ms");
        assertThat(added.error()).isNull();
        assertThat(added.result().<List<Object>> getCollection("ids")).containsExactly("r1", "r2");
        // the id of a single rule only
        assertThat(added.result().containsKey("id")).isFalse();
        assertThat(added.result().containsKey("description")).isFalse();
        assertThat(added.result().<List<Object>> getCollection("descriptions")).containsExactly(
                "fail every dead letter send to mock:target", "delay every run of step tpyo by 1 millisecond");
        assertThat(added.result().<List<Object>> getCollection("warnings")).singleElement().asString()
                .startsWith("r2: step=tpyo matches no step of the context now");

        CliActionOutcome one = chaos("add", null, "endpoint=mock:target delay duration=1ms");
        assertThat(one.result().getString("id")).isEqualTo("r3");
        assertThat(one.result().containsKey("warnings")).isFalse();

        CliActionOutcome changed = chaos("change", "r3", "endpoint=mock:taget delay duration=1ms");
        assertThat(changed.result().<List<Object>> getCollection("warnings")).singleElement().asString()
                .startsWith("r3: endpoint=mock:taget matches no endpoint of the context now");
    }

    @Test
    void aRuleThatIsNotValidIsRefusedWithTheReason() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        CliActionOutcome outcome = chaos("add", null, "endpoint=mock:target error rate=2");

        // the same strict parser as camel.chaos.rules; the reason is reported to the tool, not thrown (and logged)
        assertThat(outcome.error())
                .isEqualTo(
                        "Invalid chaos rule [endpoint=mock:target error rate=2]: rate=2 is not a probability above 0 and at most 1");
        assertThat(outcome.failure()).isNull();
        assertThat(outcome.errorReply().getString("status")).isEqualTo("failed");
        assertThat(reportRules()).isEmpty();
        // a class that is not a Throwable
        assertThat(chaos("add", null, "endpoint=mock:target error exception=java.lang.String").error())
                .contains("java.lang.String");
        assertThat(reportRules()).isEmpty();
    }

    @Test
    void anIdThatDoesNotExist() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        CliActionOutcome removed = chaos("remove", "r9", null);
        assertThat(removed.error()).isEqualTo("There is no chaos rule r9");
        assertThat(removed.reason()).isEqualTo(CliActionOutcome.NOT_FOUND);

        CliActionOutcome changed = chaos("change", "r9", "endpoint=mock:target drop");
        assertThat(changed.error()).isEqualTo("There is no chaos rule r9");
        assertThat(changed.reason()).isEqualTo(CliActionOutcome.NOT_FOUND);
        assertThat(reportRules()).isEmpty();
    }

    @Test
    void aRuleRemovedWhileItIsChangedIsNotFound() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        // the rule is there when change checks it, and gone (chaos-remove, chaos-clear) when it is changed
        RacingChaosManager racing = new RacingChaosManager("There is no chaos rule r1", true, false);
        useManager(racing);

        CliActionOutcome changed = chaos("change", "r1", "endpoint=mock:target drop");

        assertThat(changed.error()).isEqualTo("There is no chaos rule r1");
        assertThat(changed.reason()).isEqualTo(CliActionOutcome.NOT_FOUND);
        assertThat(racing.changes).isEqualTo(1);
    }

    @Test
    void aChangedRuleThatIsNotValidIsStillAnError() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        // the rule is still there: the reason of the manager goes to the tool
        RacingChaosManager racing = new RacingChaosManager("Invalid chaos rule [drop]: no hook", true, true);
        useManager(racing);

        CliActionOutcome changed = chaos("change", "r1", "drop");

        assertThat(changed.error()).isEqualTo("Invalid chaos rule [drop]: no hook");
        assertThat(changed.reason()).isNull();
        assertThat(racing.changes).isEqualTo(1);
    }

    /** The action finds the given manager instead of the ChaosManager of camel-chaos. */
    private void useManager(RacingChaosManager manager) {
        context.getCamelContextExtension().addContextPlugin(RacingChaosManager.class, manager);
        context.setClassResolver(new DefaultClassResolver(context) {
            @Override
            public Class<?> resolveClass(String name) {
                return ChaosAction.CHAOS_MANAGER.equals(name) ? RacingChaosManager.class : super.resolveClass(name);
            }
        });
    }

    /**
     * A manager whose hasRule answers in turn the given values, and whose changeRule throws the
     * IllegalArgumentException that ChaosManager throws for a rule that does not exist or is not valid.
     */
    public static final class RacingChaosManager {

        private final String failure;
        private final Deque<Boolean> hasRule;
        int changes;

        RacingChaosManager(String failure, Boolean... hasRule) {
            this.failure = failure;
            this.hasRule = new ArrayDeque<>(List.of(hasRule));
        }

        public boolean hasRule(String id) {
            return hasRule.size() > 1 ? hasRule.poll() : hasRule.peek();
        }

        public int changeRule(String id, String rule) {
            changes++;
            throw new IllegalArgumentException(failure);
        }
    }

    @Test
    void missingArgumentsAndUnknownCommand() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));

        assertThat(chaos("add", null, " ").error()).isEqualTo("A rule is required");
        assertThat(chaos("change", "r1", null).error()).isEqualTo("The id of the rule and the new rule are required");
        assertThat(chaos("remove", null, null).error()).isEqualTo("The id of the rule is required");
        assertThat(chaos("enable", null, null).error()).startsWith("Unknown chaos command: enable");
        assertThat(reportRules()).isEmpty();
    }

    @Test
    void refusedWithProfileProd() throws Exception {
        // camel-chaos is on the classpath and camel.chaos.enabled=true, but profile prod keeps it off
        start("prod", Map.of("camel.chaos.enabled", "true"));

        CliActionOutcome outcome = chaos("add", null, "endpoint=mock:target error");

        assertThat(outcome.error()).startsWith("Chaos is off in this integration");
        assertThat(outcome.result()).isNull();
        // the action does not switch chaos on
        assertThat(ChaosManager.get(context)).isNull();
        assertThat(context.getCamelContextExtension().getSendHook()).isNull();
        assertThat(context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"))
                .getException()).isNull();
    }

    @Test
    void refusedWhenChaosIsOff() throws Exception {
        start(null, Map.of());

        assertThat(chaos("clear", null, null).error()).startsWith("Chaos is off in this integration");
        assertThat(ChaosManager.get(context)).isNull();
    }

    @Test
    void refusedWithoutCamelChaos() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        // an integration without the camel-chaos JAR: its class resolver does not find the manager
        context.setClassResolver(new DefaultClassResolver(context) {
            @Override
            public Class<?> resolveClass(String name) {
                return ChaosAction.CHAOS_MANAGER.equals(name) ? null : super.resolveClass(name);
            }
        });

        assertThat(chaos("add", null, "endpoint=mock:target error").error())
                .startsWith("camel-chaos is not on the classpath of this integration");
        assertThat(reportRules()).isEmpty();
    }

    @Test
    void theStatusHasTheChaosConsole() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        chaos("add", null, "endpoint=mock:target drop");

        JsonObject chaos = (JsonObject) connector.status().get("chaos");

        assertThat(chaos.getBoolean("enabled")).isTrue();
        JsonArray rules = chaos.getCollection("rules");
        JsonObject rule = (JsonObject) rules.get(0);
        assertThat(rule.getString("id")).isEqualTo("r1");
        assertThat(rule.getString("state")).isEqualTo("active");
        assertThat(((JsonObject) chaos.get("report")).getString("verdict")).isEqualTo("PASS");
    }

    @Test
    void theStatusSaysWhyChaosIsOff() throws Exception {
        start("prod", Map.of());

        JsonObject chaos = (JsonObject) connector.status().get("chaos");

        assertThat(chaos.getBoolean("enabled")).isFalse();
        assertThat(chaos.getString("reason")).contains("profile prod");
    }

    @Test
    void addThroughTheActionFile(@TempDir Path home) throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        // the connector writes to ${user.home}/.camel, keep the test away from the real one
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            long pid = ProcessHandle.current().pid();
            File dir = home.resolve(".camel").toFile();
            File outputFile = new File(dir, pid + "-output.json");
            JsonObject request = new JsonObject();
            request.put("action", "chaos");
            request.put("command", "add");
            request.put("rule", "endpoint=mock:target drop until=1m");
            // written before the connector starts, so its first poll reads the whole file
            IOHelper.writeText(request.toJson(), new File(dir, pid + "-action.json"));
            connector.start();

            // a reply that is still being written does not parse yet: wait for the whole of it
            await().atMost(10, TimeUnit.SECONDS).ignoreExceptions().untilAsserted(() -> {
                assertThat(outputFile).exists();
                String text = IOHelper.loadText(new FileInputStream(outputFile));
                assertThat(text).isNotBlank();
                assertThat(((JsonObject) Jsoner.deserialize(text)).getString("id")).isEqualTo("r1");
            });
            assertThat(reportRules()).hasSize(1);
        } finally {
            connector.stop();
            System.setProperty("user.home", oldHome);
        }
    }
}
