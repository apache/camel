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
 * camel cmd chaos-add, chaos-change, chaos-remove and chaos-clear: the chaos action they send, what they print, and
 * that a refused rule is an error with the reason of the integration.
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
    void addAndChangeNeedTheIntegration() {
        // the id of a rule is only known in one integration
        assertThatThrownBy(() -> command(new CamelChaosAddAction(main()), "--rule=endpoint=* drop"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosChangeAction(main()), "--id=r1", "--rule=endpoint=* drop"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosAddAction(main()), "orders"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosChangeAction(main()), "orders", "--rule=endpoint=* drop"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
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
    void removeNeedsTheIntegrationAndTheId() {
        // ids are per integration
        assertThatThrownBy(() -> command(new CamelChaosRemoveAction(main()), "--id=r1"))
                .isInstanceOf(CommandLine.MissingParameterException.class);
        assertThatThrownBy(() -> command(new CamelChaosRemoveAction(main()), "orders"))
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

    private CamelJBangMain main() {
        return new CamelJBangMain().withPrinter(printer);
    }

    private static <T extends CamelChaosAction> T command(T command, String... args) {
        CommandLine.populateCommand(command, args);
        return command;
    }
}
