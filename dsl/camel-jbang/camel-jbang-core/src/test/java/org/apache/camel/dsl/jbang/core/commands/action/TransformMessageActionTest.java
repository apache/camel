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
 * camel transform message: what it asks the Camel it started to transform, and how it shows the answer.
 */
@ExtendWith(MockitoExtension.class)
class TransformMessageActionTest extends ActionCommandTestSupport {

    @TempDir
    Path dir;

    @Test
    void transformsTheMessage() throws Exception {
        TransformMessageAction command = command("--body=Hello World", "--language=simple", "--template=Bye ${body}",
                "--header=id=1", "--option=trim=true");

        assertThat(callWithResponse(command::doWatchCall, answer("success", "Bye World"))).isEqualTo(CliExitCode.OK);

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("transform");
        assertThat(action.getString("language")).isEqualTo("simple");
        assertThat(action.getString("template")).isEqualTo("Bye ${body}");
        assertThat(action.getString("body")).isEqualTo("Hello World");
        JsonObject header = ((JsonArray) action.get("headers")).getMap(0);
        assertThat(header.getString("key") + "=" + header.getString("value")).isEqualTo("id=1");
        JsonObject option = ((JsonArray) action.get("options")).getMap(0);
        assertThat(option.getString("key") + "=" + option.getString("value")).isEqualTo("trim=true");
        assertThat(printer.getStdout()).contains("Message transformed (success)", " (3ms)", "Bye World");
    }

    @Test
    void outputGoesToTheFile() throws Exception {
        Path output = dir.resolve("out.json");
        TransformMessageAction command = command("--body=Hello", "--language=simple", "--template=Bye",
                "--output=" + output);

        assertThat(callWithResponse(command::doWatchCall, answer("success", "Bye"))).isEqualTo(CliExitCode.OK);

        assertThat(printer.getStdout()).contains("Output saved to file (success)").doesNotContain("Bye");
        JsonObject written = Jsoner.deserialize(Files.readString(output), (JsonObject) null);
        assertThat(written.getString("exchangeId")).isEqualTo("ex-1");
    }

    @Test
    void failedTransformationShowsTheException() throws Exception {
        TransformMessageAction command = command("--body=Hello", "--language=simple", "--template=${bean:nope}");
        JsonObject answer = answer("failed", null);
        answer.put("exception",
                new JsonObject(Map.of("type", "org.apache.camel.NoSuchBeanException", "message", "No bean: nope")));

        assertThat(callWithResponse(command::doWatchCall, answer)).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStdout()).contains("Failed (exception)", "No bean: nope");
    }

    @Test
    void anActionTheIntegrationFailsIsReported() throws Exception {
        TransformMessageAction command = command("--body=Hello", "--language=nope", "--template=Bye");
        // the answer of the camel-cli-connector when the action throws an exception: no message, no timestamp
        JsonObject failed = new JsonObject(Map.of("status", "failed", "error", "No language could be found for: nope"));

        assertThat(callWithResponse(command::doWatchCall, failed)).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStdout()).contains("Failed (exception)");
        assertThat(printer.getStderr()).contains("No language could be found for: nope");
    }

    @Test
    void coloredStatusLineIsKeptByThePrinter() throws Exception {
        TransformMessageAction command = command("--body=Hello", "--language=simple", "--template=Bye",
                "--logging-color=true");

        assertThat(callWithResponse(command::doWatchCall, answer("success", "Bye"))).isEqualTo(CliExitCode.OK);

        String out = printer.getStdout().replaceAll("\u001B\\[[;\\d]*m", "");
        assertThat(out).contains(Long.toString(TEST_PID), "Message transformed (success)", " (3ms)");
    }

    @Test
    void headerWithoutValueIsAUsageError() {
        TransformMessageAction command = command("--body=Hello", "--language=simple", "--template=Bye", "--header=id");

        assertThatThrownBy(command::doWatchCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("Header must be in key=value format, was: id");
    }

    @Test
    void templateAndLanguageAreRequired() throws Exception {
        assertThat(command("--body=Hello").doCall()).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr()).contains("Either source or template and one of language/component must be configured");

        assertThat(command("--body=Hello", "--language=simple").doCall()).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr()).contains("Both template and one of language/component must be configured");
    }

    @Test
    void templateFileThatDoesNotExistIsNotFound() throws Exception {
        Path template = dir.resolve("nope.txt");

        assertThat(command("--body=Hello", "--language=simple", "--template=file:" + template).doCall())
                .isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getStderr()).contains("Template file does not exist: " + template);
    }

    private TransformMessageAction command(String... args) {
        TransformMessageAction command = new TransformMessageAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        if (!String.join(" ", args).contains("--logging-color")) {
            command.loggingColor = false;
        }
        // the Camel the command started, here the fake integration
        command.pid = TEST_PID;
        return command;
    }

    // the answer of the transform action of the integration
    private static JsonObject answer(String status, String transformed) {
        JsonObject answer = new JsonObject();
        answer.put("language", "simple");
        answer.put("exchangeId", "ex-1");
        answer.put("timestamp", 1_700_000_000_000L);
        answer.put("elapsed", 3L);
        answer.put("status", status);
        if (transformed != null) {
            JsonObject body = new JsonObject();
            body.put("type", "java.lang.String");
            body.put("value", transformed);
            JsonObject message = new JsonObject();
            message.put("body", body);
            answer.put("message", message);
        }
        return answer;
    }
}
