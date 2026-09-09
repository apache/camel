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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import dev.tamboui.style.Color;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;

/**
 * Resolves AI provider/model/url choices for the AI panel: builds the ordered list of choices shown in the
 * {@link AiProviderSwitchPopup} from persisted {@link TuiSettings}, and applies a chosen provider/model/url onto an
 * {@link LlmClient}. Kept independent of {@link AiPanel} so these rules (choice ordering, de-duplication against the
 * persisted default, provider-name validation) can be tested directly instead of only through the panel's key-event
 * handling.
 */
final class AiProviderSelector {

    static final String ACP_PREFIX = "acp:";
    static final String ACP_CUSTOM = "acp:custom";
    private static final String NPX_HINT = "npx not found: install Node.js 22 or newer (https://nodejs.org) and try again.";

    /**
     * One external ACP agent the panel knows how to launch. {@code executable} is the first token of the command,
     * checked on the PATH before spawning so a missing tool yields {@code installHint} instead of an obscure error.
     * {@code glyph}, {@code color} and {@code logo} identify the agent in the panel's header strip: the logo names a
     * {@code /tui/logos/<logo>.png} resource drawn in terminals with native graphics, the glyph is the fallback
     * everywhere else.
     */
    record AcpPreset(String id, String label, List<String> command, String executable, String loginHint,
            String installHint, String glyph, Color color, String logo) {
    }

    private static final List<AcpPreset> ACP_PRESETS = List.of(
            new AcpPreset(
                    "acp:claude", "Claude Code (ACP)",
                    List.of("npx", "-y", "@agentclientprotocol/claude-agent-acp"), "npx",
                    "Log in with the claude CLI or set ANTHROPIC_API_KEY, then ask again.", NPX_HINT,
                    "✱", Color.rgb(0xD9, 0x77, 0x57), "claude"),
            new AcpPreset(
                    "acp:codex", "Codex (ACP)",
                    List.of("npx", "-y", "@agentclientprotocol/codex-acp"), "npx",
                    "Run `codex login` or set OPENAI_API_KEY, then ask again.", NPX_HINT,
                    "⬢", Color.rgb(0x10, 0xA3, 0x7F), "codex"),
            new AcpPreset(
                    "acp:bob", "IBM Bob (ACP)",
                    List.of("bob", "acp"), "bob",
                    "Set BOBSHELL_API_KEY or run `bob` once to sign in, then ask again.",
                    "bob not found: install Bob Shell (https://bob.ibm.com/docs/shell) and try again.",
                    "◆", Color.rgb(0x0F, 0x62, 0xFE), "bob"),
            new AcpPreset(
                    "acp:qwen", "Qwen Code (ACP)",
                    List.of("qwen", "--acp"), "qwen",
                    "Set OPENAI_API_KEY and OPENAI_BASE_URL for Qwen Code, then ask again.",
                    "qwen not found: npm install -g @qwen-code/qwen-code and try again.",
                    "✦", Color.rgb(0x61, 0x5C, 0xED), "qwen"),
            new AcpPreset(
                    "acp:opencode", "OpenCode (ACP)",
                    List.of("opencode", "acp"), "opencode",
                    "Run `opencode auth login`, then ask again.",
                    "opencode not found: install it from https://opencode.ai and try again.",
                    "▣", Color.rgb(0x9F, 0xD3, 0x5B), "opencode"),
            new AcpPreset(
                    "acp:dsh", "DeepSeek Harness (ACP, preview)",
                    List.of("npx", "-y", "@deepseek-ai/dsh", "--profile", "acp"), "npx",
                    "Configure the model key in DeepSeek Harness, then ask again.", NPX_HINT,
                    "◉", Color.rgb(0x4D, 0x6B, 0xFE), "dsh"));

    static List<AcpPreset> acpPresets() {
        return ACP_PRESETS;
    }

    static boolean isAcp(String provider) {
        return provider != null && provider.startsWith(ACP_PREFIX);
    }

    static String acpLabel(String provider) {
        for (AcpPreset preset : ACP_PRESETS) {
            if (preset.id().equals(provider)) {
                return preset.label();
            }
        }
        return ACP_CUSTOM.equals(provider) ? "Custom (ACP)" : provider;
    }

    /**
     * Resolves the preset for an {@code acp:*} provider id. The custom provider builds its command from
     * {@code camel.tui.ai.acp.command}, split on whitespace (no quoting support).
     *
     * @throws IllegalArgumentException for an unknown id, or the custom id without a configured command
     */
    AcpPreset acpPreset(String provider, TuiSettings settings) {
        for (AcpPreset preset : ACP_PRESETS) {
            if (preset.id().equals(provider)) {
                return preset;
            }
        }
        if (ACP_CUSTOM.equals(provider)) {
            String raw = settings.getAiAcpCommand();
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "No custom ACP command configured. Set camel.tui.ai.acp.command in F2 -> Settings.");
            }
            List<String> command = List.of(raw.trim().split("\\s+"));
            return new AcpPreset(
                    ACP_CUSTOM, "Custom (ACP)", command, command.get(0),
                    "Check the agent's own login instructions, then ask again.",
                    command.get(0) + " not found: check camel.tui.ai.acp.command.",
                    "●", Theme.ACCENT, null);
        }
        throw new IllegalArgumentException("Unknown ACP provider '" + provider + "'.");
    }

    /**
     * Where {@code executable} is found on {@code path}, with the {@code .cmd}/{@code .exe} suffix Windows needs; null
     * when absent. An absolute {@code executable} resolves to itself when it is executable.
     */
    static String resolveExecutable(String executable, String path) {
        Path direct = Path.of(executable);
        if (direct.isAbsolute()) {
            return firstExecutable(direct);
        }
        if (path == null) {
            return null;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            String found = firstExecutable(Path.of(dir).resolve(executable));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The candidate itself or its {@code .cmd}/{@code .exe} sibling, whichever is executable; null when none is. */
    private static String firstExecutable(Path candidate) {
        for (Path variant : List.of(candidate, Path.of(candidate + ".cmd"), Path.of(candidate + ".exe"))) {
            if (Files.isExecutable(variant)) {
                return variant.toString();
            }
        }
        return null;
    }

    /** Same, on the process PATH: what {@code ProcessBuilder} must be given so a Windows shim is actually launched. */
    static String resolveExecutable(String executable) {
        return resolveExecutable(executable, System.getenv("PATH"));
    }

    /** True when {@code executable} is an absolute path to an executable file or is found on the PATH. */
    static boolean isOnPath(String executable) {
        return resolveExecutable(executable) != null;
    }

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
        for (String provider : List.of("anthropic", "openai", "gemini", "ollama", "watsonx")) {
            if (!provider.equals(defaultProvider)) {
                choices.add(new AiProviderSwitchPopup.ProviderChoice(provider, "", "", false));
            }
        }
        for (AcpPreset preset : ACP_PRESETS) {
            if (!preset.id().equals(defaultProvider)) {
                choices.add(new AiProviderSwitchPopup.ProviderChoice(preset.id(), "", "", false));
            }
        }
        String custom = settings.getAiAcpCommand();
        if (custom != null && !custom.isBlank() && !ACP_CUSTOM.equals(defaultProvider)) {
            choices.add(new AiProviderSwitchPopup.ProviderChoice(ACP_CUSTOM, "", "", false));
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
        if (isAcp(provider)) {
            return;
        }
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
