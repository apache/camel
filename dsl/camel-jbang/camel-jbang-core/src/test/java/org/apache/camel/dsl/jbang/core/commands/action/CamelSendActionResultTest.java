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
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd send: the message it sends, and the exit code of what the integration answers.
 */
@ExtendWith(MockitoExtension.class)
class CamelSendActionResultTest extends ActionCommandTestSupport {

    @TempDir
    Path dir;

    @Test
    void sendsTheMessage() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        int exit = callWithResponse(command("orders", "--endpoint=direct:start", "--body=hello", "--header=id=1",
                "--timeout=100"), answer("success"));

        assertThat(exit).isEqualTo(CliExitCode.OK);
        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("send");
        assertThat(action.getString("endpoint")).isEqualTo("direct:start");
        assertThat(action.getString("body")).isEqualTo("hello");
        assertThat(action.getString("exchangePattern")).isEqualTo("InOnly");
        // the timeout is at least 5 seconds
        assertThat(action.getLong("pollTimeout")).isEqualTo(5000L);
        JsonArray headers = action.getCollection("headers");
        JsonObject header = headers.getMap(0);
        assertThat(header.getString("key")).isEqualTo("id");
        assertThat(header.getString("value")).isEqualTo("1");
    }

    @Test
    void failedAndTimedOutExchanges() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        // the reply message and an exception without a message
        assertThat(callWithResponse(command("orders", "--endpoint=direct:start", "--reply"), answer("error")))
                .isEqualTo(CliExitCode.FAILURE);
        assertThat(printer.getStdout()).contains("Failed (exception)").contains("NullPointerException");
        assertThat(callWithResponse(command("orders", "--endpoint=direct:start"), answer("timeout")))
                .isEqualTo(CliExitCode.TIMEOUT);
    }

    @Test
    void replyGoesToTheReplyFile() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        Path reply = dir.resolve("reply.json");

        int exit = callWithResponse(command("orders", "--endpoint=direct:start", "--reply-file=" + reply),
                answer("success"));

        assertThat(exit).isEqualTo(CliExitCode.OK);
        assertThat(readActionFile(TEST_PID).getString("exchangePattern")).isEqualTo("InOut");
        JsonObject written = Jsoner.deserialize(Files.readString(reply), (JsonObject) null);
        assertThat(written.getString("exchangeId")).isEqualTo("ex-1");
    }

    @Test
    void bodyFromAFileIsSentWithItsAbsolutePath() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        Path body = Files.writeString(dir.resolve("order.json"), "{}");

        callWithResponse(command("orders", "--endpoint=direct:start", "--body=file:" + body), answer("success"));

        assertThat(readActionFile(TEST_PID).getString("body")).isEqualTo("file:" + body.toAbsolutePath());
    }

    @Test
    void coloredStatusLineIsKeptByThePrinter() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        CamelSendAction command = command("orders", "--endpoint=direct:start");
        command.loggingColor = true;

        assertThat(callWithResponse(command, answer("success"))).isEqualTo(CliExitCode.OK);

        // the colored parts of the status line too, so a CLI that runs the command in its JVM has all of it
        String out = printer.getStdout().replaceAll("\u001B\\[[;\\d]*m", "");
        assertThat(out).contains(Long.toString(TEST_PID), "direct://start", "Reply received (success)", " (5ms)");
    }

    @Test
    void anActionTheIntegrationFailsIsReported() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        // the answer of the camel-cli-connector when the action throws an exception: no exchange, no elapsed time
        JsonObject failed = new JsonObject(Map.of("status", "failed", "error", "No endpoint could be found for: foo:bar"));

        assertThat(callWithResponse(command("orders", "--endpoint=foo:bar"), failed)).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).contains("No endpoint could be found for: foo:bar");
    }

    @Test
    void repeatSendsASummary() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        Path feed = Files.createDirectories(dir.resolve("refunds-fails"));
        Files.writeString(feed.resolve("001.json"), "{}");
        Files.writeString(feed.resolve("001.properties"), "country=ES\n");
        Files.writeString(feed.resolve("002.json"), "{}");
        JsonObject summary = new JsonObject();
        summary.put("timestamp", 1_700_000_000_000L);
        summary.put("status", "failed");
        summary.put("elapsed", 1200L);
        summary.put("endpoint", "kafka://refunds");
        summary.put("messages", 50L);
        summary.put("sent", 50L);
        summary.put("failed", 2L);
        JsonObject error = new JsonObject();
        error.put("message", "ChaosException: Chaos error of rule r1");
        error.put("count", 2L);
        summary.put("errors", new JsonArray(List.of(error)));

        CamelSendAction command = command("orders", "--endpoint=kafka:refunds", "--body=file:" + feed, "--repeat=50",
                "--interval=200ms");
        int exit = callWithResponse(command, summary);

        assertThat(exit).isEqualTo(CliExitCode.FAILURE);
        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("body")).isEqualTo("file:" + feed.toAbsolutePath());
        assertThat(action.getLong("repeat")).isEqualTo(50L);
        assertThat(action.getLong("interval")).isEqualTo(200L);
        assertThat(printer.getStdout()).contains("kafka://refunds", "Sent 50 messages (48 ok, 2 failed)")
                .contains("2 x ChaosException: Chaos error of rule r1");
        // the wait: the timeout, and the pauses and a second for each of the 50 messages
        assertThat(command.waitMillis()).isEqualTo(20000 + 10000 + 50 * 1200);

        // a folder without a repeat: one message per file, which the wait counts
        CamelSendAction folder = command("orders", "--endpoint=kafka:refunds", "--body=file:" + feed);
        assertThat(folder.waitMillis()).isEqualTo(20000 + 10000 + 2 * 1000);
    }

    @Test
    void whatCannotBeRepeated() {
        assertThatThrownBy(() -> command("orders", "--endpoint=direct:start", "--repeat=3", "--poll").doCall())
                .isInstanceOf(CliException.class).hasMessageContaining("A poll does not repeat")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
        assertThatThrownBy(() -> command("orders", "--endpoint=direct:start", "--repeat=3", "--reply-file=x.json")
                .doCall()).hasMessageContaining("--reply-file saves one reply");
        assertThatThrownBy(() -> command("orders", "--endpoint=direct:start", "--repeat=10001").doCall())
                .hasMessageContaining("--repeat must be between 1 and 10000");
        assertThatThrownBy(() -> command("orders", "--endpoint=direct:start", "--repeat=3", "--interval=2m").doCall())
                .hasMessageContaining("--interval must be at most 60s");
        assertThatThrownBy(() -> command("orders", "--endpoint=direct:start", "--repeat=3", "--interval=500..100").doCall())
                .isInstanceOf(CliException.class).hasMessageContaining("from the shortest to the longest pause");
    }

    @Test
    void aRangeDrawsEachPauseAtRandom() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject summary = new JsonObject(
                Map.of("status", "success", "sent", 10L, "failed", 0L, "elapsed", 3000L,
                        "endpoint", "direct://start", "errors", new JsonArray()));

        CamelSendAction command = command("orders", "--endpoint=direct:start", "--repeat=10", "--interval=100..500");
        assertThat(callWithResponse(command, summary)).isEqualTo(CliExitCode.OK);

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getLong("interval")).isEqualTo(100L);
        assertThat(action.getLong("intervalMax")).isEqualTo(500L);
        // the wait counts the longest pause
        assertThat(command.waitMillis()).isEqualTo(20000 + 10000 + 10 * 1500);
        assertThat(printer.getStdout()).contains("Sent 10 messages (success)");
    }

    @Test
    void headerWithoutValueIsAUsageError() {
        assertThatThrownBy(() -> command("orders", "--endpoint=direct:start", "--header=id").doCall())
                .isInstanceOf(CliException.class)
                .hasMessage("Header must be in key=value format, was: id")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    private CamelSendAction command(String... args) {
        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        CommandLineTestHelper.parse(command, args);
        // the status line on the printer, not the console
        command.loggingColor = false;
        return command;
    }

    // the answer of the send dev console of the integration
    private static JsonObject answer(String status) {
        JsonObject answer = new JsonObject();
        answer.put("timestamp", 1_700_000_000_000L);
        answer.put("status", status);
        answer.put("elapsed", 5L);
        answer.put("endpoint", "direct://start");
        answer.put("exchangeId", "ex-1");
        if ("error".equals(status)) {
            // an exception without a message, such as a NullPointerException
            answer.put("exception", new JsonObject(Map.of("type", "java.lang.NullPointerException")));
        }
        JsonObject body = new JsonObject();
        body.put("type", "java.lang.String");
        body.put("value", "reply");
        JsonObject message = new JsonObject();
        message.put("exchangeType", "DefaultExchange");
        message.put("messageType", "DefaultMessage");
        message.put("body", body);
        answer.put("message", message);
        return answer;
    }
}
