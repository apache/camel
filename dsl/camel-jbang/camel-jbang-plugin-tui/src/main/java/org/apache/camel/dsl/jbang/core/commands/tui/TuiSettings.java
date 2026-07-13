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
    static final String PROP_LOG_PIN = "camel.tui.logPin";
    static final String PROP_DEFAULT_FOLDER = "camel.tui.defaultFolder";
    static final String PROP_RATE_PER = "camel.tui.ratePer";
    static final String PROP_AI_PROVIDER = "camel.tui.ai.provider";
    static final String PROP_AI_MODEL = "camel.tui.ai.model";
    static final String PROP_AI_URL = "camel.tui.ai.url";

    private String themeId;
    private String startTab;
    private String logPin;
    private String ratePer;
    private String defaultFolder;
    private String aiProvider;
    private String aiModel;
    private String aiUrl;

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
            settings.logPin = trimToNull(TuiUserConfig.read(PROP_LOG_PIN));
            settings.ratePer = trimToNull(TuiUserConfig.read(PROP_RATE_PER));
            settings.defaultFolder = trimToNull(TuiUserConfig.read(PROP_DEFAULT_FOLDER));
            settings.aiProvider = trimToNull(TuiUserConfig.read(PROP_AI_PROVIDER));
            settings.aiModel = trimToNull(TuiUserConfig.read(PROP_AI_MODEL));
            settings.aiUrl = trimToNull(TuiUserConfig.read(PROP_AI_URL));
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
            TuiUserConfig.write(PROP_LOG_PIN, logPin);
            TuiUserConfig.write(PROP_RATE_PER, ratePer);
            TuiUserConfig.write(PROP_DEFAULT_FOLDER, defaultFolder);
            TuiUserConfig.write(PROP_AI_PROVIDER, aiProvider);
            TuiUserConfig.write(PROP_AI_MODEL, aiModel);
            TuiUserConfig.write(PROP_AI_URL, aiUrl);
        } catch (RuntimeException e) {
            // best-effort: a save failure must not disrupt the TUI
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
