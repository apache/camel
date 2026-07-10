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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the provider-choice ordering/de-duplication rules and provider-name validation that used to live as private
 * methods on {@link AiPanel}, now independently testable on {@link AiProviderSelector}.
 */
@Isolated
class AiProviderSelectorTest {

    private final AiProviderSelector selector = new AiProviderSelector();
    private String originalHome;

    @AfterEach
    void tearDown() {
        if (originalHome != null) {
            CommandLineHelper.useHomeDir(originalHome);
        }
    }

    private void useHome(Path dir) {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(dir.toString());
    }

    @Test
    void noEnvKeysYieldsOnlyDefaultAndOllama(@TempDir Path tempDir) {
        useHome(tempDir);

        List<AiProviderSwitchPopup.ProviderChoice> choices = selector.buildChoices(Map.of());

        assertEquals(List.of("auto", "ollama"),
                choices.stream().map(AiProviderSwitchPopup.ProviderChoice::provider).toList());
        assertTrue(choices.get(0).persistedDefault());
    }

    @Test
    void anthropicKeyAddsAnthropicUnlessAlreadyDefault(@TempDir Path tempDir) {
        useHome(tempDir);

        List<AiProviderSwitchPopup.ProviderChoice> choices
                = selector.buildChoices(Map.of("ANTHROPIC_API_KEY", "sk-test"));
        assertEquals(List.of("auto", "anthropic", "ollama"),
                choices.stream().map(AiProviderSwitchPopup.ProviderChoice::provider).toList());

        TuiSettings settings = TuiSettings.load();
        settings.setAiProvider("anthropic");
        settings.save();

        List<AiProviderSwitchPopup.ProviderChoice> withAnthropicDefault
                = selector.buildChoices(Map.of("ANTHROPIC_API_KEY", "sk-test"));
        assertEquals(List.of("anthropic", "ollama"),
                withAnthropicDefault.stream().map(AiProviderSwitchPopup.ProviderChoice::provider).toList(),
                "anthropic must not be listed twice when it's already the default");
    }

    @Test
    void openaiKeyOrLlmApiKeyBothAddOpenaiOnlyOnce(@TempDir Path tempDir) {
        useHome(tempDir);

        List<AiProviderSwitchPopup.ProviderChoice> viaOpenaiKey
                = selector.buildChoices(Map.of("OPENAI_API_KEY", "sk-test"));
        assertEquals(List.of("auto", "openai", "ollama"),
                viaOpenaiKey.stream().map(AiProviderSwitchPopup.ProviderChoice::provider).toList());

        List<AiProviderSwitchPopup.ProviderChoice> viaBothKeys
                = selector.buildChoices(Map.of("OPENAI_API_KEY", "sk-test", "LLM_API_KEY", "sk-other"));
        assertEquals(List.of("auto", "openai", "ollama"),
                viaBothKeys.stream().map(AiProviderSwitchPopup.ProviderChoice::provider).toList(),
                "openai must appear only once even when both env vars are set");
    }

    @Test
    void ollamaDefaultIsNotDuplicated(@TempDir Path tempDir) {
        useHome(tempDir);
        TuiSettings settings = TuiSettings.load();
        settings.setAiProvider("ollama");
        settings.save();

        List<AiProviderSwitchPopup.ProviderChoice> choices = selector.buildChoices(Map.of());

        assertEquals(List.of("ollama"),
                choices.stream().map(AiProviderSwitchPopup.ProviderChoice::provider).toList());
    }

    @Test
    void applyChoiceSetsApiTypeModelAndUrl() {
        LlmClient client = LlmClient.create();

        selector.applyChoice(client, "openai", "gpt-4o", "https://api.example.com");

        assertEquals(LlmClient.ApiType.openai, client.apiType());
        assertEquals("gpt-4o", client.model());
    }

    @Test
    void applyChoiceLeavesApiTypeUntouchedForAutoOrBlank() {
        LlmClient client = LlmClient.create();
        LlmClient.ApiType before = client.apiType();

        selector.applyChoice(client, "auto", null, null);
        assertEquals(before, client.apiType());

        selector.applyChoice(client, "  ", null, null);
        assertEquals(before, client.apiType());
    }

    @Test
    void applyChoiceRejectsUnknownProviderWithActionableMessage() {
        LlmClient client = LlmClient.create();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> selector.applyChoice(client, "bogus", null, null));

        assertTrue(ex.getMessage().contains("bogus"));
        assertTrue(ex.getMessage().contains("ollama"));
        assertTrue(ex.getMessage().contains("openai"));
        assertTrue(ex.getMessage().contains("anthropic"));
    }
}
