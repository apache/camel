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

import java.net.InetSocketAddress;
import java.net.ProxySelector;

/**
 * User-facing preferences for the Camel TUI, persisted under {@code camel.tui.*} keys in the CLI user configuration
 * files. The object is loaded as a whole ({@link #load()}), mutated in memory, and written back ({@link #save()}),
 * giving every setting one place to live and future settings a natural home.
 * <p>
 * Persistence goes through {@link TuiUserConfig}, which applies per-key precedence between the local and global config
 * files: a key a project has deliberately committed to the local file is read from and written back to the local file,
 * while a user's personal preferences default to the global home file.
 */
final class TuiSettings {

    static final String PROP_THEME = "camel.tui.theme";
    static final String PROP_START_TAB = "camel.tui.startTab";
    static final String PROP_SELECT_TAB = "camel.tui.selectTab";
    static final String PROP_LOG_PIN = "camel.tui.logPin";
    static final String PROP_DEFAULT_FOLDER = "camel.tui.defaultFolder";
    static final String PROP_RATE_PER = "camel.tui.ratePer";
    static final String PROP_AI_PROVIDER = "camel.tui.ai.provider";
    static final String PROP_AI_MODEL = "camel.tui.ai.model";
    static final String PROP_AI_URL = "camel.tui.ai.url";
    static final String PROP_PROXY_HOST = "camel.tui.proxyHost";
    static final String PROP_PROXY_PORT = "camel.tui.proxyPort";
    static final String PROP_SHELL_HISTORY = "camel.tui.shell.history";
    static final String PROP_AI_PROMPT_HISTORY = "camel.tui.ai.promptHistory";

    private String themeId;
    private String startTab;
    private String selectTab;
    private String logPin;
    private String ratePer;
    private String defaultFolder;
    private String proxyHost;
    private String proxyPort;
    private String aiProvider;
    private String aiModel;
    private String aiUrl;
    private String shellHistory;
    private String aiPromptHistory;

    String getThemeId() {
        return themeId;
    }

    void setThemeId(String themeId) {
        this.themeId = themeId;
    }

    String getStartTab() {
        return startTab;
    }

    void setStartTab(String startTab) {
        this.startTab = startTab;
    }

    String getSelectTab() {
        return selectTab;
    }

    void setSelectTab(String selectTab) {
        this.selectTab = selectTab;
    }

    String getLogPin() {
        return logPin;
    }

    void setLogPin(String logPin) {
        this.logPin = logPin;
    }

    String getRatePer() {
        return ratePer;
    }

    void setRatePer(String ratePer) {
        this.ratePer = ratePer;
    }

    String getDefaultFolder() {
        return defaultFolder;
    }

    void setDefaultFolder(String defaultFolder) {
        this.defaultFolder = defaultFolder;
    }

    String getProxyHost() {
        return proxyHost;
    }

    void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    String getProxyPort() {
        return proxyPort;
    }

    void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    String getAiProvider() {
        return aiProvider;
    }

    void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    String getAiModel() {
        return aiModel;
    }

    void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    String getAiUrl() {
        return aiUrl;
    }

    void setAiUrl(String aiUrl) {
        this.aiUrl = aiUrl;
    }

    String getShellHistory() {
        return shellHistory;
    }

    void setShellHistory(String shellHistory) {
        this.shellHistory = shellHistory;
    }

    String getAiPromptHistory() {
        return aiPromptHistory;
    }

    void setAiPromptHistory(String aiPromptHistory) {
        this.aiPromptHistory = aiPromptHistory;
    }

    int getShellHistoryLimit() {
        return TuiHistoryLimits.resolveLimit(shellHistory);
    }

    int getAiPromptHistoryLimit() {
        return TuiHistoryLimits.resolveLimit(aiPromptHistory);
    }

    /**
     * Loads the current settings, resolving each key with per-key local/global precedence via {@link TuiUserConfig}.
     * Unset keys yield {@code null} fields; a read failure yields an object with {@code null} fields rather than
     * throwing, matching the best-effort behavior elsewhere in the TUI.
     */
    static TuiSettings load() {
        TuiSettings settings = new TuiSettings();
        try {
            settings.themeId = trimToNull(TuiUserConfig.read(PROP_THEME));
            settings.startTab = trimToNull(TuiUserConfig.read(PROP_START_TAB));
            settings.selectTab = trimToNull(TuiUserConfig.read(PROP_SELECT_TAB));
            settings.logPin = trimToNull(TuiUserConfig.read(PROP_LOG_PIN));
            settings.ratePer = trimToNull(TuiUserConfig.read(PROP_RATE_PER));
            settings.defaultFolder = trimToNull(TuiUserConfig.read(PROP_DEFAULT_FOLDER));
            settings.proxyHost = trimToNull(TuiUserConfig.read(PROP_PROXY_HOST));
            settings.proxyPort = trimToNull(TuiUserConfig.read(PROP_PROXY_PORT));
            settings.aiProvider = trimToNull(TuiUserConfig.read(PROP_AI_PROVIDER));
            settings.aiModel = trimToNull(TuiUserConfig.read(PROP_AI_MODEL));
            settings.aiUrl = trimToNull(TuiUserConfig.read(PROP_AI_URL));
            settings.shellHistory = trimToNull(TuiUserConfig.read(PROP_SHELL_HISTORY));
            settings.aiPromptHistory = trimToNull(TuiUserConfig.read(PROP_AI_PROMPT_HISTORY));
        } catch (RuntimeException e) {
            // best-effort: return an object with null fields on read failure
        }
        return settings;
    }

    /**
     * Writes every field back, routing each key to its home file (local when it is a project override, otherwise the
     * global file) via {@link TuiUserConfig}. A blank/{@code null} field removes its key so the file never carries
     * empty values. Failures are swallowed so a save hiccup never disrupts the TUI.
     */
    void save() {
        try {
            TuiUserConfig.write(PROP_THEME, themeId);
            TuiUserConfig.write(PROP_START_TAB, startTab);
            TuiUserConfig.write(PROP_SELECT_TAB, selectTab);
            TuiUserConfig.write(PROP_LOG_PIN, logPin);
            TuiUserConfig.write(PROP_RATE_PER, ratePer);
            TuiUserConfig.write(PROP_DEFAULT_FOLDER, defaultFolder);
            TuiUserConfig.write(PROP_PROXY_HOST, proxyHost);
            TuiUserConfig.write(PROP_PROXY_PORT, proxyPort);
            TuiUserConfig.write(PROP_AI_PROVIDER, aiProvider);
            TuiUserConfig.write(PROP_AI_MODEL, aiModel);
            TuiUserConfig.write(PROP_AI_URL, aiUrl);
            TuiUserConfig.write(PROP_SHELL_HISTORY, shellHistory);
            TuiUserConfig.write(PROP_AI_PROMPT_HISTORY, aiPromptHistory);
        } catch (RuntimeException e) {
            // best-effort: a save failure must not disrupt the TUI
        }
    }

    /**
     * Returns a {@link ProxySelector} based on the configured proxy settings, falling back to the JVM default.
     */
    static ProxySelector proxySelector() {
        TuiSettings s = load();
        String host = s.getProxyHost();
        if (host != null && !host.isEmpty()) {
            int port = 8080;
            String portStr = s.getProxyPort();
            if (portStr != null) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    // use default port
                }
            }
            return ProxySelector.of(new InetSocketAddress(host, port));
        }
        return ProxySelector.getDefault();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
