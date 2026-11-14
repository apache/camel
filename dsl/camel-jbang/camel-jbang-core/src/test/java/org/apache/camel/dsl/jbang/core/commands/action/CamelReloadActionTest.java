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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CamelReloadActionTest extends ActionCommandTestSupport {

    @Test
    void testWritesReloadActionForMatchingName() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        int exit = callWithSingleProcess(command("myApp"));

        assertThat(exit).isZero();
        JsonObject action = readActionFile(TEST_PID);
        assertThat(action).as("action file should be written for the matched process").isNotNull();
        assertThat(action.getString("action")).isEqualTo("reload");
        assertThat(action.containsKey("changed")).isFalse();
    }

    @Test
    void testNoActionWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelReloadAction command = command("doesNotExist");

        assertThatThrownBy(() -> callWithSingleProcess(command)).isInstanceOf(CliException.class)
                .hasMessage("No running Camel integration matches: doesNotExist")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(readActionFile(TEST_PID)).as("no action file should be written when no process matches").isNull();
    }

    @Test
    void waitReloadsTheChangedFilesAndPrintsThem() throws Exception {
        // CAMEL-26939: right after an edit, an agent reloads it at once and hears what came of it
        writeStatusFile(TEST_PID, "myApp");
        CamelReloadAction command = command("myApp");
        command.waitForOutcome = true;

        int exit = callWithResponse(command, json("{\"files\":[\"routes/orders.yaml\"]}"));

        assertThat(exit).isEqualTo(CliExitCode.OK);
        assertThat(readActionFile(TEST_PID).getBoolean("changed")).isTrue();
        assertThat(printer.getStdout()).contains("Reloaded myApp:", "routes/orders.yaml");
    }

    @Test
    void waitSaysWhenNothingChanged() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelReloadAction command = command("myApp");
        command.waitForOutcome = true;
        command.jsonOutput = true;

        int exit = callWithResponse(command, json("{\"files\":[]}"));

        assertThat(exit).isEqualTo(CliExitCode.OK);
        JsonObject outcome = json(printer.getStdout());
        assertThat(outcome.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(outcome.getString("name")).isEqualTo("myApp");
        assertThat(outcome.getString("status")).isEqualTo("unchanged");
        JsonArray files = outcome.getCollection("files");
        assertThat(files).isEmpty();
    }

    @Test
    void aFailedReloadIsTheOutcomeWithExitCodeOne() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        JsonObject failed = json("{\"status\":\"failed\",\"error\":\"Unknown step: frm\","
                                 + "\"result\":{\"files\":[\"routes/orders.yaml\"]}}");
        CamelReloadAction command = command("myApp");
        command.waitForOutcome = true;
        command.jsonOutput = true;

        int exit = callWithResponse(command, failed);

        assertThat(exit).isEqualTo(CliExitCode.FAILURE);
        // one document: the outcome, with the files whose reload failed
        JsonObject outcome = json(printer.getStdout());
        assertThat(outcome.getString("status")).isEqualTo("failed");
        assertThat(outcome.getString("error")).isEqualTo("Unknown step: frm");
        JsonArray files = outcome.getCollection("files");
        assertThat(files).containsExactly("routes/orders.yaml");

        CamelReloadAction text = command("myApp");
        text.waitForOutcome = true;
        assertThatThrownBy(() -> callWithResponse(text, failed)).isInstanceOf(CliException.class)
                .hasMessage("Reloading myApp failed: Unknown step: frm")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.FAILURE);
    }

    @Test
    void waitNeedsAnIntegrationThatReloadsOnDemand() throws Exception {
        writeStatusFile(TEST_PID, json("{\"context\":{\"name\":\"myApp\",\"version\":\"4.23.0\"}}"));
        CamelReloadAction command = command("myApp");
        command.waitForOutcome = true;

        assertThatThrownBy(() -> callWithSingleProcess(command)).isInstanceOf(CliException.class)
                .hasMessageContaining("runs Camel 4.23.0").hasMessageContaining("--wait needs Camel 4.24 or later");
        assertThat(readActionFile(TEST_PID)).isNull();

        // a connector that does not know the option reloads the context, and answers without the files
        writeStatusFile(TEST_PID, "myApp");
        assertThatThrownBy(() -> callWithResponse(command, json("{\"status\":\"success\"}")))
                .isInstanceOf(CliException.class).hasMessageContaining("does not reload the changed files on demand");
    }

    @Test
    void jsonGoesWithWait() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelReloadAction command = command("myApp");
        command.jsonOutput = true;

        assertThatThrownBy(() -> callWithSingleProcess(command)).isInstanceOf(CliException.class)
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
        assertThat(readActionFile(TEST_PID)).isNull();
    }

    private CamelReloadAction command(String name) {
        CamelReloadAction command = new CamelReloadAction(new CamelJBangMain().withPrinter(printer));
        command.name = name;
        return command;
    }

    private static JsonObject json(String text) {
        return Jsoner.deserialize(text.trim(), (JsonObject) null);
    }
}
