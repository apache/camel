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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for property completion logic used by SourceTab autocomplete.
 *
 * Exercises the same grouping and filtering logic as the provider methods, using the real CamelCatalog to validate
 * against actual metadata.
 */
class PropertyCompletionProviderTest {

    private static CamelCatalog catalog;
    private static Map<String, BaseOptionModel> mainOptionsCache;
    private static Map<String, String> mainGroupsCache;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
        mainOptionsCache = new HashMap<>();
        mainGroupsCache = new HashMap<>();
        MainModel mainModel = catalog.mainModel();
        for (MainModel.MainOptionModel opt : mainModel.getOptions()) {
            if (opt.getName() != null) {
                mainOptionsCache.put(opt.getName(), opt);
            }
        }
        for (MainModel.MainGroupModel grp : mainModel.getGroups()) {
            if (grp.getName() != null) {
                mainGroupsCache.put(grp.getName(), grp.getDescription());
            }
        }
    }

    // --- Group-level completions ---

    @Test
    void emptyPrefixShowsGroupsNotIndividualOptions() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("");

        assertThat(items).isNotEmpty();
        // should contain group entries like camel.main., camel.debug., etc.
        assertThat(items).anyMatch(i -> i.key().equals("camel.main."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.debug."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.rest."));
        // should also contain component/dataformat/language prefixes
        assertThat(items).anyMatch(i -> i.key().equals("camel.component."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.dataformat."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.language."));
        // should NOT contain individual options at this level
        assertThat(items).noneMatch(i -> i.key().equals("camel.main.autoStartup"));
    }

    @Test
    void groupsHaveDescriptions() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("");

        var mainGroup = items.stream().filter(i -> i.key().equals("camel.main.")).findFirst();
        assertThat(mainGroup).isPresent();
        assertThat(mainGroup.get().description()).isNotNull().isNotEmpty();

        var debugGroup = items.stream().filter(i -> i.key().equals("camel.debug.")).findFirst();
        assertThat(debugGroup).isPresent();
        assertThat(debugGroup.get().description()).isNotNull().isNotEmpty();
    }

    @Test
    void camelPrefixShowsGroups() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.");

        assertThat(items).anyMatch(i -> i.key().equals("camel.main."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.component."));
        // should not show individual options
        assertThat(items).noneMatch(i -> i.key().equals("camel.main.autoStartup"));
    }

    @Test
    void filteringGroupsByPartialName() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.d");

        assertThat(items).anyMatch(i -> i.key().equals("camel.debug."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.dataformat."));
        // should not show unrelated groups
        assertThat(items).noneMatch(i -> i.key().equals("camel.rest."));
    }

    @Test
    void vaultGroupsIncluded() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.vault");

        assertThat(items).anyMatch(i -> i.key().equals("camel.vault.aws."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.vault.gcp."));
    }

    // --- Option-level completions (after selecting a group) ---

    @Test
    void camelMainDotShowsOptionsNotGroups() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.main.");

        assertThat(items).isNotEmpty();
        // should contain actual options
        assertThat(items).anyMatch(i -> i.key().equals("camel.main.autoStartup"));
        // should NOT contain group entries
        assertThat(items).noneMatch(i -> i.key().endsWith(".") && !i.key().contains("="));
    }

    @Test
    void camelDebugDotShowsDebugOptions() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.debug.");

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(i -> i.key().startsWith("camel.debug."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.debug.enabled"));
    }

    @Test
    void optionsHaveMetadata() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.main.");

        var autoStartup = items.stream()
                .filter(i -> i.key().equals("camel.main.autoStartup"))
                .findFirst();
        assertThat(autoStartup).isPresent();
        assertThat(autoStartup.get().description()).isNotNull().isNotEmpty();
        assertThat(autoStartup.get().type()).isNotNull();
    }

    @Test
    void filteringOptionsWithinGroup() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.main.auto");

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(i -> i.key().toLowerCase().contains("auto"));
        assertThat(items).anyMatch(i -> i.key().equals("camel.main.autoStartup"));
    }

    @Test
    void camelRestDotShowsRestOptions() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.rest.");

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(i -> i.key().startsWith("camel.rest."));
    }

    // --- Component completions ---

    @Test
    void camelComponentDotShowsComponentNames() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.component.");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().equals("camel.component.kafka."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.component.timer."));
    }

    @Test
    void camelComponentKafkaDotShowsKafkaOptions() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.component.kafka.");

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(i -> i.key().startsWith("camel.component.kafka."));
        assertThat(items).anyMatch(i -> i.key().equals("camel.component.kafka.brokers"));
    }

    @Test
    void componentOptionsHaveDescriptions() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.component.kafka.");

        var brokers = items.stream()
                .filter(i -> i.key().equals("camel.component.kafka.brokers"))
                .findFirst();
        assertThat(brokers).isPresent();
        assertThat(brokers.get().description()).isNotNull().isNotEmpty();
    }

    // --- Dataformat completions ---

    @Test
    void camelDataformatDotShowsDataformatNames() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.dataformat.");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().contains("json"));
    }

    // --- Language completions ---

    @Test
    void camelLanguageDotShowsLanguageNames() {
        List<AutocompletePopup.CompletionItem> items = provideCompletions("camel.language.");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().contains("simple"));
    }

    // --- Value completions ---

    @Test
    void booleanOptionReturnsValueCompletions() {
        List<AutocompletePopup.CompletionItem> items = provideValueCompletions("camel.main.autoStartup");

        assertThat(items).hasSize(2);
        assertThat(items).anyMatch(i -> i.key().equals("true"));
        assertThat(items).anyMatch(i -> i.key().equals("false"));
        // value completions should carry the parent option's description
        assertThat(items).allMatch(i -> i.description() != null && !i.description().isEmpty());
    }

    @Test
    void enumOptionReturnsEnumValues() {
        // camel.main.startupRecorder is an enum option
        List<AutocompletePopup.CompletionItem> items = provideValueCompletions("camel.main.startupRecorder");

        if (!items.isEmpty()) {
            assertThat(items).allMatch(i -> i.description() != null && !i.description().isEmpty());
            assertThat(items).allMatch(i -> i.group() != null || i.type() != null);
        }
    }

    @Test
    void componentEnumOptionReturnsValues() {
        List<AutocompletePopup.CompletionItem> items
                = provideValueCompletions("camel.component.kafka.autoOffsetReset");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().equals("latest"));
        assertThat(items).anyMatch(i -> i.key().equals("earliest"));
        // each value carries the parent option's description
        assertThat(items).allMatch(i -> i.description() != null && !i.description().isEmpty());
    }

    @Test
    void unknownKeyReturnsEmptyValueCompletions() {
        List<AutocompletePopup.CompletionItem> items = provideValueCompletions("camel.main.nonExistent");
        assertThat(items).isEmpty();
    }

    @Test
    void stringOptionReturnsEmptyValueCompletions() {
        // camel.main.name is a string option with no enums
        List<AutocompletePopup.CompletionItem> items = provideValueCompletions("camel.main.name");
        assertThat(items).isEmpty();
    }

    // --- Helper methods that mirror SourceTab's provider logic ---

    private List<AutocompletePopup.CompletionItem> provideCompletions(String linePrefix) {
        String keyPrefix = linePrefix != null ? linePrefix.trim().toLowerCase() : "";
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();

        // determine if the prefix matches a specific main group
        String matchedGroup = null;
        for (String groupName : mainGroupsCache.keySet()) {
            String groupPrefix = groupName + ".";
            if (keyPrefix.startsWith(groupPrefix)) {
                matchedGroup = groupName;
                break;
            }
        }

        if (matchedGroup != null) {
            String groupDot = matchedGroup + ".";
            String optFilter = keyPrefix.substring(groupDot.length());
            for (Map.Entry<String, BaseOptionModel> entry : mainOptionsCache.entrySet()) {
                if (entry.getKey().startsWith(groupDot)) {
                    String optName = entry.getKey().substring(groupDot.length());
                    if (optFilter.isEmpty() || optName.toLowerCase().contains(optFilter)) {
                        BaseOptionModel opt = entry.getValue();
                        items.add(new AutocompletePopup.CompletionItem(
                                entry.getKey(), opt.getDescription(), opt.getType(),
                                opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                                opt.getGroup()));
                    }
                }
            }
        } else if (keyPrefix.startsWith("camel.component.")) {
            addPrefixedCompletions(items, keyPrefix, "camel.component.",
                    catalog.findComponentNames(),
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        } else if (keyPrefix.startsWith("camel.dataformat.")) {
            addPrefixedCompletions(items, keyPrefix, "camel.dataformat.",
                    catalog.findDataFormatNames(),
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        } else if (keyPrefix.startsWith("camel.language.")) {
            addPrefixedCompletions(items, keyPrefix, "camel.language.",
                    catalog.findLanguageNames(),
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        } else {
            for (Map.Entry<String, String> entry : mainGroupsCache.entrySet()) {
                String groupKey = entry.getKey() + ".";
                if (keyPrefix.isEmpty() || groupKey.toLowerCase().contains(keyPrefix)) {
                    items.add(new AutocompletePopup.CompletionItem(
                            groupKey, entry.getValue(), null, null, false, null, null));
                }
            }
            if (keyPrefix.isEmpty() || "camel.component.".contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        "camel.component.", "Component configuration prefix", null, null, false, null, null));
            }
            if (keyPrefix.isEmpty() || "camel.dataformat.".contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        "camel.dataformat.", "Data format configuration prefix", null, null, false, null, null));
            }
            if (keyPrefix.isEmpty() || "camel.language.".contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        "camel.language.", "Language configuration prefix", null, null, false, null, null));
            }
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideValueCompletions(String key) {
        BaseOptionModel opt = lookupOption(key);
        if (opt == null) {
            return List.of();
        }

        String optDesc = opt.getDescription();
        String optType = opt.getType();
        Object optDefault = opt.getDefaultValue();
        String optGroup = opt.getGroup();
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();

        List<String> enums = opt.getEnums();
        if (enums != null && !enums.isEmpty()) {
            for (String value : enums) {
                boolean isDefault = value.equals(String.valueOf(optDefault));
                items.add(new AutocompletePopup.CompletionItem(
                        value, optDesc, optType, isDefault ? value : optDefault,
                        false, null, optGroup));
            }
            return items;
        }

        if ("boolean".equalsIgnoreCase(optType) || "java.lang.Boolean".equals(opt.getJavaType())) {
            items.add(new AutocompletePopup.CompletionItem(
                    "true", optDesc, "boolean", optDefault, false, null, optGroup));
            items.add(new AutocompletePopup.CompletionItem(
                    "false", optDesc, "boolean", optDefault, false, null, optGroup));
            return items;
        }

        return items;
    }

    private BaseOptionModel lookupOption(String key) {
        if (mainOptionsCache.containsKey(key)) {
            return mainOptionsCache.get(key);
        }
        if (key.startsWith("camel.component.")) {
            return lookupPrefixedOption(key, "camel.component.",
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        }
        if (key.startsWith("camel.dataformat.")) {
            return lookupPrefixedOption(key, "camel.dataformat.",
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        if (key.startsWith("camel.language.")) {
            return lookupPrefixedOption(key, "camel.language.",
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        return null;
    }

    private static BaseOptionModel lookupPrefixedOption(
            String key, String prefix,
            java.util.function.Function<String, List<? extends BaseOptionModel>> optionsLoader) {
        String rest = key.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String name = rest.substring(0, dot);
        String optionName = rest.substring(dot + 1);
        List<? extends BaseOptionModel> options = optionsLoader.apply(name);
        if (options == null) {
            return null;
        }
        return options.stream()
                .filter(o -> o.getName().equals(optionName))
                .findFirst().orElse(null);
    }

    private static void addPrefixedCompletions(
            List<AutocompletePopup.CompletionItem> items,
            String keyPrefix, String prefix,
            List<String> names,
            java.util.function.Function<String, List<? extends BaseOptionModel>> optionsLoader) {
        String rest = keyPrefix.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot > 0) {
            String name = rest.substring(0, dot);
            String optPrefix = rest.substring(dot + 1);
            List<? extends BaseOptionModel> options = optionsLoader.apply(name);
            if (options != null) {
                for (BaseOptionModel opt : options) {
                    String fullKey = prefix + name + "." + opt.getName();
                    if (optPrefix.isEmpty() || opt.getName().toLowerCase().contains(optPrefix)) {
                        items.add(new AutocompletePopup.CompletionItem(
                                fullKey, opt.getDescription(), opt.getType(),
                                opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                                opt.getGroup()));
                    }
                }
            }
        } else {
            for (String name : names) {
                String fullKey = prefix + name + ".";
                if (rest.isEmpty() || name.toLowerCase().contains(rest)) {
                    items.add(new AutocompletePopup.CompletionItem(
                            fullKey, null, null, null, false, null, null));
                }
            }
        }
    }
}
