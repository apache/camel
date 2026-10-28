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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel explain: a route explained by an LLM (a local server plays Ollama). Only the explanation is on stdout.
 */
class ExplainTest extends CamelCommandBaseTestSupport {

    private static final String ROUTE = """
            - route:
                id: hello
                from:
                  uri: timer:tick
                  steps:
                    - filter:
                        simple: "${body} != null"
                        steps:
                          - to: kafka:greetings
            """;

    private static final String EXPLANATION = "The route sends a greeting to Kafka every second.";

    @TempDir
    Path dir;

    private Path route;
    private FakeLlmServer llm;

    @BeforeEach
    void start() throws Exception {
        route = Files.writeString(dir.resolve("hello.camel.yaml"), ROUTE);
        llm = new FakeLlmServer(EXPLANATION);
    }

    @AfterEach
    void stop() {
        llm.close();
    }

    @Test
    void explainsTheRoute() throws Exception {
        assertThat(command(route.toString()).doCall()).isZero();

        // streamed: the parts of the answer as they come
        assertThat(printer.getStdout()).contains("Explaining: " + route, EXPLANATION).doesNotContain("LLM Configuration");
        assertThat(printer.getStderr()).contains("LLM Configuration:", "  Model: test", "Analyzing route with test");
        assertThat(llm.lastRequest().getString("prompt")).contains("Route definition:", "kafka:greetings");
    }

    @Test
    void explainsTheRouteWithoutStreaming() throws Exception {
        assertThat(command(route.toString(), "--stream=false").doCall()).isZero();

        assertThat(printer.getStdout()).contains(EXPLANATION);
        assertThat(llm.lastRequest().getBoolean("stream")).isFalse();
    }

    @Test
    void thePromptsAreOnStderr() throws Exception {
        assertThat(command(route.toString(), "--show-prompt", "--format=markdown", "--verbose").doCall()).isZero();

        assertThat(printer.getStderr()).contains("--- SYSTEM PROMPT ---", "Format output as Markdown",
                "Include technical details", "--- USER PROMPT ---", "kafka:greetings");
        assertThat(printer.getStdout()).doesNotContain("--- SYSTEM PROMPT ---");
    }

    @Test
    void catalogContextDescribesTheComponentsAndEips() throws Exception {
        assertThat(command(route.toString(), "--catalog-context").doCall()).isZero();

        String prompt = llm.lastRequest().getString("prompt");
        assertThat(prompt).contains("Reference information:", "- kafka: ", "- timer: ", "(EIP)");
    }

    @Test
    void customSystemPrompt() throws Exception {
        assertThat(command(route.toString(), "--system-prompt=Answer in one word.").doCall()).isZero();

        assertThat(llm.lastRequest().getString("system")).isEqualTo("Answer in one word.");
    }

    @Test
    void aShortApiKeyIsNotShown() throws Exception {
        assertThat(command(route.toString(), "--api-key=abc").doCall()).isZero();

        assertThat(printer.getStderr()).contains("  API Key: ****").doesNotContain("abc");
    }

    @Test
    void aLongApiKeyShowsItsLastCharacters() throws Exception {
        assertThat(command(route.toString(), "--api-key=sk-1234567890abcd").doCall()).isZero();

        assertThat(printer.getStderr()).contains("  API Key: ****abcd").doesNotContain("sk-1234567890");
    }

    @Test
    void unknownFormatIsAUsageError() {
        Explain command = command(route.toString(), "--format=json");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--format must be text or markdown, was: json");
        assertThat(llm.lastRequest()).isNull();
    }

    @Test
    void aFileThatDoesNotExistIsNotFound() throws Exception {
        assertThat(command(dir.resolve("nope.yaml").toString()).doCall()).isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getStderr()).contains("File not found: ");
        assertThat(llm.lastRequest()).isNull();
    }

    private Explain command(String... args) {
        Explain command = new Explain(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Arrays.stream(args), Stream.of("--api-type=ollama", "--url=" + llm.url(), "--model=test"))
                        .toArray(String[]::new));
        return command;
    }
}
