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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Editor assistance for the Source tab: autocomplete, quick-doc, validation and deprecation providers for Camel YAML
 * DSL files and application properties, backed by the Camel catalog, the YAML DSL completion tree and Spring Boot
 * configuration metadata. {@link SourceTab} wires the provider methods into {@link SourceViewer}.
 */
final class SourceEditAssist {

    private final MonitorContext ctx;
    private final CatalogCache catalogCache = new CatalogCache();
    private Path rootDir;

    // ---- YAML DSL completion ----

    private static final Set<String> TREE_BOILERPLATE = Set.of("id", "note", "description", "disabled");

    // ---- Property placeholder loading ----

    private List<AutocompletePopup.CompletionItem> placeholderCache;
    private long placeholderCacheTime;
    private Path placeholderCacheDir;

    private static final Set<String> PREDICATE_EIPS = Set.of(
            "filter", "when", "validate", "onWhen", "on-when",
            "handled", "continued", "retryWhile", "retry-while",
            "completionPredicate", "completion-predicate",
            "completion", "loopDoWhile", "loop-do-while");

    // Properties quick-doc caches (invalidated when catalog version changes)
    private String propsCatalogVersion;
    private Map<String, BaseOptionModel> mainOptionsCache;
    private Map<String, String> mainGroupsCache;
    private final Map<String, Map<String, BaseOptionModel>> componentOptionsCache = new HashMap<>();
    private final Map<String, Map<String, BaseOptionModel>> languageOptionsCache = new HashMap<>();
    private final Map<String, Map<String, BaseOptionModel>> dataformatOptionsCache = new HashMap<>();

    // Component name completion cache (keyed by catalog version)
    private String componentsCatalogVersion;
    private List<AutocompletePopup.CompletionItem> consumerComponents;
    private List<AutocompletePopup.CompletionItem> producerComponents;

    // YAML DSL completion tree (loaded from generated schema)
    private JsonObject completionTree;
    private boolean completionTreeLoaded;

    // Spring Boot configuration metadata cache (lazy-loaded on-demand via IPC or from local JARs)
    private Map<String, JsonObject> springBootMetadataCache;
    private boolean springBootMetadataLoaded;
    private Map<String, BaseOptionModel> springBootOptionsCache;
    private Map<String, String> springBootGroupsCache;
    private Map<String, List<String>> springBootHintsCache;
    private java.util.concurrent.CompletableFuture<SpringBootMetadataResolver.MetadataResult> springBootMetadataFuture;

    private static final Pattern YAML_URI_PATTERN = Pattern.compile(
            "^\\s*-?\\s*(?:uri|from|to|toD|wireTap|enrich|pollEnrich|deadLetterChannel):\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*(?::[^\"\\s]*)?)");
    private static final Pattern YAML_KEY_PATTERN = Pattern.compile(
            "^\\s*-?\\s*([a-zA-Z][a-zA-Z0-9]*)\\s*:");

    SourceEditAssist(MonitorContext ctx) {
        this.ctx = ctx;
    }

    /**
     * The project root directory of the selected integration, used to resolve property placeholder files.
     */
    void setRootDir(Path rootDir) {
        this.rootDir = rootDir;
    }

    /**
     * Drops the per-integration completion tree so it is reloaded for the next selected integration.
     */
    void reset() {
        completionTreeLoaded = false;
        completionTree = null;
    }

    CamelCatalog getCatalog() {
        return catalogCache.get(ctx.findSelectedIntegration());
    }

    Map<Integer, List<SourceViewer.DocEntry>> provideCamelQuickDocs(List<JsonObject> codeData) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || codeData.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<SourceViewer.DocEntry>> result = new LinkedHashMap<>();
        for (int i = 0; i < codeData.size(); i++) {
            String code = codeData.get(i).getString("code");
            if (code == null) {
                continue;
            }

            Matcher uriMatcher = YAML_URI_PATTERN.matcher(code);
            if (uriMatcher.find()) {
                String uri = uriMatcher.group(1);
                if (uri.endsWith("\"")) {
                    uri = uri.substring(0, uri.length() - 1);
                }
                EipDocSupport.buildEndpointInlineDoc(result, codeData, catalog, uri, i);
                continue;
            }

            Matcher keyMatcher = YAML_KEY_PATTERN.matcher(code);
            if (keyMatcher.find()) {
                String key = keyMatcher.group(1);
                if (catalog.eipModel(key) != null) {
                    EipDocSupport.buildEipInlineDoc(result, codeData, catalog, key, null, i);
                }
            }
        }
        return result;
    }

    List<SourceViewer.DocEntry> provideEditQuickDoc(List<String> lines, int cursorRow) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || lines == null || cursorRow < 0 || cursorRow >= lines.size()) {
            return List.of();
        }
        String line = lines.get(cursorRow);

        Matcher uriMatcher = YAML_URI_PATTERN.matcher(line);
        if (uriMatcher.find()) {
            String uri = uriMatcher.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            String component = uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : uri;
            ComponentModel model = catalog.componentModel(component);
            if (model != null) {
                String title = model.getTitle() != null ? model.getTitle() : component;
                String desc = model.getDescription() != null ? model.getDescription() : "";
                return List.of(SourceViewer.DocEntry.of(title + " — " + desc));
            }
        }

        // check if inside a parameters: block — look up component endpoint option doc
        SourceViewer.DocEntry optionDoc = resolveParameterOptionDoc(catalog, lines, cursorRow);
        if (optionDoc != null) {
            return List.of(optionDoc);
        }

        // check if this is an EIP option (e.g., message under log, expression under split)
        SourceViewer.DocEntry eipOptionDoc = resolveEipOptionDoc(catalog, lines, cursorRow);
        if (eipOptionDoc != null) {
            return List.of(eipOptionDoc);
        }

        Matcher keyMatcher = YAML_KEY_PATTERN.matcher(line);
        if (keyMatcher.find()) {
            String key = keyMatcher.group(1);
            EipModel eipModel = catalog.eipModel(key);
            if (eipModel != null) {
                String title = eipModel.getTitle() != null ? eipModel.getTitle() : key;
                String desc = eipModel.getDescription() != null ? eipModel.getDescription() : "";
                return List.of(SourceViewer.DocEntry.of(title + " — " + desc));
            }
        }

        return List.of();
    }

    SourceViewer.DocEntry resolveEipOptionDoc(CamelCatalog catalog, List<String> lines, int cursorRow) {
        String cursorLine = lines.get(cursorRow);
        String trimmed = cursorLine.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        String optionName = trimmed.substring(0, colonIdx).trim();
        int cursorIndent = countLeadingSpaces(cursorLine);

        // walk up to find the parent EIP
        for (int i = cursorRow - 1; i >= 0; i--) {
            String l = lines.get(i);
            if (l.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(l);
            if (indent < cursorIndent) {
                String t = l.trim();
                if (t.startsWith("- ")) {
                    t = t.substring(2).trim();
                }
                int ci = t.indexOf(':');
                if (ci > 0) {
                    String eipName = t.substring(0, ci).trim();
                    EipModel model = catalog.eipModel(eipName);
                    if (model != null) {
                        for (BaseOptionModel opt : model.getOptions()) {
                            if (optionName.equals(opt.getName())) {
                                String desc = formatFullOptionDoc(opt);
                                return desc != null
                                        ? SourceViewer.DocEntry.withTitle(formatOptionTitle(opt), desc)
                                        : null;
                            }
                        }
                    }
                }
                break;
            }
        }
        return null;
    }

    SourceViewer.DocEntry resolveParameterOptionDoc(CamelCatalog catalog, List<String> lines, int cursorRow) {
        String cursorLine = lines.get(cursorRow);
        String trimmed = cursorLine.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
            return null;
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        String optionName = trimmed.substring(0, colonIdx).trim();
        int cursorIndent = countLeadingSpaces(cursorLine);

        // walk up to find parameters: and then the component URI
        boolean foundParameters = false;
        int parametersIndent = -1;
        for (int i = cursorRow - 1; i >= 0; i--) {
            String l = lines.get(i);
            if (l.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(l);
            if (indent < cursorIndent && !foundParameters) {
                String t = l.trim();
                if (t.startsWith("- ")) {
                    t = t.substring(2).trim();
                }
                if (t.equals("parameters:")) {
                    foundParameters = true;
                    parametersIndent = indent;
                    continue;
                }
                break;
            }
            if (foundParameters && indent <= parametersIndent) {
                // look for uri: line at same or lower indent
                String t = l.trim();
                if (t.startsWith("- ")) {
                    t = t.substring(2).trim();
                }
                Matcher m = YAML_URI_PATTERN.matcher(l);
                if (m.find()) {
                    String uri = m.group(1);
                    if (uri.endsWith("\"")) {
                        uri = uri.substring(0, uri.length() - 1);
                    }
                    String comp = uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : uri;
                    ComponentModel model = catalog.componentModel(comp);
                    if (model != null) {
                        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
                            if (optionName.equals(opt.getName())) {
                                String desc = formatFullOptionDoc(opt);
                                return desc != null
                                        ? SourceViewer.DocEntry.withTitle(formatOptionTitle(opt), desc)
                                        : null;
                            }
                        }
                    }
                    break;
                }
                if (indent < parametersIndent) {
                    break;
                }
            }
        }
        return null;
    }

    static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    List<SourceViewer.DocEntry> provideEditPropertyQuickDoc(List<String> lines, int cursorRow) {
        if (lines == null || cursorRow < 0 || cursorRow >= lines.size()) {
            return List.of();
        }
        String line = lines.get(cursorRow);
        if (line == null) {
            return List.of();
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return List.of();
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return List.of();
        }
        String key = trimmed.substring(0, eq).trim();

        CamelCatalog catalog = getCatalog();
        if (catalog != null) {
            ensureMainOptionsCache(catalog);
            BaseOptionModel opt = lookupPropertyOption(catalog, key);
            if (opt != null) {
                String desc = formatFullOptionDoc(opt);
                if (desc != null) {
                    String title = formatOptionTitle(opt);
                    return List.of(opt.isDeprecated()
                            ? SourceViewer.DocEntry.deprecated(desc)
                            : SourceViewer.DocEntry.withTitle(title, desc));
                }
            }
        }

        ensureSpringBootMetadataCache();
        if (springBootMetadataCache != null) {
            JsonObject sbProp = springBootMetadataCache.get(key);
            if (sbProp != null) {
                String doc = SpringBootMetadataHelper.formatDoc(sbProp);
                if (doc != null) {
                    boolean deprecated = Boolean.TRUE.equals(sbProp.get("deprecated"));
                    return List.of(deprecated
                            ? SourceViewer.DocEntry.deprecated(doc)
                            : SourceViewer.DocEntry.of(doc));
                }
            }
        }
        return List.of();
    }

    static boolean isPropertiesFile(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".properties");
    }

    static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    List<AutocompletePopup.CompletionItem> providePropertyCompletions(String linePrefix) {
        CamelCatalog catalog = getCatalog();
        if (catalog != null) {
            ensureMainOptionsCache(catalog);
        }
        ensureSpringBootMetadataCache();
        if (catalog == null && (springBootOptionsCache == null || springBootOptionsCache.isEmpty())) {
            return List.of();
        }

        String keyPrefix = linePrefix != null ? linePrefix.trim().toLowerCase() : "";

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();

        // determine if the prefix matches a specific main group (e.g., camel.main.)
        String matchedGroup = null;
        if (mainGroupsCache != null) {
            for (String groupName : mainGroupsCache.keySet()) {
                String groupPrefix = groupName + ".";
                if (keyPrefix.startsWith(groupPrefix)) {
                    matchedGroup = groupName;
                    break;
                }
            }
        }

        if (matchedGroup != null) {
            // show options within the matched group
            String groupDot = matchedGroup + ".";
            String optFilter = keyPrefix.substring(groupDot.length());
            if (mainOptionsCache != null) {
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
            }
        } else if (keyPrefix.startsWith("camel.component.")) {
            // camel.component.<name>. options
            addPrefixedCompletions(items, catalog, keyPrefix, "camel.component.",
                    catalog.findComponentNames(),
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        } else if (keyPrefix.startsWith("camel.dataformat.")) {
            // camel.dataformat.<name>. options
            addPrefixedCompletions(items, catalog, keyPrefix, "camel.dataformat.",
                    catalog.findDataFormatNames(),
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        } else if (keyPrefix.startsWith("camel.language.")) {
            // camel.language.<name>. options
            addPrefixedCompletions(items, catalog, keyPrefix, "camel.language.",
                    catalog.findLanguageNames(),
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        } else if (!keyPrefix.isEmpty() && !keyPrefix.startsWith("camel.")
                && springBootOptionsCache != null) {
            // Spring Boot property completions (e.g., server., spring.datasource.)
            addSpringBootCompletions(items, keyPrefix);
        } else {
            // show group-level entries
            if (mainGroupsCache != null) {
                for (Map.Entry<String, String> entry : mainGroupsCache.entrySet()) {
                    String groupKey = entry.getKey() + ".";
                    if (keyPrefix.isEmpty() || groupKey.toLowerCase().contains(keyPrefix)) {
                        items.add(new AutocompletePopup.CompletionItem(
                                groupKey, entry.getValue(), null, null, false, null, null));
                    }
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
            // Spring Boot top-level groups
            addSpringBootTopLevelGroups(items, keyPrefix);
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> {
                    boolean aGroup = a.key().endsWith(".");
                    boolean bGroup = b.key().endsWith(".");
                    if (aGroup != bGroup) {
                        return aGroup ? -1 : 1;
                    }
                    return String.CASE_INSENSITIVE_ORDER.compare(a.key(), b.key());
                }));

        return items;
    }

    void addPrefixedCompletions(
            List<AutocompletePopup.CompletionItem> items,
            CamelCatalog catalog, String keyPrefix, String prefix,
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
                            fullKey, capitalize(prefix.split("\\.")[1]) + ": " + name,
                            null, null, false, null, null));
                }
            }
        }
    }

    void addSpringBootCompletions(List<AutocompletePopup.CompletionItem> items, String keyPrefix) {
        // collect next-level sub-groups under this prefix
        Set<String> subGroups = new java.util.TreeSet<>();
        List<Map.Entry<String, BaseOptionModel>> directOptions = new ArrayList<>();

        for (Map.Entry<String, BaseOptionModel> entry : springBootOptionsCache.entrySet()) {
            String key = entry.getKey();
            if (!key.toLowerCase().startsWith(keyPrefix)) {
                continue;
            }
            String rest = key.substring(keyPrefix.length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                // has sub-group: e.g. "aop.auto" under "spring." → sub-group "aop"
                subGroups.add(keyPrefix + rest.substring(0, dot));
            } else {
                // direct property at this level
                directOptions.add(entry);
            }
        }

        if (subGroups.size() > 1 || (!subGroups.isEmpty() && !directOptions.isEmpty())) {
            // show sub-groups as drill-down entries
            for (String group : subGroups) {
                String groupKey = group + ".";
                items.add(new AutocompletePopup.CompletionItem(
                        groupKey, "Spring Boot configuration", null, null, false, null, null));
            }
            // also show any direct properties at this level
            for (Map.Entry<String, BaseOptionModel> entry : directOptions) {
                BaseOptionModel opt = entry.getValue();
                items.add(new AutocompletePopup.CompletionItem(
                        entry.getKey(), opt.getDescription(), opt.getType(),
                        opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                        "Spring Boot"));
            }
        } else {
            // single sub-group or leaf level: show all matching properties
            for (Map.Entry<String, BaseOptionModel> entry : springBootOptionsCache.entrySet()) {
                if (entry.getKey().toLowerCase().startsWith(keyPrefix)) {
                    BaseOptionModel opt = entry.getValue();
                    items.add(new AutocompletePopup.CompletionItem(
                            entry.getKey(), opt.getDescription(), opt.getType(),
                            opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                            "Spring Boot"));
                }
            }
        }
    }

    void addSpringBootTopLevelGroups(
            List<AutocompletePopup.CompletionItem> items, String keyPrefix) {
        if (springBootGroupsCache == null || springBootGroupsCache.isEmpty()) {
            return;
        }
        Set<String> topLevelGroups = new java.util.TreeSet<>();
        for (String group : springBootGroupsCache.keySet()) {
            int dot = group.indexOf('.');
            String topLevel = dot > 0 ? group.substring(0, dot) : group;
            topLevelGroups.add(topLevel);
        }
        for (String group : topLevelGroups) {
            String groupKey = group + ".";
            if (keyPrefix.isEmpty() || groupKey.toLowerCase().contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        groupKey, "Spring Boot configuration", null, null, false, null, null));
            }
        }
    }

    List<AutocompletePopup.CompletionItem> providePropertyValueCompletions(String key) {
        CamelCatalog catalog = getCatalog();
        if (key == null || key.isEmpty()) {
            return loadPropertyPlaceholders();
        }
        if (catalog != null) {
            ensureMainOptionsCache(catalog);
        }
        ensureSpringBootMetadataCache();

        BaseOptionModel opt = lookupOption(catalog, key);
        if (opt == null) {
            // check Spring Boot hints even without a matching option model
            List<AutocompletePopup.CompletionItem> hintItems = lookupSpringBootHints(key);
            if (!hintItems.isEmpty()) {
                hintItems.addAll(loadPropertyPlaceholders());
                return hintItems;
            }
            return loadPropertyPlaceholders();
        }

        String optDesc = opt.getDescription();
        String optType = opt.getType();
        Object optDefault = opt.getDefaultValue();
        String optGroup = opt.getGroup();

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        java.util.function.Predicate<String> valueFilter = null;

        // enum values
        List<String> enums = opt.getEnums();
        if (enums != null && !enums.isEmpty()) {
            java.util.Set<String> validValues = new java.util.HashSet<>();
            for (String value : enums) {
                validValues.add(value.toLowerCase());
                boolean isDefault = value.equals(String.valueOf(optDefault));
                items.add(new AutocompletePopup.CompletionItem(
                        value, optDesc, optType, isDefault ? value : optDefault,
                        false, null, optGroup));
            }
            valueFilter = v -> validValues.contains(v.toLowerCase());
        } else if ("boolean".equalsIgnoreCase(optType) || "java.lang.Boolean".equals(opt.getJavaType())) {
            valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
            items.add(new AutocompletePopup.CompletionItem(
                    "true", optDesc, "boolean", optDefault, false, null, optGroup));
            items.add(new AutocompletePopup.CompletionItem(
                    "false", optDesc, "boolean", optDefault, false, null, optGroup));
        } else if (isNumericType(optType, opt.getJavaType())) {
            valueFilter = SourceEditAssist::isNumericValue;
        }

        // Spring Boot hints for values without enum metadata
        if (items.isEmpty()) {
            items.addAll(lookupSpringBootHints(key));
        }

        // only include placeholders whose actual value is compatible with the option type
        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    List<AutocompletePopup.CompletionItem> lookupSpringBootHints(String key) {
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        if (springBootHintsCache != null) {
            List<String> hintValues = springBootHintsCache.get(key);
            if (hintValues != null) {
                for (String value : hintValues) {
                    items.add(new AutocompletePopup.CompletionItem(
                            value, null, null, null, false, null, "Spring Boot"));
                }
            }
        }
        return items;
    }

    BaseOptionModel lookupOption(CamelCatalog catalog, String key) {
        // camel.main.* options
        if (mainOptionsCache != null && mainOptionsCache.containsKey(key)) {
            return mainOptionsCache.get(key);
        }

        if (catalog == null) {
            // no Camel catalog — only Spring Boot options available
            if (springBootOptionsCache != null && springBootOptionsCache.containsKey(key)) {
                return springBootOptionsCache.get(key);
            }
            return null;
        }

        // camel.component.<name>.<option>
        if (key.startsWith("camel.component.")) {
            return lookupPrefixedOption(catalog, key, "camel.component.",
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        }
        // camel.dataformat.<name>.<option>
        if (key.startsWith("camel.dataformat.")) {
            return lookupPrefixedOption(catalog, key, "camel.dataformat.",
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        // camel.language.<name>.<option>
        if (key.startsWith("camel.language.")) {
            return lookupPrefixedOption(catalog, key, "camel.language.",
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        // Spring Boot options
        if (springBootOptionsCache != null && springBootOptionsCache.containsKey(key)) {
            return springBootOptionsCache.get(key);
        }
        return null;
    }

    BaseOptionModel lookupPrefixedOption(
            CamelCatalog catalog, String key, String prefix,
            java.util.function.Function<String, List<? extends BaseOptionModel>> optionsLoader) {
        String rest = key.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot > 0) {
            String name = rest.substring(0, dot);
            String optName = rest.substring(dot + 1);
            List<? extends BaseOptionModel> options = optionsLoader.apply(name);
            if (options != null) {
                for (BaseOptionModel opt : options) {
                    if (opt.getName().equals(optName)) {
                        return opt;
                    }
                }
            }
        }
        return null;
    }

    BaseOptionModel lookupPrefixedOption(
            String key, String prefix,
            Map<String, Map<String, BaseOptionModel>> cache,
            java.util.function.Function<String, List<? extends BaseOptionModel>> optionsLoader) {
        String rest = key.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String name = rest.substring(0, dot);
        String optionName = rest.substring(dot + 1);
        Map<String, BaseOptionModel> opts = cache.computeIfAbsent(name, n -> {
            List<? extends BaseOptionModel> options = optionsLoader.apply(n);
            if (options == null) {
                return Map.of();
            }
            Map<String, BaseOptionModel> map = new HashMap<>();
            for (BaseOptionModel o : options) {
                if (o.getName() != null) {
                    map.put(o.getName(), o);
                }
            }
            return map;
        });
        return opts.get(optionName);
    }

    static boolean isNumericType(String type, String javaType) {
        if (type != null) {
            switch (type.toLowerCase()) {
                case "integer":
                case "int":
                case "long":
                case "short":
                case "byte":
                case "float":
                case "double":
                case "number":
                    return true;
            }
        }
        if (javaType != null) {
            switch (javaType) {
                case "int":
                case "long":
                case "short":
                case "byte":
                case "float":
                case "double":
                case "java.lang.Integer":
                case "java.lang.Long":
                case "java.lang.Short":
                case "java.lang.Byte":
                case "java.lang.Float":
                case "java.lang.Double":
                    return true;
            }
        }
        return false;
    }

    static boolean isNumericValue(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    List<AutocompletePopup.CompletionItem> provideYamlKeyCompletions(String context) {
        if (context == null) {
            return List.of();
        }

        // component name completion on uri: lines
        if (context.startsWith("yaml-uri:")) {
            return provideComponentNameCompletions(context.substring(9));
        }

        // tree-driven YAML DSL completion (EIPs, expressions, languages, data formats, route options, top-level)
        if (context.startsWith("yaml-tree:")) {
            return provideTreeCompletions(context.substring(10));
        }

        if (!context.startsWith("yaml:")) {
            return List.of();
        }
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        // context format: "yaml:componentName:consumer|producer[:existingKey1,existingKey2,...][|uri]"
        String contextBody = context.substring(5);
        String uri = null;
        int pipeIdx = contextBody.indexOf('|');
        if (pipeIdx >= 0) {
            uri = contextBody.substring(pipeIdx + 1);
            contextBody = contextBody.substring(0, pipeIdx);
        }
        String[] parts = contextBody.split(":", 3);
        if (parts.length < 2) {
            return List.of();
        }
        String componentName = parts[0];
        String role = parts[1];
        boolean isConsumer = "consumer".equals(role);

        Set<String> existingKeys = new HashSet<>();
        if (parts.length > 2 && !parts[2].isEmpty()) {
            existingKeys.addAll(Arrays.asList(parts[2].split(",")));
        }

        ComponentModel model = catalog.componentModel(componentName);
        if (model == null) {
            return List.of();
        }

        // use the catalog to parse the URI and find parameters already set via the context path
        if (uri != null) {
            try {
                existingKeys.addAll(catalog.endpointProperties(uri).keySet());
            } catch (Exception e) {
                // ignore
            }
        }

        // build a set of multi-valued option names so we can allow duplicates
        Set<String> multiValuedOptions = new HashSet<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
            if (opt.isMultiValue()) {
                multiValuedOptions.add(opt.getName());
            }
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
            if (!includeEndpointOption(opt, isConsumer)) {
                continue;
            }
            if (existingKeys.contains(opt.getName()) && !multiValuedOptions.contains(opt.getName())) {
                continue;
            }
            items.add(new AutocompletePopup.CompletionItem(
                    opt.getName(), opt.getDescription(), opt.getType(),
                    opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                    opt.getGroup(), opt.isRequired()));
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> Boolean.compare(b.required(), a.required()))
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    List<AutocompletePopup.CompletionItem> provideComponentNameCompletions(String role) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        boolean isConsumer = "consumer".equals(role);
        IntegrationInfo info = ctx.findSelectedIntegration();
        String version = info != null ? info.camelVersion : null;

        // rebuild cache if catalog version changed
        if (version != null && !version.equals(componentsCatalogVersion)) {
            componentsCatalogVersion = version;
            consumerComponents = null;
            producerComponents = null;
        }

        List<AutocompletePopup.CompletionItem> cached = isConsumer ? consumerComponents : producerComponents;
        if (cached != null) {
            return cached;
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (String name : catalog.findComponentNames()) {
            ComponentModel model = catalog.componentModel(name);
            if (model == null) {
                continue;
            }
            if (isConsumer && model.isProducerOnly()) {
                continue;
            }
            if (!isConsumer && model.isConsumerOnly()) {
                continue;
            }
            String labels = model.getLabel();
            String firstLabel = labels != null && !labels.isEmpty()
                    ? labels.split(",")[0].trim()
                    : "component";
            items.add(new AutocompletePopup.CompletionItem(
                    name, model.getTitle() + " - " + model.getDescription(),
                    firstLabel, null, model.isDeprecated(), model.getDeprecationNote(),
                    labels));
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));

        if (isConsumer) {
            consumerComponents = items;
        } else {
            producerComponents = items;
        }
        return items;
    }

    boolean isListChildrenNode(String nodeName) {
        JsonObject node = getTreeNode(nodeName);
        return node != null && Boolean.TRUE.equals(node.get("listChildren"));
    }

    JsonObject getCompletionTree() {
        if (!completionTreeLoaded) {
            completionTreeLoaded = true;
            // try bundled resource first
            try (var is = getClass().getResourceAsStream("/schema/camelYamlDsl-model.json")) {
                if (is != null) {
                    String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    completionTree = (JsonObject) org.apache.camel.util.json.Jsoner.deserialize(json);
                }
            } catch (Exception e) {
                // ignore
            }
            // fallback: try catalog version manager (may have a different Camel version)
            if (completionTree == null) {
                CamelCatalog cat = getCatalog();
                if (cat != null) {
                    try (var is = cat.getVersionManager()
                            .getResourceAsStream("org/apache/camel/catalog/schemas/camelYamlDsl-model.json")) {
                        if (is != null) {
                            String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            completionTree = (JsonObject) org.apache.camel.util.json.Jsoner.deserialize(json);
                        }
                    } catch (Exception e) {
                        // ignore — tree not available for this Camel version
                    }
                }
            }
        }
        return completionTree;
    }

    JsonObject getTreeNode(String nodeName) {
        JsonObject tree = getCompletionTree();
        if (tree == null) {
            return null;
        }
        JsonObject nodes = (JsonObject) tree.get("nodes");
        if (nodes == null) {
            return null;
        }
        return (JsonObject) nodes.get(nodeName);
    }

    List<AutocompletePopup.CompletionItem> provideTreeCompletions(String contextAfterPrefix) {
        // context format: "nodeName" or "nodeName:existingKey1,existingKey2,..."
        String[] parts = contextAfterPrefix.split(":", 2);
        String nodeName = parts[0];

        Set<String> existingKeys = Set.of();
        if (parts.length > 1 && !parts[1].isEmpty()) {
            existingKeys = new HashSet<>(Arrays.asList(parts[1].split(",")));
        }

        JsonObject node = getTreeNode(nodeName);
        if (node == null) {
            return List.of();
        }

        JsonArray children = (JsonArray) node.get("children");
        if (children == null) {
            return List.of();
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (Object obj : children) {
            JsonObject child = (JsonObject) obj;
            String name = (String) child.get("name");
            if (name == null) {
                continue;
            }
            if (TREE_BOILERPLATE.contains(name)) {
                continue;
            }
            if (existingKeys.contains(name)) {
                continue;
            }

            String desc = (String) child.get("description");
            String type = (String) child.get("type");
            String group = (String) child.get("group");
            String label = (String) child.get("label");
            Object defVal = child.get("default");
            boolean required = Boolean.TRUE.equals(child.get("required"));
            boolean deprecated = Boolean.TRUE.equals(child.get("deprecated"));
            String depNote = (String) child.get("deprecationNote");

            items.add(new AutocompletePopup.CompletionItem(
                    name, desc, type, defVal, deprecated, depNote, group != null ? group : label, required));
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> {
                    boolean aIsSteps = "steps".equals(a.key()) || "outputs".equals(a.key());
                    boolean bIsSteps = "steps".equals(b.key()) || "outputs".equals(b.key());
                    return Boolean.compare(aIsSteps, bIsSteps);
                })
                .thenComparing((a, b) -> Boolean.compare(b.required(), a.required())));
        return items;
    }

    List<AutocompletePopup.CompletionItem> provideTreeValueCompletions(String contextAfterPrefix) {
        // context format: "nodeName:optionName"
        String[] parts = contextAfterPrefix.split(":", 2);
        if (parts.length < 2) {
            return List.of();
        }
        String nodeName = parts[0];
        String optionName = parts[1];

        JsonObject node = getTreeNode(nodeName);
        if (node == null) {
            return loadPropertyPlaceholders();
        }

        JsonArray children = (JsonArray) node.get("children");
        if (children == null) {
            return loadPropertyPlaceholders();
        }

        // find the matching child
        JsonObject matchedChild = null;
        for (Object obj : children) {
            JsonObject child = (JsonObject) obj;
            if (optionName.equals(child.get("name"))) {
                matchedChild = child;
                break;
            }
        }
        if (matchedChild == null) {
            return loadPropertyPlaceholders();
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        String type = (String) matchedChild.get("type");
        String desc = (String) matchedChild.get("description");
        Object defVal = matchedChild.get("default");
        String group = (String) matchedChild.get("group");

        java.util.function.Predicate<String> valueFilter = null;
        JsonArray enumValues = (JsonArray) matchedChild.get("enum");
        if (enumValues != null && !enumValues.isEmpty()) {
            Set<String> validValues = new HashSet<>();
            for (Object e : enumValues) {
                String value = String.valueOf(e);
                validValues.add(value.toLowerCase());
                boolean isDefault = value.equals(String.valueOf(defVal));
                items.add(new AutocompletePopup.CompletionItem(
                        value, desc, type, isDefault ? value : defVal, false, null, group));
            }
            valueFilter = v -> validValues.contains(v.toLowerCase());
        } else if ("boolean".equalsIgnoreCase(type)) {
            valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
            items.add(new AutocompletePopup.CompletionItem(
                    "true", desc, "boolean", defVal, false, null, group));
            items.add(new AutocompletePopup.CompletionItem(
                    "false", desc, "boolean", defVal, false, null, group));
        } else if ("number".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type)) {
            valueFilter = SourceEditAssist::isNumericValue;
        }

        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    static boolean includeEndpointOption(ComponentModel.EndpointOptionModel opt, boolean isConsumer) {
        String label = opt.getLabel();
        if (label == null || label.isEmpty()) {
            return true;
        }
        if (label.contains("consumer") && label.contains("producer")) {
            return true;
        }
        if (isConsumer) {
            return !label.contains("producer");
        } else {
            return !label.contains("consumer");
        }
    }

    List<AutocompletePopup.CompletionItem> provideYamlValueCompletions(String context) {
        if (context == null) {
            return List.of();
        }

        // EIP value completion
        // tree-driven value completion
        if (context.startsWith("yaml-tree-value:")) {
            return provideTreeValueCompletions(context.substring(16));
        }

        if (!context.startsWith("yaml:")) {
            return List.of();
        }
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        // context format: "yaml:componentName:optionName"
        String[] parts = context.substring(5).split(":", 2);
        if (parts.length < 2) {
            return List.of();
        }
        String componentName = parts[0];
        String optionName = parts[1];

        ComponentModel model = catalog.componentModel(componentName);
        if (model == null) {
            return loadPropertyPlaceholders();
        }

        ComponentModel.EndpointOptionModel opt = null;
        for (ComponentModel.EndpointOptionModel o : model.getEndpointOptions()) {
            if (o.getName().equals(optionName)) {
                opt = o;
                break;
            }
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        java.util.function.Predicate<String> valueFilter = null;

        if (opt != null) {
            List<String> enums = opt.getEnums();
            if (enums != null && !enums.isEmpty()) {
                java.util.Set<String> validValues = new java.util.HashSet<>();
                for (String value : enums) {
                    validValues.add(value.toLowerCase());
                    boolean isDefault = value.equals(String.valueOf(opt.getDefaultValue()));
                    items.add(new AutocompletePopup.CompletionItem(
                            value, opt.getDescription(), opt.getType(),
                            isDefault ? value : opt.getDefaultValue(),
                            false, null, opt.getGroup()));
                }
                valueFilter = v -> validValues.contains(v.toLowerCase());
            } else if ("boolean".equalsIgnoreCase(opt.getType())
                    || "java.lang.Boolean".equals(opt.getJavaType())) {
                valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
                items.add(new AutocompletePopup.CompletionItem(
                        "true", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
                items.add(new AutocompletePopup.CompletionItem(
                        "false", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
            } else if (isNumericType(opt.getType(), opt.getJavaType())) {
                valueFilter = SourceEditAssist::isNumericValue;
            }
        }

        // only include placeholders whose actual value is compatible with the option type
        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    List<AutocompletePopup.CompletionItem> provideEipValueCompletions(String contextAfterPrefix) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        // context format: "eipName:optionName"
        String[] parts = contextAfterPrefix.split(":", 2);
        if (parts.length < 2) {
            return List.of();
        }
        String eipName = parts[0];
        String optionName = parts[1];

        EipModel model = catalog.eipModel(eipName);
        if (model == null) {
            return loadPropertyPlaceholders();
        }

        EipModel.EipOptionModel opt = null;
        for (EipModel.EipOptionModel o : model.getOptions()) {
            if (o.getName().equals(optionName)) {
                opt = o;
                break;
            }
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        java.util.function.Predicate<String> valueFilter = null;
        if (opt != null) {
            List<String> enums = opt.getEnums();
            if (enums != null && !enums.isEmpty()) {
                java.util.Set<String> validValues = new java.util.HashSet<>();
                for (String value : enums) {
                    validValues.add(value.toLowerCase());
                    boolean isDefault = value.equals(String.valueOf(opt.getDefaultValue()));
                    items.add(new AutocompletePopup.CompletionItem(
                            value, opt.getDescription(), opt.getType(),
                            isDefault ? value : opt.getDefaultValue(),
                            false, null, opt.getGroup()));
                }
                valueFilter = v -> validValues.contains(v.toLowerCase());
            } else if ("boolean".equalsIgnoreCase(opt.getType())
                    || "java.lang.Boolean".equals(opt.getJavaType())) {
                valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
                items.add(new AutocompletePopup.CompletionItem(
                        "true", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
                items.add(new AutocompletePopup.CompletionItem(
                        "false", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
            } else if (isNumericType(opt.getType(), opt.getJavaType())) {
                valueFilter = SourceEditAssist::isNumericValue;
            }
        }

        // only include placeholders whose actual value is compatible with the option type
        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    List<AutocompletePopup.CompletionItem> loadPropertyPlaceholders() {
        if (rootDir == null || !java.nio.file.Files.isDirectory(rootDir)) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        if (placeholderCache != null && rootDir.equals(placeholderCacheDir) && (now - placeholderCacheTime) < 5000) {
            return placeholderCache;
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        try (var stream = java.nio.file.Files.list(rootDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".properties"))
                    .forEach(p -> {
                        try {
                            for (String line : java.nio.file.Files.readAllLines(p)) {
                                String trimmed = line.trim();
                                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                                    continue;
                                }
                                int eq = trimmed.indexOf('=');
                                if (eq > 0) {
                                    String key = trimmed.substring(0, eq).trim();
                                    String value = trimmed.substring(eq + 1).trim();
                                    items.add(new AutocompletePopup.CompletionItem(
                                            "{{" + key + "}}", value, "placeholder",
                                            null, false, null, p.getFileName().toString()));
                                }
                            }
                        } catch (IOException e) {
                            // skip unreadable files
                        }
                    });
        } catch (IOException e) {
            return List.of();
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        placeholderCache = items;
        placeholderCacheTime = now;
        placeholderCacheDir = rootDir;
        return items;
    }

    Map<Integer, List<SourceViewer.DocEntry>> providePropertiesQuickDocs(List<JsonObject> codeData) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || codeData.isEmpty()) {
            return Map.of();
        }
        ensureMainOptionsCache(catalog);

        Map<Integer, List<SourceViewer.DocEntry>> result = new LinkedHashMap<>();
        for (int i = 0; i < codeData.size(); i++) {
            String code = codeData.get(i).getString("code");
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            BaseOptionModel opt = lookupPropertyOption(catalog, key);
            if (opt != null) {
                String doc = EipDocSupport.formatOptionDoc(opt);
                if (doc != null) {
                    result.put(i, List.of(opt.isDeprecated()
                            ? SourceViewer.DocEntry.deprecated(doc)
                            : SourceViewer.DocEntry.of(doc)));
                }
                continue;
            }
            // fallback to Spring Boot configuration metadata for non-camel properties
            ensureSpringBootMetadataCache();
            if (springBootMetadataCache != null) {
                JsonObject sbProp = springBootMetadataCache.get(key);
                if (sbProp != null) {
                    String doc = SpringBootMetadataHelper.formatDoc(sbProp);
                    if (doc != null) {
                        boolean deprecated = Boolean.TRUE.equals(sbProp.get("deprecated"));
                        result.put(i, List.of(deprecated
                                ? SourceViewer.DocEntry.deprecated(doc)
                                : SourceViewer.DocEntry.of(doc)));
                    }
                }
            }
        }
        return result;
    }

    Set<Integer> scanDeprecatedProperties(List<JsonObject> codeData) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || codeData.isEmpty()) {
            return Set.of();
        }
        ensureMainOptionsCache(catalog);

        Set<Integer> result = new HashSet<>();
        for (int i = 0; i < codeData.size(); i++) {
            String code = codeData.get(i).getString("code");
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            BaseOptionModel opt = lookupPropertyOption(catalog, key);
            if (opt != null) {
                if (opt.isDeprecated()) {
                    result.add(i);
                }
                continue;
            }
            ensureSpringBootMetadataCache();
            if (springBootMetadataCache != null) {
                JsonObject sbProp = springBootMetadataCache.get(key);
                if (sbProp != null && Boolean.TRUE.equals(sbProp.get("deprecated"))) {
                    result.add(i);
                }
            }
        }
        return result;
    }

    String validatePropertyLine(String line) {
        CamelCatalog catalog = getCatalog();
        if (catalog != null) {
            try {
                ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(line);
                if (result.isAccepted()) {
                    if (!result.isSuccess()) {
                        String msg = result.summaryErrorMessage(false);
                        if (msg != null) {
                            return msg.trim();
                        }
                    }
                    return null;
                }
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        // validate Spring Boot properties
        return validateSpringBootPropertyLine(line);
    }

    String validateSpringBootPropertyLine(String line) {
        ensureSpringBootMetadataCache();
        if (springBootOptionsCache == null || springBootOptionsCache.isEmpty()) {
            return null;
        }
        String trimmed = line.trim();
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String key = trimmed.substring(0, eq).trim();
        // only validate keys that look like Spring Boot properties
        if (key.startsWith("camel.") || key.startsWith("#") || key.startsWith("!")) {
            return null;
        }
        // check if the key exists in Spring Boot metadata
        if (!springBootOptionsCache.containsKey(key)) {
            // check if it's a known prefix (partial key) — don't flag those
            for (String known : springBootOptionsCache.keySet()) {
                if (known.startsWith(key + ".")) {
                    return null;
                }
            }
            // check if it belongs to a known Spring Boot group
            boolean inSpringBootNamespace = false;
            for (String group : springBootGroupsCache.keySet()) {
                if (key.startsWith(group + ".")) {
                    inSpringBootNamespace = true;
                    break;
                }
            }
            if (inSpringBootNamespace) {
                return "Unknown Spring Boot property: " + key;
            }
        }
        return null;
    }

    private org.apache.camel.dsl.yaml.validator.YamlValidator yamlValidator;

    /**
     * Validates Camel YAML DSL source the way the editor does on save: the YAML DSL schema (unknown or misspelled
     * options, wrong structure), then endpoint URIs and simple expressions against the catalog. Returns the messages,
     * empty when the source is valid.
     */
    List<String> validateCamelYaml(String content) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        try {
            if (yamlValidator == null) {
                yamlValidator = new org.apache.camel.dsl.yaml.validator.YamlValidator();
            }
            msgs.addAll(SourceValidationSupport.formatSchemaErrors(yamlValidator.validate(content)));
        } catch (Exception e) {
            msgs.add("Invalid YAML: " + e.getMessage());
            return msgs;
        }
        msgs.addAll(validateYamlEndpoints(content));
        msgs.addAll(validateYamlSimple(content));
        return msgs;
    }

    /** Validates a properties file (application.properties) line by line against the catalog, as the editor does. */
    List<String> validateProperties(String content) {
        return SourceValidationSupport.validatePropertiesLines(content, this::validatePropertyLine);
    }

    /**
     * Validates source by file type with the same checks the editor runs on save: Camel YAML DSL for .yaml/.yml files,
     * Camel and Spring Boot options for .properties files. Other file types have no validation and yield no messages.
     */
    List<String> validateSource(String fileName, String content) {
        String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return validateCamelYaml(content);
        }
        if (name.endsWith(".properties")) {
            return validateProperties(content);
        }
        return List.of();
    }

    static boolean isValidatableFile(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties");
    }

    List<String> validateYamlEndpoints(String content) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }
        return doValidateYamlEndpoints(content, catalog);
    }

    List<String> validateYamlSimple(String content) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }
        return doValidateYamlSimple(content, catalog);
    }

    static List<String> doValidateYamlSimple(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            String simpleText = null;
            int lineNum = i + 1;
            int lineIndent = countLeadingSpaces(line);
            boolean isLogMessage = false;

            // Strip YAML list prefix for matching
            String key = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;

            // Match "simple: <value>" (inline shorthand)
            if (key.startsWith("simple:") && !key.equals("simple:")) {
                simpleText = SourceTab.extractYamlValue(key, "simple");
            }
            // Match "simple:" followed by "expression: <value>" on next line
            else if (key.equals("simple:")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isBlank()) {
                        continue;
                    }
                    if (next.startsWith("expression:")) {
                        simpleText = SourceTab.extractYamlValue(next, "expression");
                        lineNum = j + 1;
                    }
                    break;
                }
            }
            // Match "message: <value>" under log: EIP
            else if (key.startsWith("message:") && !key.equals("message:")) {
                String parentEip = findParentEip(lines, i, lineIndent);
                if ("log".equals(parentEip)) {
                    simpleText = SourceTab.extractYamlValue(key, "message");
                    isLogMessage = true;
                }
            }

            if (simpleText == null || simpleText.isEmpty()) {
                continue;
            }
            // Skip what the catalog cannot validate because a placeholder is unresolved
            if (hasPlaceholderAsLogicalOperand(simpleText)) {
                continue;
            }

            // Determine predicate vs expression context
            boolean predicate = false;
            if (!isLogMessage) {
                String parentEip = findParentEip(lines, i, lineIndent);
                predicate = parentEip != null && PREDICATE_EIPS.contains(parentEip);
            }

            try {
                LanguageValidationResult result = predicate
                        ? catalog.validateLanguagePredicate(null, "simple", simpleText)
                        : catalog.validateLanguageExpression(null, "simple", simpleText);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null) {
                        errors.add("Line " + lineNum + ": Simple syntax error: " + error);
                    }
                }
            } catch (Exception e) {
                // best effort
            }
        }
        return errors;
    }

    /**
     * Whether the text uses a property placeholder as an operand of a logical operator, such as
     * <tt>{{enabled}} && ${body} > 10</tt>.
     *
     * A placeholder can expand to an entire predicate, which the catalog cannot know as it validates without a running
     * Camel application. The catalog substitutes a placeholder with a dummy value, which is what an operand of a binary
     * operator needs, but a logical operator needs a predicate on either side. Validating those would report an error
     * for a route that is perfectly valid at runtime, so they are skipped.
     */
    static boolean hasPlaceholderAsLogicalOperand(String text) {
        if (text == null || !text.contains("{{")) {
            return false;
        }

        // split into the operands of the logical operators, ignoring any quoted literal
        List<String> operands = new ArrayList<>();
        char quote = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote == 0 && (ch == '\'' || ch == '"')) {
                quote = ch;
            } else if (quote == ch) {
                quote = 0;
            } else if (quote == 0 && i < text.length() - 1) {
                char next = text.charAt(i + 1);
                if (ch == '&' && next == '&' || ch == '|' && next == '|') {
                    operands.add(text.substring(start, i));
                    i++;
                    start = i + 1;
                }
            }
        }
        if (operands.isEmpty()) {
            // no logical operator so the placeholders are all used as a value which the catalog can validate
            return false;
        }
        operands.add(text.substring(start));

        for (String operand : operands) {
            String s = operand.trim();
            // is the operand nothing but a single placeholder
            if (s.startsWith("{{") && s.endsWith("}}") && s.indexOf("}}") == s.length() - 2) {
                return true;
            }
        }
        return false;
    }

    static String findParentEip(String[] lines, int lineIdx, int lineIndent) {
        for (int j = lineIdx - 1; j >= 0; j--) {
            String prev = lines[j];
            if (prev.isBlank()) {
                continue;
            }
            int prevIndent = countLeadingSpaces(prev);
            if (prevIndent < lineIndent) {
                return extractEipFromLine(prev.trim());
            }
        }
        return null;
    }

    static List<String> doValidateYamlEndpoints(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            Matcher m = YAML_URI_PATTERN.matcher(line);
            if (!m.find()) {
                continue;
            }

            String uri = m.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            if (uri.startsWith("{{")) {
                continue;
            }
            // scheme-only URI (e.g., "uri: timer") needs a colon for catalog parsing
            if (!uri.contains(":")) {
                uri = uri + ":";
            }

            String eipName = extractEipFromLine(trimmed);
            int lineIndent = countLeadingSpaces(line);

            // for "uri:" lines, walk backwards to find the parent EIP (from, to, etc.)
            if ("uri".equals(eipName)) {
                for (int j = i - 1; j >= 0; j--) {
                    String prev = lines[j];
                    if (prev.isBlank()) {
                        continue;
                    }
                    int prevIndent = countLeadingSpaces(prev);
                    if (prevIndent < lineIndent) {
                        eipName = extractEipFromLine(prev.trim());
                        break;
                    }
                }
            }

            boolean consumerOnly = eipName != null && YamlSourceContext.CONSUMER_EIPS.contains(eipName);
            boolean producerOnly = eipName != null && YamlSourceContext.PRODUCER_EIPS.contains(eipName);

            // look ahead for a parameters: block at the same indent level as uri
            StringBuilder uriBuilder = new StringBuilder(uri);
            boolean hasParams = uri.contains("?");
            Map<String, Integer> optionLineMap = new LinkedHashMap<>();
            for (int j = i + 1; j < lines.length; j++) {
                String next = lines[j];
                if (next.isBlank()) {
                    continue;
                }
                int nextIndent = countLeadingSpaces(next);
                if (nextIndent < lineIndent) {
                    break;
                }
                String nextTrimmed = next.trim();
                if (nextIndent == lineIndent && nextTrimmed.startsWith("parameters:")) {
                    int paramBlockIndent = nextIndent;
                    for (int k = j + 1; k < lines.length; k++) {
                        String paramLine = lines[k];
                        if (paramLine.isBlank()) {
                            continue;
                        }
                        int paramIndent = countLeadingSpaces(paramLine);
                        if (paramIndent <= paramBlockIndent) {
                            break;
                        }
                        String paramTrimmed = paramLine.trim();
                        int colonPos = paramTrimmed.indexOf(':');
                        if (colonPos > 0) {
                            String key = paramTrimmed.substring(0, colonPos).trim();
                            String val = paramTrimmed.substring(colonPos + 1).trim();
                            if (val.startsWith("\"") && val.endsWith("\"") && val.length() > 1) {
                                val = val.substring(1, val.length() - 1);
                            } else if (val.startsWith("'") && val.endsWith("'") && val.length() > 1) {
                                val = val.substring(1, val.length() - 1);
                            }
                            char sep = hasParams ? '&' : '?';
                            uriBuilder.append(sep).append(key).append('=').append(val);
                            hasParams = true;
                            optionLineMap.put(key, k);
                        }
                    }
                    break;
                }
                if (nextIndent == lineIndent) {
                    break;
                }
            }

            String fullUri = uriBuilder.toString();
            try {
                EndpointValidationResult result
                        = catalog.validateEndpointProperties(fullUri, false, consumerOnly, producerOnly);
                if (!result.isSuccess()) {
                    String scheme = fullUri.contains(":") ? fullUri.substring(0, fullUri.indexOf(':')) : fullUri;
                    collectEndpointErrors(errors, result, scheme, i, optionLineMap);
                }
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return errors;
    }

    static void collectEndpointErrors(
            List<String> errors, EndpointValidationResult result, String scheme,
            int uriLineIdx, Map<String, Integer> optionLineMap) {
        if (result.getUnknown() != null) {
            for (String name : result.getUnknown()) {
                StringBuilder sb = new StringBuilder(scheme).append(": Unknown option '").append(name).append("'");
                if (result.getUnknownSuggestions() != null) {
                    String[] suggestions = result.getUnknownSuggestions().get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        sb.append(". Did you mean: ").append(Arrays.asList(suggestions));
                    }
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + sb);
            }
        }
        if (result.getInvalidBoolean() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidBoolean().entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid boolean value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
        if (result.getInvalidInteger() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidInteger().entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid integer value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
        if (result.getInvalidNumber() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidNumber().entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid number value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
        if (result.getInvalidEnum() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidEnum().entrySet()) {
                StringBuilder sb = new StringBuilder(scheme)
                        .append(": Invalid enum value '").append(entry.getValue())
                        .append("' for option '").append(entry.getKey()).append("'");
                if (result.getInvalidEnumChoices() != null) {
                    String[] choices = result.getInvalidEnumChoices().get(entry.getKey());
                    if (choices != null) {
                        sb.append(". Possible values: ").append(Arrays.asList(choices));
                    }
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx)) + sb);
            }
        }
        if (result.getNotConsumerOnly() != null) {
            for (String name : result.getNotConsumerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in consumer only mode");
            }
        }
        if (result.getNotProducerOnly() != null) {
            for (String name : result.getNotProducerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in producer only mode");
            }
        }
    }

    static String linePrefix(int lineIdx) {
        return "Line " + (lineIdx + 1) + ": ";
    }

    static String extractEipFromLine(String trimmed) {
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            return trimmed.substring(0, colon).trim();
        }
        return null;
    }

    static String formatFullOptionDoc(BaseOptionModel opt) {
        if (opt == null) {
            return null;
        }
        return opt.getDescription();
    }

    static String formatOptionTitle(BaseOptionModel opt) {
        List<String> parts = new ArrayList<>();
        parts.add(opt.getName());
        List<String> meta = new ArrayList<>();
        if (opt.getType() != null) {
            meta.add(opt.getType());
        }
        if (opt.isRequired()) {
            meta.add("required");
        }
        if (opt.getDefaultValue() != null) {
            meta.add("default: " + opt.getDefaultValue());
        }
        if (!meta.isEmpty()) {
            parts.add("(" + String.join(", ", meta) + ")");
        }
        return String.join(" ", parts);
    }

    BaseOptionModel lookupPropertyOption(CamelCatalog catalog, String key) {
        if (mainOptionsCache != null) {
            BaseOptionModel opt = mainOptionsCache.get(key);
            if (opt != null) {
                return opt;
            }
        }
        if (key.startsWith("camel.component.")) {
            return lookupPrefixedOption(key, "camel.component.", componentOptionsCache,
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        }
        if (key.startsWith("camel.language.")) {
            return lookupPrefixedOption(key, "camel.language.", languageOptionsCache,
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        if (key.startsWith("camel.dataformat.")) {
            return lookupPrefixedOption(key, "camel.dataformat.", dataformatOptionsCache,
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        return null;
    }

    void ensureMainOptionsCache(CamelCatalog catalog) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        String version = info != null ? info.camelVersion : null;
        if (version != null && !version.equals(propsCatalogVersion)) {
            mainOptionsCache = null;
            mainGroupsCache = null;
            componentOptionsCache.clear();
            languageOptionsCache.clear();
            dataformatOptionsCache.clear();
            springBootMetadataCache = null;
            springBootMetadataLoaded = false;
            springBootOptionsCache = null;
            springBootGroupsCache = null;
            springBootHintsCache = null;
            springBootMetadataFuture = null;
            propsCatalogVersion = version;
        }
        if (mainOptionsCache == null) {
            mainOptionsCache = new HashMap<>();
            mainGroupsCache = new HashMap<>();
            MainModel mainModel = catalog.mainModel();
            if (mainModel != null) {
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
        }
    }

    void ensureSpringBootMetadataCache() {
        if (springBootMetadataLoaded) {
            // check if async loading completed
            if (springBootMetadataFuture != null && springBootMetadataFuture.isDone()) {
                applySpringBootMetadataResult(springBootMetadataFuture.join());
                springBootMetadataFuture = null;
            }
            return;
        }
        springBootMetadataLoaded = true;
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || !"Spring Boot".equals(info.platform)) {
            return;
        }

        if (!info.phantom && info.pid != null && !info.pid.isEmpty()) {
            springBootMetadataCache = SpringBootMetadataHelper.fetchMetadata(ctx, info.pid);
            if (springBootMetadataCache != null && !springBootMetadataCache.isEmpty()) {
                applySpringBootMetadataResult(
                        new SpringBootMetadataResolver.MetadataResult(springBootMetadataCache, Map.of()));
                return;
            }
        }

        if (info.directory != null) {
            Path pomFile = Path.of(info.directory, "pom.xml");
            if (Files.isRegularFile(pomFile)) {
                String camelVer = info.camelVersion;
                springBootMetadataFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> SpringBootMetadataResolver.loadFromPom(pomFile, camelVer),
                        ctx.backgroundExecutor);
            }
        }
    }

    void applySpringBootMetadataResult(SpringBootMetadataResolver.MetadataResult result) {
        if (result == null) {
            return;
        }
        springBootMetadataCache = result.properties();
        if (springBootMetadataCache != null && !springBootMetadataCache.isEmpty()) {
            springBootOptionsCache = new HashMap<>();
            springBootGroupsCache = new HashMap<>();
            springBootHintsCache = result.hints() != null ? result.hints() : Map.of();

            for (Map.Entry<String, JsonObject> entry : springBootMetadataCache.entrySet()) {
                String name = entry.getKey();
                BaseOptionModel model = SpringBootMetadataHelper.toOptionModel(entry.getValue());
                springBootOptionsCache.put(name, model);

                int lastDot = name.lastIndexOf('.');
                if (lastDot > 0) {
                    String group = name.substring(0, lastDot);
                    springBootGroupsCache.putIfAbsent(group, "");
                }
            }
        }
    }
}
