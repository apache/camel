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
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

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
    void headerWithoutValueIsAUsageError() {
        assertThatThrownBy(() -> command("orders", "--endpoint=direct:start", "--header=id").doCall())
                .isInstanceOf(CliException.class)
                .hasMessage("Header must be in key=value format, was: id")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    private CamelSendAction command(String... args) {
        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
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
