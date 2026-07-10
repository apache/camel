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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;

/**
 * Resolves AI provider/model/url choices for the AI panel: builds the ordered list of choices shown in the
 * {@link AiProviderSwitchPopup} from persisted {@link TuiSettings}, and applies a chosen provider/model/url onto an
 * {@link LlmClient}. Kept independent of {@link AiPanel} so these rules (choice ordering, de-duplication against the
 * persisted default, provider-name validation) can be tested directly instead of only through the panel's key-event
 * handling.
 */
final class AiProviderSelector {

    /**
     * Builds the ordered provider choices for the switch popup: the persisted default first, followed by every other
     * known provider (regardless of whether an API key is currently detected for it, so it stays available for manual
     * selection), skipping whichever one is already the default to avoid listing it twice.
     */
    List<AiProviderSwitchPopup.ProviderChoice> buildChoices() {
        TuiSettings settings = TuiSettings.load();
        String defaultProvider = settings.getAiProvider() != null ? settings.getAiProvider() : "auto";
        List<AiProviderSwitchPopup.ProviderChoice> choices = new ArrayList<>();
        choices.add(new AiProviderSwitchPopup.ProviderChoice(
                defaultProvider,
                settings.getAiModel() != null ? settings.getAiModel() : "",
                settings.getAiUrl() != null ? settings.getAiUrl() : "",
                true));
        if (!"anthropic".equals(defaultProvider)) {
            choices.add(new AiProviderSwitchPopup.ProviderChoice("anthropic", "", "", false));
        }
        if (!"openai".equals(defaultProvider)) {
            choices.add(new AiProviderSwitchPopup.ProviderChoice("openai", "", "", false));
        }
        if (!"ollama".equals(defaultProvider)) {
            choices.add(new AiProviderSwitchPopup.ProviderChoice("ollama", "", "", false));
        }
        return choices;
    }

    /**
     * Applies a provider/model/url choice onto {@code target}. A blank or {@code "auto"} provider leaves the client's
     * default provider selection untouched.
     *
     * @throws IllegalArgumentException if {@code provider} is set and not a recognized {@link LlmClient.ApiType}
     */
    void applyChoice(LlmClient target, String provider, String model, String url) {
        if (provider != null && !provider.isBlank() && !"auto".equals(provider)) {
            target.withApiType(parseApiType(provider));
        }
        if (model != null && !model.isBlank()) {
            target.withModel(model);
        }
        if (url != null && !url.isBlank()) {
            target.withUrl(url);
        }
    }

    private static LlmClient.ApiType parseApiType(String provider) {
        try {
            return LlmClient.ApiType.valueOf(provider.replace('-', '_'));
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(LlmClient.ApiType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Unknown AI provider '" + provider + "'. Valid values: " + valid + ", auto.");
        }
    }
}
