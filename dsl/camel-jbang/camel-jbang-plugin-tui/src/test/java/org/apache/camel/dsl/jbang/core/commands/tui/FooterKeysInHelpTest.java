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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.text.Span;
import dev.tamboui.widgets.tabs.TabsState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keys the footer of a tab shows are written down in the help page of the tab (F1), so the two do not drift apart:
 * a key added to the footer and not to the help page fails here. Only the footer of a tab just opened is checked; the
 * keys of a detail view or a popup are not.
 */
class FooterKeysInHelpTest {

    /** The keys of the whole TUI, which the help page of each tab need not repeat. */
    private static final Set<String> GLOBAL_KEYS = Set.of("Esc", "q", "?", "F1", "F2", "F3", "F6", "F8", "F10");

    /** A key in a help page: `x`, **x**, or the first cell of a table row. */
    private static final Pattern HELP_KEY = Pattern.compile(
            "`([^`\\n]+)`|\\*\\*([^*\\n]+)\\*\\*|^\\|\\s*([^|`*\\n]+?)\\s*\\|", Pattern.MULTILINE);

    @Test
    void theKeysOfTheFooterAreInTheHelpPage() {
        List<String> missing = new ArrayList<>();
        for (MonitorTab tab : allTabs()) {
            String help = tab.getHelpText();
            if (help == null) {
                continue;
            }
            Set<String> documented = helpKeys(help);
            for (String key : footerKeys(tab)) {
                if (!GLOBAL_KEYS.contains(key) && !isDocumented(key, documented)) {
                    missing.add(tab.getClass().getSimpleName() + ": " + key);
                }
            }
        }
        assertThat(missing)
                .as("Footer keys missing from the help page of the tab (src/main/resources/tui/help/<tab>.md)")
                .isEmpty();
    }

    @Test
    void theHelpKeysAreFoundInTheirMarkdownForms() {
        Set<String> keys = helpKeys("""
                - `s` — sort
                - **w** — wrap
                | `Ctrl+R` | refactor |
                | Enter | open |
                - `PgUp / PgDn` — page
                """);
        assertThat(keys).contains("s", "w", "Ctrl+R", "Enter", "PgUp/PgDn");
        assertThat(isDocumented("PgUp/Dn", keys)).isTrue();
        assertThat(isDocumented("↑↓", Set.of("Up/Down"))).isTrue();
        assertThat(isDocumented("↑↓", helpKeys("| ↑ / ↓ | select |"))).isTrue();
        assertThat(isDocumented("x", keys)).isFalse();
    }

    private static Set<String> footerKeys(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFooter(spans);
        Set<String> keys = new LinkedHashSet<>();
        for (Span span : spans) {
            if (span.style().equals(Theme.hintKey())) {
                String key = span.content().strip();
                if ("show".equals(key)) {
                    // the toggles that follow it: show body, headers, properties and variables
                    keys.addAll(List.of("b", "h", "p", "v"));
                } else {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    private static Set<String> helpKeys(String help) {
        Set<String> keys = new HashSet<>();
        Matcher m = HELP_KEY.matcher(help);
        while (m.find()) {
            for (int g = 1; g <= 3; g++) {
                if (m.group(g) != null) {
                    keys.add(normalize(m.group(g)));
                }
            }
        }
        return keys;
    }

    private static boolean isDocumented(String key, Set<String> documented) {
        String k = normalize(key);
        if (documented.contains(k)) {
            return true;
        }
        // a pair the help page writes down apart: s/S as s and S
        if (k.length() > 2 && k.indexOf('/') > 0 && k.indexOf('/') < k.length() - 1) {
            for (String part : k.split("/")) {
                if (!documented.contains(part)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** The ways a footer and a help page write the same key, made one. */
    private static String normalize(String key) {
        String k = key.strip().replace(" / ", "/");
        return switch (k) {
            case "PgUp/Dn" -> "PgUp/PgDn";
            case TuiIcons.HINT_SCROLL, "↑/↓" -> "Up/Down";
            case TuiIcons.HINT_H, "←/→" -> "Left/Right";
            default -> k;
        };
    }

    private static List<MonitorTab> allTabs() {
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        MonitorContext ctx = new MonitorContext(data, infraData);
        DataRefreshService dataService = new DataRefreshService(
                "test",
                new DataRefreshService.RefreshContext() {
                    @Override
                    public int selectedTab() {
                        return 0;
                    }

                    @Override
                    public boolean isSwitchPopupVisible() {
                        return false;
                    }

                    @Override
                    public String getPendingAutoSelect() {
                        return null;
                    }

                    @Override
                    public void clearPendingAutoSelect() {
                    }

                    @Override
                    public void onInfraAutoSelected(int tableIndex, String pid) {
                    }

                    @Override
                    public boolean isInfraSelected() {
                        return false;
                    }
                },
                Path::of, Path::of);
        TabRegistry registry = new TabRegistry(new TabsState(TabRegistry.TAB_OVERVIEW));
        registry.initTabs(ctx, dataService, () -> {
        });
        List<MonitorTab> tabs = new ArrayList<>();
        registry.allTabEntries().stream()
                .filter(entry -> entry.tabIndex() != TabRegistry.TAB_MORE)
                .forEach(entry -> tabs.add(entry.tab()));
        registry.moreTabs().forEach(mt -> tabs.add(mt.tab()));
        return tabs;
    }
}
