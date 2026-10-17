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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel overview: the overview of the routes of a project, the summary file, and what an LLM adds to it (a local server
 * plays Ollama).
 */
class OverviewTest extends CamelCommandBaseTestSupport {

    private static final String ROUTE = """
            - route:
                id: hello
                from:
                  uri: timer:tick
                  steps:
                    - log: Hello Camel
            """;

    // the answer of the LLM, in the format the summary asks for
    private static final String ANSWER = """
            OVERVIEW: The project logs a greeting every second.
            DESCRIPTIONS:
            - hello: Say hello | Logs a greeting on a timer.
            """;

    @TempDir
    Path project;

    private HttpServer ollama;
    private volatile String answer = ANSWER;

    @BeforeEach
    void route() throws Exception {
        Files.writeString(project.resolve("hello.camel.yaml"), ROUTE);
    }

    @AfterEach
    void stopOllama() {
        if (ollama != null) {
            ollama.stop(0);
        }
    }

    @Test
    void overviewOfTheRoutes() throws Exception {
        assertThat(command().doCall()).isZero();

        assertThat(printer.getOutput()).contains("`hello`", "`timer:tick`");
    }

    @Test
    void overviewAsJson() throws Exception {
        assertThat(command("--format=json").doCall()).isZero();

        JsonObject json = Jsoner.deserialize(printer.getOutput(), (JsonObject) null);
        JsonArray routes = (JsonArray) json.get("routes");
        JsonObject route = routes.getMap(0);
        assertThat(route.getString("route")).isEqualTo("hello");
        assertThat(route.getString("from")).isEqualTo("timer:tick");
    }

    @Test
    void unknownFormatIsAUsageError() {
        Overview command = command("--format=yaml");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--format must be markdown or json, was: yaml");
    }

    @Test
    void directoryThatDoesNotExistIsNotFound() {
        Overview command = new Overview(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, project.resolve("nope").toString());

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND));
    }

    @Test
    void aFileIsNotAProjectDirectory() {
        Overview command = new Overview(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, project.resolve("hello.camel.yaml").toString());

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE));
    }

    @Test
    void aProjectWithoutRoutesIsAFailure() throws Exception {
        Files.delete(project.resolve("hello.camel.yaml"));

        Overview command = command();

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessageStartingWith("No routes found in ");
    }

    @Test
    void saveWritesTheSummary() throws Exception {
        assertThat(command("--save").doCall()).isZero();

        assertThat(project.resolve(IntegrationSummary.FILE_NAME)).exists();
        assertThat(printer.getStderr()).contains("Saved " + IntegrationSummary.FILE_NAME);
    }

    @Test
    void aiWritesWhatTheLlmAnswersIntoTheSummary() throws Exception {
        startOllama();

        assertThat(command(ai()).doCall()).isZero();

        String summary = Files.readString(project.resolve(IntegrationSummary.FILE_NAME));
        assertThat(summary).contains("The project logs a greeting every second.", IntegrationSummary.AI_PREFIX + "Say hello");
        // the summary is printed, and what was saved is on stderr
        assertThat(printer.getStdout()).contains("Say hello");
        assertThat(printer.getStderr()).contains("1 route descriptions, 1 notes (AI-assisted");
    }

    @Test
    void anAnswerInAnotherFormatIsAFailure() throws Exception {
        answer = "I am not sure what these routes do.";
        startOllama();

        Overview command = command(ai());

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessageContaining("did not follow the expected format; nothing was saved");
        assertThat(printer.getStderr()).contains("I am not sure what these routes do.");
        assertThat(project.resolve(IntegrationSummary.FILE_NAME)).doesNotExist();
    }

    @Test
    void aiWithoutAnLlmIsAFailure() {
        // an --url that does not answer is a failure: the client does not go on to another provider
        Overview command = command("--ai", "--api-type=ollama", "--url=http://127.0.0.1:1");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessageStartingWith("LLM service is not running or not reachable");
    }

    @Test
    void applyDescriptionsPutsThemIntoTheRoutes() throws Exception {
        startOllama();
        assertThat(command(ai()).doCall()).isZero();

        assertThat(command("--apply-descriptions").doCall()).isZero();

        assertThat(Files.readString(project.resolve("hello.camel.yaml"))).contains("Say hello");
        assertThat(printer.getStdout()).contains("Updated hello.camel.yaml: hello");
        // the description is part of the route now, so the summary no longer has it as AI-assisted
        assertThat(Files.readString(project.resolve(IntegrationSummary.FILE_NAME)))
                .doesNotContain(IntegrationSummary.AI_PREFIX + "Say hello");
    }

    @Test
    void applyDescriptionsWithoutASummaryIsAFailure() {
        Overview command = command("--apply-descriptions");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessageContaining("run camel overview --ai first");
    }

    private Overview command(String... args) {
        Overview command = new Overview(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Stream.of(project.toString()), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    private String[] ai() {
        return new String[] {
                "--ai", "--api-type=ollama", "--url=http://127.0.0.1:" + ollama.getAddress().getPort(), "--model=test" };
    }

    // plays Ollama: its models, and the answer of the model
    private void startOllama() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ollama.createContext("/api/tags", ex -> reply(ex, "{\"models\":[{\"name\":\"test\",\"size\":1000}]}"));
        ollama.createContext("/api/ps", ex -> reply(ex, "{\"models\":[]}"));
        ollama.createContext("/api/show", ex -> reply(ex, "{\"model_info\":{}}"));
        ollama.createContext("/api/generate", ex -> {
            JsonObject response = new JsonObject();
            response.put("response", answer);
            response.put("done", true);
            reply(ex, response.toJson());
        });
        ollama.start();
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
