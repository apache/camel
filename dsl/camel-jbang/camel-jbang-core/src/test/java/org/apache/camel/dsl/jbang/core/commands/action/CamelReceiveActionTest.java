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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd receive: starting and stopping to receive in a running integration, its status, and the messages it
 * received.
 */
@ExtendWith(MockitoExtension.class)
class CamelReceiveActionTest extends ActionCommandTestSupport {

    @Test
    void testStartsReceivingFromNamedIntegration() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        // the start sub-action against the running integration (no --endpoint, so no auto-dump/spawn)
        int exit = callWithResponse(command("myApp", "--action=start"), new JsonObject());

        assertThat(exit).isZero();
        JsonObject action = readActionFile(TEST_PID);
        assertThat(action).as("action file should be written for the matched process").isNotNull();
        assertThat(action.getString("action")).isEqualTo("receive");
        assertThat(action.getString("enabled")).isEqualTo("true");
        assertThat(action.getString("endpoint")).isEqualTo("*");
        assertThat(printer.getOutput()).contains("Starting to receive messages from existing Camel: myApp");
    }

    @Test
    void testRendersNothingWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelReceiveAction command = command("doesNotExist", "--action=start");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No running Camel integration matches: doesNotExist");
        assertThat(printer.getOutput()).as("nothing should be rendered when no process matches").isEmpty();
    }

    @Test
    void stopTurnsReceivingOff() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("myApp", "--action=stop"), new JsonObject())).isZero();

        assertThat(readActionFile(TEST_PID).getString("enabled")).isEqualTo("false");
    }

    @Test
    void failureToStartReceivingIsAnError() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        JsonObject reply = new JsonObject();
        reply.put("url", "kafka:payments");
        reply.put("error", "Cannot connect to the broker");

        assertThat(callWithResponse(command("myApp", "--action=start"), reply)).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr())
                .contains("Error starting to receive messages from: kafka:payments due to: Cannot connect to the broker");
    }

    @Test
    void endpointStartsReceivingAndDumpsTheMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeReceived(batch(message(1, "Hello Camel")));

        assertThat(callWithResponse(command("myApp", "--endpoint=seda:orders"), new JsonObject())).isZero();

        assertThat(readActionFile(TEST_PID).getString("endpoint")).isEqualTo("seda:orders");
        assertThat(printer.getOutput()).contains("Received Message: (1)", "Hello Camel");
    }

    @Test
    void clearEmptiesTheReceivedMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        Path received = writeReceived(batch(message(1, "Hello Camel")));

        assertThat(callWithSingleProcess(command("myApp", "--action=clear"))).isZero();

        assertThat(Files.readString(received)).isEqualTo("{}");
    }

    @Test
    void unknownActionIsAUsageError() {
        assertThatThrownBy(() -> command("myApp", "--action=pause").doCall())
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessageContaining("Unknown action: pause");
    }

    @Test
    void statusListsTheEndpointsOfTheIntegration() throws Exception {
        writeStatusFile(TEST_PID, receiveStatus("seda:orders", "jms:payments?password=secret"));

        assertThat(callWithSingleProcess(command("myApp", "--mask"))).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("myApp", "Enabled", "seda:orders", "jms:payments");
        assertThat(out).as("--mask hides the password").doesNotContain("secret");
    }

    @Test
    void statusWithAnUnknownSortIsAUsageError() throws Exception {
        writeStatusFile(TEST_PID, receiveStatus("seda:orders"));

        CamelReceiveAction command = command("myApp", "--sort=size");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessageContaining("--sort must be one of");
    }

    @Test
    void dumpPrintsTheReceivedMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeReceived(batch(message(1, "Hello Camel"), message(2, "Bye Camel")));

        assertThat(callWithSingleProcess(command("myApp", "--action=dump"))).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("Received Message: (1)", "Received Message: (2)", "seda:orders", "orderId",
                "Hello Camel", "Bye Camel");
    }

    @Test
    void dumpWithoutANameReadsTheRunningIntegrations() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeReceived(batch(message(1, "Hello Camel")));

        assertThat(callWithSingleProcess(command("--action=dump"))).isZero();

        assertThat(printer.getOutput()).contains("Received Message: (1)", "Hello Camel");
    }

    @Test
    void dumpOfANameThatMatchesNoIntegrationIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelReceiveAction command = command("doesNotExist", "--action=dump");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND));
    }

    @Test
    void dumpOnlyTheBody() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeReceived(batch(message(1, "Hello Camel")));

        assertThat(callWithSingleProcess(command("myApp", "--action=dump", "--only-body"))).isZero();

        assertThat(printer.getOutput()).contains("Hello Camel").doesNotContain("orderId");
    }

    @Test
    void dumpTheLastMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeReceived(batch(message(1, "first")), batch(message(2, "second"), message(3, "third")));

        assertThat(callWithSingleProcess(command("myApp", "--action=dump", "--tail=1"))).isZero();

        assertThat(printer.getOutput()).contains("third").doesNotContain("first", "second");
    }

    @Test
    void dumpTheMatchingMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeReceived(batch(message(1, "Hello Camel"), message(2, "Bye World")));

        assertThat(callWithSingleProcess(command("myApp", "--action=dump", "--grep=world"))).isZero();

        // without the highlighting of the match
        assertThat(printer.getOutput().replaceAll("\u001B\\[[;\\d]*m", "")).contains("Bye World")
                .doesNotContain("Hello Camel");
    }

    @Test
    void dumpAsJsonPrintsEachMessageOnce() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeReceived(batch(message(1, "Hello Camel"), message(2, "Bye Camel")));

        assertThat(callWithSingleProcess(command("myApp", "--action=dump", "--output=json"))).isZero();

        List<String> lines = printer.getLines();
        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(l -> Jsoner.deserialize(l, (JsonObject) null).getLong("uid")).containsExactly(1L, 2L);
        assertThat(lines.get(0)).contains("Hello Camel").doesNotContain("Bye Camel");
    }

    private CamelReceiveAction command(String... args) {
        CamelReceiveAction command = new CamelReceiveAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Stream.of("--follow=false", "--logging-color=false"), Arrays.stream(args))
                        .toArray(String[]::new));
        return command;
    }

    // the received messages, a batch per line as the camel-cli-connector writes them
    private static Path writeReceived(JsonObject... batches) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(TEST_PID + "-receive.json");
        Files.write(f, Arrays.stream(batches).map(JsonObject::toJson).toList());
        return f;
    }

    private static JsonObject batch(JsonObject... messages) {
        JsonObject batch = new JsonObject();
        batch.put("messages", new JsonArray(Arrays.asList(messages)));
        return batch;
    }

    private static JsonObject message(long uid, String body) {
        JsonObject header = new JsonObject();
        header.put("key", "orderId");
        header.put("type", "java.lang.String");
        header.put("value", "42");
        JsonObject value = new JsonObject();
        value.put("type", "java.lang.String");
        value.put("value", body);
        JsonObject message = new JsonObject();
        message.put("exchangeId", "A-" + uid);
        message.put("messageType", "DefaultMessage");
        message.put("headers", new JsonArray(List.of(header)));
        message.put("body", value);
        JsonObject answer = new JsonObject();
        answer.put("uid", uid);
        answer.put("endpointUri", "seda:orders");
        answer.put("remoteEndpoint", false);
        answer.put("timestamp", System.currentTimeMillis());
        answer.put("message", message);
        return answer;
    }

    private static JsonObject receiveStatus(String... uris) {
        JsonObject receive = new JsonObject();
        receive.put("enabled", true);
        receive.put("total", 3);
        receive.put("lastTimestamp", System.currentTimeMillis());
        receive.put("endpoints", new JsonArray(Arrays.stream(uris).map(uri -> {
            JsonObject endpoint = new JsonObject();
            endpoint.put("uri", uri);
            return endpoint;
        }).toList()));
        JsonObject context = new JsonObject();
        context.put("name", "myApp");
        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("receive", receive);
        return root;
    }
}
