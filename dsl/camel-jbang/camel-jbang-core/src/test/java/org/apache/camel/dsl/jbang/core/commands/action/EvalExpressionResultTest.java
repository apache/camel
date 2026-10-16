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
 * camel eval expression in a running integration: what it sends, and the result or the error it prints.
 */
@ExtendWith(MockitoExtension.class)
class EvalExpressionResultTest extends ActionCommandTestSupport {

    @Test
    void resultOfTheExpression() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        int exit = callWithResponse(command("orders", "--template=${body.length()}", "--body=hello", "--header=id=1",
                "--variable=total=2"), new JsonObject(Map.of("status", "success", "result", "5")));

        assertThat(exit).isEqualTo(CliExitCode.OK);
        assertThat(printer.getStdout()).isEqualTo("5");
        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("eval");
        assertThat(action.getString("language")).isEqualTo("simple");
        assertThat(action.getString("template")).isEqualTo("${body.length()}");
        assertThat(action.getString("body")).isEqualTo("hello");
        JsonArray headers = action.getCollection("headers");
        JsonObject header = headers.getMap(0);
        assertThat(header.getString("key")).isEqualTo("id");
        assertThat(header.getString("value")).isEqualTo("1");
        JsonArray variables = action.getCollection("variables");
        JsonObject variable = variables.getMap(0);
        assertThat(variable.getString("key")).isEqualTo("total");
    }

    @Test
    void failedEvaluationIsAnError() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        JsonObject exception = new JsonObject(Map.of("message", "Unknown function: foo", "stackTrace", "at Simple"));
        JsonObject answer = new JsonObject(Map.of("status", "failed", "exception", exception));

        assertThatThrownBy(() -> callWithResponse(command("orders", "--template=${foo}"), answer))
                .isInstanceOf(CliException.class)
                .hasMessage("Unknown function: foo")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.FAILURE);
        // the stack trace on stderr
        assertThat(printer.getStderr()).contains("at Simple");
    }

    @Test
    void headerWithoutValueIsAUsageError() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        assertThatThrownBy(() -> callWithResponse(command("orders", "--template=${body}", "--header=id"), null))
                .isInstanceOf(CliException.class)
                .hasMessage("Header must be in key=value format, was: id")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    private EvalExpressionCommand command(String... args) {
        EvalExpressionCommand command = new EvalExpressionCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }
}
