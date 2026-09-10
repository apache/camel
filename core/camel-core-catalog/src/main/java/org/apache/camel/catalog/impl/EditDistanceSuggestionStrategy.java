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
package org.apache.camel.catalog.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.camel.catalog.SuggestionStrategy;

/**
 * A {@link SuggestionStrategy} based on edit distance, for the "did you mean" of unknown option names and invalid enum
 * values. Option names are camelCase identifiers, so a mistake is a typo, a slip of case, a missing or swapped letter,
 * or a name typed only partly; the strategy ranks the known names by how many such edits separate them from the unknown
 * one and returns the closest, at most {@link #DEFAULT_MAX_SUGGESTIONS} of them.
 * <p>
 * A name that matches ignoring case comes first, then names the unknown one is a prefix of (or that are a prefix of
 * it), then names within a small Damerau-Levenshtein distance (transpositions count as one edit). The distance allowed
 * grows with the length of the name, so a long name may be a few letters off while a short one must be close. This is
 * the default strategy of the catalog.
 */
public class EditDistanceSuggestionStrategy implements SuggestionStrategy {

    public static final int DEFAULT_MAX_SUGGESTIONS = 5;

    private static final int MIN_PREFIX_LENGTH = 3;

    private final int maxSuggestions;

    public EditDistanceSuggestionStrategy() {
        this(DEFAULT_MAX_SUGGESTIONS);
    }

    public EditDistanceSuggestionStrategy(int maxSuggestions) {
        this.maxSuggestions = maxSuggestions;
    }

    @Override
    public String[] suggestEndpointOptions(Set<String> names, String unknownOption) {
        return suggest(names, unknownOption, maxSuggestions);
    }

    /**
     * The known names closest to the unknown one, best first; empty when none is close enough.
     *
     * @param  names          the valid names
     * @param  unknown        the name that was not recognised
     * @param  maxSuggestions how many names to return at most
     * @return                the suggestions, never null
     */
    public static String[] suggest(Collection<String> names, String unknown, int maxSuggestions) {
        if (names == null || unknown == null || unknown.isBlank() || maxSuggestions <= 0) {
            return new String[0];
        }
        String key = unknown.trim().toLowerCase(Locale.ROOT);
        int allowed = allowedDistance(key);
        List<Match> matches = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String candidate = name.toLowerCase(Locale.ROOT);
            int score;
            if (candidate.equals(key)) {
                score = 0;
            } else if (isPrefix(candidate, key)) {
                score = 1;
            } else {
                int distance = distance(candidate, key);
                if (distance > allowed) {
                    continue;
                }
                // a real edit is never as good as a name that only lacks its tail
                score = distance + 1;
            }
            matches.add(new Match(name, score));
        }
        matches.sort(Comparator.comparingInt(Match::score).thenComparing(Match::name));
        List<String> answer = new ArrayList<>();
        for (Match match : matches) {
            if (answer.size() >= maxSuggestions) {
                break;
            }
            answer.add(match.name());
        }
        return answer.toArray(new String[0]);
    }

    /** How many edits a name may be off: two for short names, a quarter of the length for longer ones. */
    static int allowedDistance(String key) {
        return Math.max(2, key.length() / 4);
    }

    private static boolean isPrefix(String candidate, String key) {
        if (key.length() >= MIN_PREFIX_LENGTH && candidate.startsWith(key)) {
            return true;
        }
        return candidate.length() >= MIN_PREFIX_LENGTH && key.startsWith(candidate);
    }

    /**
     * The Damerau-Levenshtein distance (optimal string alignment): insertions, deletions, substitutions and
     * transpositions of adjacent characters each count as one edit.
     */
    static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, d[i - 2][j - 2] + 1);
                }
                d[i][j] = value;
            }
        }
        return d[n][m];
    }

    private record Match(String name, int score) {
    }
}
