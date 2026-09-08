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
package org.apache.camel.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * Ranks catalog entries against a search term such as a protocol or product name (mqtt, s3, rabbitmq) for the suggest
 * methods on {@link CamelCatalog}.
 */
final class CatalogTermMatcher {

    private static final int NO_MATCH = -1;
    /** Shorter terms are too ambiguous to match as a substring of a name. */
    private static final int MIN_SUBSTRING_LENGTH = 3;

    private record Match(String name, int rank, String group) {
    }

    private CatalogTermMatcher() {
    }

    /**
     * Component names matching the term, best match first. Components that share one implementation under several
     * schemes (imap, pop3, smtp) are returned once: by the scheme that matched, or otherwise their primary scheme.
     */
    static List<String> suggestComponentNames(CamelCatalog catalog, String term, int max) {
        String key = normalize(term);
        if (key.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (String name : catalog.findComponentNames()) {
            ComponentModel model = name == null || name.isBlank() ? null : catalog.componentModel(name);
            if (model == null) {
                continue;
            }
            int rank = rankComponent(model, key);
            if (rank != NO_MATCH) {
                String group = model.getAlternativeSchemes();
                matches.add(new Match(name, rank, group == null || group.isBlank() ? null : group));
            }
        }
        Map<String, Match> bestPerGroup = new HashMap<>();
        for (Match match : matches) {
            if (match.group() != null) {
                bestPerGroup.merge(match.group(), match, CatalogTermMatcher::better);
            }
        }
        matches.removeIf(m -> m.group() != null && bestPerGroup.get(m.group()) != m);
        return names(matches, max);
    }

    /**
     * Data format or language names matching the term, best match first.
     */
    static List<String> suggestNames(
            List<String> names, Function<String, ? extends BaseModel<?>> modelLoader, String term, int max) {
        String key = normalize(term);
        if (key.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (String name : names) {
            BaseModel<?> model = name == null || name.isBlank() ? null : modelLoader.apply(name);
            if (model == null) {
                continue;
            }
            int rank = rankNamed(model, key);
            if (rank != NO_MATCH) {
                matches.add(new Match(name, rank, null));
            }
        }
        return names(matches, max);
    }

    private static int rankComponent(ComponentModel model, String key) {
        String scheme = model.getScheme();
        if (key.equals(normalize(scheme))) {
            return 0;
        }
        if (matchesAlias(model, key)) {
            return 1;
        }
        if (model.getAlternativeSchemes() != null && containsNormalized(model.getAlternativeSchemes().split(","), key)) {
            return 2;
        }
        if (matchesTitle(model, key)) {
            return 3;
        }
        if (containsNormalized(words(scheme), key)) {
            return 4;
        }
        if (key.length() >= MIN_SUBSTRING_LENGTH && normalize(scheme).contains(key)) {
            return 5;
        }
        return NO_MATCH;
    }

    private static int rankNamed(BaseModel<?> model, String key) {
        String name = model.getName();
        if (key.equals(normalize(name))) {
            return 0;
        }
        if (matchesAlias(model, key)) {
            return 1;
        }
        if (matchesTitle(model, key)) {
            return 2;
        }
        if (containsNormalized(camelCaseWords(name), key)) {
            return 3;
        }
        if (key.length() >= MIN_SUBSTRING_LENGTH && normalize(name).contains(key)) {
            return 4;
        }
        return NO_MATCH;
    }

    private static boolean matchesAlias(BaseModel<?> model, String key) {
        List<String> aliases = model.getAliases();
        return aliases != null && containsNormalized(aliases.toArray(new String[0]), key);
    }

    private static boolean matchesTitle(BaseModel<?> model, String key) {
        String title = model.getTitle();
        return title != null && (key.equals(normalize(title)) || containsNormalized(words(title), key));
    }

    private static Match better(Match a, Match b) {
        if (a.rank() != b.rank()) {
            return a.rank() < b.rank() ? a : b;
        }
        // same rank: prefer the scheme listed first, which is the primary one
        List<String> schemes = Arrays.asList(a.group().split(","));
        return schemes.indexOf(b.name()) < schemes.indexOf(a.name()) ? b : a;
    }

    private static List<String> names(List<Match> matches, int max) {
        matches.sort(Comparator.comparingInt(Match::rank));
        List<String> answer = new ArrayList<>(matches.size());
        for (Match match : matches) {
            if (max > 0 && answer.size() >= max) {
                break;
            }
            // a catalog can list a name twice when a component was added on top of the built-in ones
            if (!answer.contains(match.name())) {
                answer.add(match.name());
            }
        }
        return answer;
    }

    private static boolean containsNormalized(String[] candidates, String key) {
        for (String candidate : candidates) {
            if (key.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static String[] words(String text) {
        return text == null ? new String[0] : text.split("[^A-Za-z0-9]+");
    }

    private static String[] camelCaseWords(String text) {
        return text == null ? new String[0] : words(text.replaceAll("([a-z0-9])([A-Z])", "$1 $2"));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

}
