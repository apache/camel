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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd chaos-add, chaos-change, chaos-remove, chaos-clear and chaos-suggest: the chaos action they send, what they
 * print, and that a refused rule is an error with the reason of the integration.
 */
@ExtendWith(MockitoExtension.class)
class CamelChaosActionTest extends ActionCommandTestSupport {

    @Test
    void add() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        String rule = "endpoint=kafka:orders* delay duration=2s rate=0.2 until=5m";

        assertThat(callWithResponse(command(new CamelChaosAddAction(main()), "orders", "--rule=" + rule),
                new JsonObject(Map.of("id", "r1", "version", 1)))).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("chaos");
        assertThat(action.getString("command")).isEqualTo("add");
        // the rule as it was given, quotes and all: the integration parses it
        assertThat(action.getString("rule")).isEqualTo(rule);
        assertThat(action).doesNotContainKey("id");
        assertThat(printer.getLines()).containsExactly("Added chaos rule r1");
    }

    @Test
    void addKeepsQuotedValues() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        String rule = "endpoint=kafka:orders* error message=\"broker down; retry\" rate=0.3";

        assertThat(callWithResponse(command(new CamelChaosAddAction(main()), "orders", "--rule", rule),
                new JsonObject(Map.of("id", "r2", "version", 1)))).isZero();

        assertThat(readActionFile(TEST_PID).getString("rule")).isEqualTo(rule);
    }

    @Test
    void aRefusedRuleIsAnErrorWithTheReason() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject(
                Map.of("status", "failed", "error",
                        "Invalid chaos rule [endpoint=kafka:orders* error rate=2]: rate=2 is not a probability above 0 and at most 1"));

        assertThatThrownBy(() -> callWithResponse(
                command(new CamelChaosAddAction(main()), "orders", "--rule=endpoint=kafka:orders* error rate=2"), answer))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("rate=2 is not a probability above 0 and at most 1")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.FAILURE);
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void chaosOffIsAnError() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject(
                Map.of("status", "failed", "error",
                        "Chaos is off in this integration: it is switched on with profile dev (unless camel.chaos.enabled=false)"
                                                    + " or with camel.chaos.enabled=true, and never with profile prod"));

        assertThatThrownBy(() -> callWithResponse(
                command(new CamelChaosAddAction(main()), "orders", "--rule=endpoint=* drop"), answer))
                .isInstanceOf(CliException.class)
                .hasMessageStartingWith("Chaos is off in this integration");
    }

    @Test
    void suggestPrintsWhatToTryWithTheCommandThatAddsIt() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject suggestion = new JsonObject(
                Map.of("route", "orders", "kind", "errors", "recipe", "http-error",
                        "title", "Returns an error (HTTP status)",
                        "rule", "endpoint=http://stock/check error status=503 rate=0.25 until=10m",
                        "why", "an HTTP call to http://stock/check without a circuit breaker: what does the route do when the"
                               + " service answers 503?"));
        JsonObject answer = new JsonObject(Map.of("suggestions", new JsonArray(List.of(suggestion))));

        assertThat(callWithResponse(command(new CamelChaosSuggestAction(main()), "orders"), answer)).isZero();

        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("suggest");
        assertThat(printer.getLines()).contains("Chaos rules to try, the most worth trying first:",
                "1. route orders: Returns an error (HTTP status) (errors)",
                "camel cmd chaos-add --rule='endpoint=http://stock/check error status=503 rate=0.25 until=10m'");
    }

    @Test
    void suggestSaysWhenThereIsNothingToTry() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        assertThat(callWithResponse(command(new CamelChaosSuggestAction(main()), "orders"),
                new JsonObject(Map.of("suggestions", new JsonArray())))).isZero();
        assertThat(printer.getOutput()).startsWith("Nothing to suggest");
    }

    @Test
    void addAndChangeNeedTheirOptions() {
        assertThatThrownBy(() -> command(new CamelChaosAddAction(main()), "orders"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosAddAction(main())))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosChangeAction(main()), "orders", "--rule=endpoint=* drop"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosChangeAction(main()), "--id=r1"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
    }

    @Test
    void addPrintsTheRuleInPlainWords() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject(
                Map.of("id", "r1", "ids", new JsonArray(List.of("r1")), "version", 1,
                        "description", "fail half of the sends to http:stock* with HTTP status 503, for 5 minutes"));

        assertThat(callWithResponse(command(new CamelChaosAddAction(main()), "orders",
                "--rule=endpoint=http:stock* error status=503 rate=0.5 until=5m"), answer)).isZero();

        assertThat(printer.getLines()).containsExactly(
                "Added chaos rule r1: fail half of the sends to http:stock* with HTTP status 503, for 5 minutes");
    }

    @Test
    void dryRunChecksTheRuleAndPrintsWhatItWouldHit() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject hits = new JsonObject(
                Map.of(
                        "sends", new JsonArray(
                                List.of(new JsonObject(
                                        Map.of("route", "orders", "endpoint", "http://stock", "step", "callStock")))),
                        "dynamicSends", new JsonArray(
                                List.of(new JsonObject(
                                        Map.of("route", "orders", "uri", "http:stock/${header.id}"))))));
        JsonObject rule = new JsonObject(
                Map.of("rule", "endpoint=http:stock* error status=503",
                        "description", "fail every send to http:stock* with HTTP status 503", "hits", hits));
        JsonObject nothing = new JsonObject(
                Map.of("rule", "step=tpyo error",
                        "description", "fail every run of step tpyo", "hits", new JsonObject(),
                        "warnings", new JsonArray(List.of("step=tpyo matches no step of the context now"))));
        JsonObject answer = new JsonObject(Map.of("rules", new JsonArray(List.of(rule, nothing))));

        assertThat(callWithResponse(command(new CamelChaosAddAction(main()), "orders", "--dry-run",
                "--rule=endpoint=http:stock* error status=503", "--rule=step=tpyo error"), answer)).isZero();

        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("check");
        assertThat(printer.getStdout()).isEqualTo("""
                Would add: endpoint=http:stock* error status=503
                  fail every send to http:stock* with HTTP status 503
                  send http://stock in route orders (step callStock)
                  send (known when it sends) http:stock/${header.id} in route orders
                Would add: step=tpyo error
                  fail every run of step tpyo
                  hits nothing in the integration now
                  Warning: step=tpyo matches no step of the context now
                (dry run: nothing was added)""");
    }

    @Test
    void addOfSeveralRulesPrintsEachInPlainWords() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject(
                Map.of("ids", new JsonArray(List.of("r1", "r2")), "version", 1,
                        "descriptions", new JsonArray(
                                List.of("fail every dead letter send to seda:dlq",
                                        "fail every send to http:stock*"))));

        assertThat(callWithResponse(command(new CamelChaosAddAction(main()), "orders",
                "--rule=endpoint=seda:dlq error at=dlq", "--rule=endpoint=http:stock* error"), answer)).isZero();

        assertThat(printer.getStdout()).startsWith("Added chaos rules r1, r2\n"
                                                   + "  r1: fail every dead letter send to seda:dlq\n"
                                                   + "  r2: fail every send to http:stock*");
    }

    @Test
    void withoutANameTheOnlyIntegrationRunningIsUsed() throws Exception {
        // chaos is off by its status, but it is the only one: it answers for itself
        writeStatusFile(TEST_PID, "orders");

        assertThat(callWithResponse(command(new CamelChaosAddAction(main()), "--rule=endpoint=kafka:orders* drop"),
                new JsonObject(Map.of("id", "r1", "version", 1)))).isZero();

        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("add");
        assertThat(printer.getLines()).containsExactly("Added chaos rule r1");
    }

    @Test
    void withoutANameTheOnlyIntegrationWithChaosOnIsUsed() throws Exception {
        writeChaosStatus(TEST_PID, "orders", true);
        writeChaosStatus(2001, "billing", false);
        // an integration without camel-chaos
        writeStatusFile(2002, "shop");

        assertThat(callWithResponse(command(new CamelChaosChangeAction(main()), "--id=r1",
                "--rule=endpoint=kafka:orders* delay duration=5s"),
                new JsonObject(
                        Map.of("id", "r1", "version", 2, "description",
                                "delay every send to kafka:orders* by 5 seconds")),
                2001, 2002)).isZero();
        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("change");
        assertThat(printer.getLines())
                .containsExactly("Changed chaos rule r1 (version 2): delay every send to kafka:orders* by 5 seconds");

        assertThat(callWithResponse(command(new CamelChaosRemoveAction(main()), "--id=r1"),
                new JsonObject(Map.of("id", "r1", "removed", 1)), 2001, 2002)).isZero();
        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("remove");
    }

    @Test
    void withoutANameSeveralWithChaosOnIsAUsageErrorWithTheList() throws Exception {
        writeChaosStatus(TEST_PID, "orders", true);
        writeChaosStatus(2001, "billing", true);
        writeChaosStatus(2002, "shop", false);

        assertThatThrownBy(() -> callWithResponse(command(new CamelChaosAddAction(main()), "--rule=endpoint=* drop"),
                new JsonObject(Map.of("id", "r1")), 2001, 2002))
                .isInstanceOf(CliException.class)
                .hasMessage("3 Camel integrations are running and 2 have chaos switched on: billing (pid 2001, chaos on),"
                            + " shop (pid 2002, chaos off), orders (pid 12345, chaos on). Give the name or pid of one.")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
        // add never means all of them
        assertThat(readActionFile(TEST_PID)).isNull();
    }

    @Test
    void withoutANameNoneWithChaosOnAmongSeveralIsAUsageError() throws Exception {
        writeChaosStatus(TEST_PID, "orders", false);
        writeStatusFile(2001, "billing");

        assertThatThrownBy(() -> callWithResponse(command(new CamelChaosRemoveAction(main()), "--id=r1"),
                new JsonObject(Map.of("id", "r1")), 2001))
                .isInstanceOf(CliException.class)
                .hasMessageStartingWith("2 Camel integrations are running and none has chaos switched on: ")
                .hasMessageContaining("orders (pid 12345, chaos off)")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
        assertThat(readActionFile(TEST_PID)).isNull();
    }

    @Test
    void withoutANameAndNoIntegrationIsAUsageError() {
        // no status file: nothing runs
        assertThatThrownBy(() -> callWithResponse(command(new CamelChaosAddAction(main()), "--rule=endpoint=* drop"),
                new JsonObject(Map.of("id", "r1"))))
                .isInstanceOf(CliException.class)
                .hasMessageStartingWith("No running Camel integrations")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    @Test
    void addMatchesExactlyOneIntegration() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        assertThatThrownBy(() -> callWithResponse(
                command(new CamelChaosAddAction(main()), "billing", "--rule=endpoint=* drop"),
                new JsonObject(Map.of("id", "r1"))))
                .isInstanceOf(CliException.class)
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(readActionFile(TEST_PID)).isNull();
    }

    @Test
    void change() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        assertThat(callWithResponse(command(new CamelChaosChangeAction(main()), "orders", "--id=r1",
                "--rule=endpoint=kafka:orders* delay duration=5s"), new JsonObject(Map.of("id", "r1", "version", 2))))
                .isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("command")).isEqualTo("change");
        assertThat(action.getString("id")).isEqualTo("r1");
        assertThat(action.getString("rule")).isEqualTo("endpoint=kafka:orders* delay duration=5s");
        assertThat(printer.getLines()).containsExactly("Changed chaos rule r1 (version 2)");
    }

    @Test
    void remove() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        assertThat(callWithResponse(command(new CamelChaosRemoveAction(main()), "orders", "--id=r1"),
                new JsonObject(Map.of("id", "r1", "removed", 1)))).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("command")).isEqualTo("remove");
        assertThat(action.getString("id")).isEqualTo("r1");
        assertThat(printer.getLines()).containsExactly("Removed chaos rule r1");
    }

    @Test
    void withoutANameIntegrationsWithChaosOffAreNotAsked() throws Exception {
        writeChaosStatus(false);

        assertThat(callWithResponse(command(new CamelChaosClearAction(main())), new JsonObject(Map.of("removed", 0))))
                .isZero();

        assertThat(readActionFile(TEST_PID)).isNull();
        assertThat(printer.getStderr()).contains("No running Camel integration has chaos switched on");
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void withANameAnIntegrationWithChaosOffAnswersForItself() throws Exception {
        writeChaosStatus(false);
        JsonObject answer = new JsonObject(
                Map.of("status", "failed", "error",
                        "Chaos is off in this integration: it is switched on with profile dev (unless camel.chaos.enabled=false)"
                                                    + " or with camel.chaos.enabled=true, and never with profile prod"));

        assertThat(callWithResponse(command(new CamelChaosClearAction(main()), "orders"), answer))
                .isEqualTo(CliExitCode.FAILURE);

        assertThat(readActionFile(TEST_PID).getString("command")).isEqualTo("clear");
        assertThat(printer.getStderr()).contains("Chaos is off in this integration");
    }

    @Test
    void removeAnIdThatDoesNotExist() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject answer = new JsonObject(
                Map.of("status", "failed", "reason", "not-found", "error", "There is no chaos rule r9"));

        assertThatThrownBy(() -> callWithResponse(command(new CamelChaosRemoveAction(main()), "orders", "--id=r9"),
                answer))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("There is no chaos rule r9")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void removeNeedsTheId() {
        assertThatThrownBy(() -> command(new CamelChaosRemoveAction(main()), "orders"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosRemoveAction(main())))
                .isInstanceOf(CommandLine.MissingParameterException.class);
    }

    @Test
    void clear() throws Exception {
        writeChaosStatus(true, "r1", "r2", "r3");

        assertThat(callWithResponse(command(new CamelChaosClearAction(main())),
                new JsonObject(Map.of("removed", 3)))).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("command")).isEqualTo("clear");
        assertThat(action).doesNotContainKeys("id", "rule");
        assertThat(printer.getLines()).containsExactly("Removed chaos rules: 3");
    }

    /** The status of the integration orders, with the chaos dev console as the CLI connector puts it there. */
    private static void writeChaosStatus(boolean enabled, String... ids) throws Exception {
        JsonObject chaos = new JsonObject();
        chaos.put("enabled", enabled);
        if (enabled) {
            JsonArray rules = new JsonArray();
            for (String id : ids) {
                rules.add(new JsonObject(Map.of("id", id, "version", 1, "state", "active")));
            }
            chaos.put("rules", rules);
        } else {
            chaos.put("reason", "Chaos is off: it stays off with profile prod");
        }
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "orders")));
        root.put("chaos", chaos);
        writeStatusFile(TEST_PID, root);
    }

    /** The status of an integration, with chaos on or off as the chaos dev console puts it there. */
    private static void writeChaosStatus(long pid, String name, boolean enabled) throws Exception {
        JsonObject chaos = new JsonObject();
        chaos.put("enabled", enabled);
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", name)));
        root.put("chaos", chaos);
        writeStatusFile(pid, root);
    }

    private CamelJBangMain main() {
        return new CamelJBangMain().withPrinter(printer);
    }

    private static <T extends CamelChaosAction> T command(T command, String... args) {
        CommandLine.populateCommand(command, args);
        return command;
    }
}
