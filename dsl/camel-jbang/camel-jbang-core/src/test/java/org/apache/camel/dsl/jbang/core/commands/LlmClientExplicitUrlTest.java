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

import org.apache.camel.dsl.jbang.core.commands.LlmClient.ApiType;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An endpoint the user names with --url is the only one the client uses: when it does not answer, the client does not
 * go on to another provider. Here there is always another one to find, an Ollama started with camel infra run.
 */
class LlmClientExplicitUrlTest {

    private static final String NOTHING_THERE = "http://127.0.0.1:1";

    @TempDir
    Path home;

    private Path previousHome;
    private FakeLlmServer infraOllama;

    @BeforeEach
    void start() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        // what camel infra run ollama leaves for the CLI to find
        infraOllama = new FakeLlmServer("unused");
        Files.writeString(CommandLineHelper.getCamelDir().resolve("infra-ollama-4711.json"),
                "{\"baseUrl\":\"" + infraOllama.url() + "\"}");
    }

    @AfterEach
    void stop() {
        infraOllama.close();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void withoutAUrlTheOllamaOfCamelInfraIsFound() {
        LlmClient client = LlmClient.create().withApiType(ApiType.ollama);

        assertThat(client.detectEndpoint()).isTrue();
        assertThat(client.endpointUrl()).isEqualTo(infraOllama.url());
    }

    @Test
    void anUnreachableUrlOfAnApiType() {
        LlmClient client = LlmClient.create().withApiType(ApiType.ollama).withUrl(NOTHING_THERE);

        assertThat(client.detectEndpoint()).isFalse();
        // not the url of another Ollama
        assertThat(client.endpointUrl()).isEqualTo(NOTHING_THERE);
    }

    @Test
    void anUnreachableUrlWithoutAnApiType() {
        LlmClient client = LlmClient.create().withUrl(NOTHING_THERE);

        assertThat(client.detectEndpoint()).isFalse();
        assertThat(client.endpointUrl()).isEqualTo(NOTHING_THERE);
        assertThat(client.apiType()).isNull();
    }

    @Test
    void aUrlThatAnswersIsUsed() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer("unused")) {
            LlmClient client = LlmClient.create().withUrl(server.url());

            assertThat(client.detectEndpoint()).isTrue();
            assertThat(client.endpointUrl()).isEqualTo(server.url());
            assertThat(client.apiType()).isEqualTo(ApiType.ollama);
        }
    }
}
