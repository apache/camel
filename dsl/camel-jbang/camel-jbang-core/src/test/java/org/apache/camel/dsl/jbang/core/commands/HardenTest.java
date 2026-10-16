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
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel harden: security suggestions for a route by an LLM (a local server plays Ollama, or an OpenAI-compatible
 * service). Only the suggestions are on stdout.
 */
class HardenTest extends CamelCommandBaseTestSupport {

    private static final String ROUTE = """
            - route:
                id: upload
                from:
                  uri: platform-http:/upload
                  steps:
                    - to: ftp:files.example.com/in?username=bob&password=secret
            """;

    private static final String SUGGESTIONS = "Use SFTP instead of FTP, and keep the password in a vault.";

    @TempDir
    Path dir;

    private Path route;
    private FakeLlmServer llm;

    @BeforeEach
    void start() throws Exception {
        route = Files.writeString(dir.resolve("upload.camel.yaml"), ROUTE);
        llm = new FakeLlmServer(SUGGESTIONS);
    }

    @AfterEach
    void stop() {
        llm.close();
    }

    @Test
    void suggestionsFromOllama() throws Exception {
        assertThat(command(route.toString()).doCall()).isZero();

        assertThat(printer.getStdout()).contains("Security Hardening Analysis: " + route, SUGGESTIONS)
                .doesNotContain("LLM Configuration");
        assertThat(printer.getStderr()).contains("LLM Configuration:", "Performing security analysis with test (Ollama)");
        assertThat(llm.lastRequest().getString("prompt")).contains("ftp:files.example.com");
    }

    @Test
    void suggestionsFromAnOpenAiCompatibleService() throws Exception {
        assertThat(command(route.toString(), "--api-type=openai", "--api-key=sk-1234567890abcd").doCall()).isZero();

        assertThat(printer.getStdout()).contains(SUGGESTIONS);
        assertThat(printer.getStderr()).contains("(OpenAI-compatible)", "  API Key: ****abcd");
        JsonObject request = llm.lastRequest();
        assertThat(request.getString("model")).isEqualTo("test");
        assertThat(request.toJson()).contains("ftp:files.example.com");
    }

    @Test
    void thePromptsAreOnStderr() throws Exception {
        assertThat(command(route.toString(), "--show-prompt", "--stream=false").doCall()).isZero();

        assertThat(printer.getStderr()).contains("--- SYSTEM PROMPT ---", "--- USER PROMPT ---");
        assertThat(printer.getStdout()).contains(SUGGESTIONS).doesNotContain("--- SYSTEM PROMPT ---");
    }

    @Test
    void anUnreachableUrlIsNotReplacedByAnotherLlm() throws Exception {
        Harden command = new Harden(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, route.toString(), "--url=http://127.0.0.1:1", "--model=test");

        // the route is not sent to the Ollama of the host
        assertThat(command.doCall()).isEqualTo(CliExitCode.FAILURE);
        assertThat(printer.getStderr()).contains("Cannot connect to LLM service at: http://127.0.0.1:1");
    }

    @Test
    void unknownFormatIsAUsageError() {
        Harden command = command(route.toString(), "--format=json");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--format must be text or markdown, was: json");
    }

    private Harden command(String... args) {
        Harden command = new Harden(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Arrays.stream(args), Stream.of("--url=" + llm.url(), "--model=test"))
                        .toArray(String[]::new));
        return command;
    }
}
