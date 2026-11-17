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
package org.apache.camel.support;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PatternHelper {

    private static final String REGEX_METACHARS = "\\^$.|?*+()[]{}";

    private PatternHelper() {
        //Utility Class
    }

    /**
     * Matches the name with the given pattern (case insensitive).
     * <p>
     * Only a single pattern is supported, use {@link #matchPatterns(String, String[])} to match multiple patterns.
     * <p>
     * The match rules are applied in this order:
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param  name    the name
     * @param  pattern a pattern to match
     * @return         <code>true</code> if match, <code>false</code> otherwise.
     * @see            #matchPatterns(String, String[])
     */
    public static boolean matchPattern(String name, String pattern) {
        if (name == null || pattern == null) {
            return false;
        }

        if (name.equalsIgnoreCase(pattern)) {
            // exact match
            return true;
        }

        if ("*".equals(pattern)) {
            // matches everything
            return true;
        }

        if (matchWildcard(name, pattern)) {
            return true;
        }

        if (mayMatchRegex(name, pattern) && matchRegex(name, pattern)) {
            return true;
        }

        // no match
        return false;
    }

    /**
     * Whether the regular expression can still match, once the exact and wildcard matches failed. This is called for
     * every key by removeHeaders("Camel*") so most calls must not compile a regular expression.
     */
    private static boolean mayMatchRegex(String name, String pattern) {
        int literalEnd = pattern.endsWith("*") ? pattern.length() - 1 : pattern.length();
        for (int i = 0; i < literalEnd; i++) {
            if (REGEX_METACHARS.indexOf(pattern.charAt(i)) >= 0) {
                return true;
            }
        }
        if (literalEnd == pattern.length()) {
            // a plain text only matches itself, which is the exact match
            return false;
        }
        // text* also means zero or more of the last character of the text, so every name that starts with the text
        // before it goes to the regular expression, which decides (the wildcard match can miss a repeat after
        // toLowerCase, for example the Greek final sigma)
        int prefix = pattern.offsetByCodePoints(literalEnd, -1);
        return name.length() >= prefix && name.regionMatches(true, 0, pattern, 0, prefix);
    }

    /**
     * Matches the name with the given patterns (case insensitive).
     *
     * @param  name     the name
     * @param  patterns pattern(s) to match
     * @return          <code>true</code> if match, <code>false</code> otherwise.
     * @see             #matchPattern(String, String)
     */
    public static boolean matchPatterns(String name, String[] patterns) {
        for (String pattern : patterns) {
            if (PatternHelper.matchPattern(name, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches the name with the given pattern (case insensitive) using simple matching only (no regular expressions).
     * <p>
     * The match rules are applied in this order:
     * <ul>
     * <li>exact match (case insensitive), returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param  name    the name
     * @param  pattern a pattern to match
     * @return         <code>true</code> if match, <code>false</code> otherwise.
     * @see            #matchSimplePatterns(String, String[])
     */
    public static boolean matchSimplePattern(String name, String pattern) {
        if (name == null || pattern == null) {
            return false;
        }

        if (name.equalsIgnoreCase(pattern)) {
            return true;
        }

        if ("*".equals(pattern)) {
            return true;
        }

        return matchWildcard(name, pattern);
    }

    /**
     * Matches the name with the given patterns (case insensitive) using simple matching only (no regular expressions).
     *
     * @param  name     the name
     * @param  patterns pattern(s) to match
     * @return          <code>true</code> if match, <code>false</code> otherwise.
     * @see             #matchSimplePattern(String, String)
     */
    public static boolean matchSimplePatterns(String name, String[] patterns) {
        for (String pattern : patterns) {
            if (PatternHelper.matchSimplePattern(name, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches the name with the given pattern (case insensitive).
     * <p>
     * The match rules are applied in this order:
     * <ul>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param  name    the name
     * @param  pattern a pattern to match
     * @return         <code>true</code> if match, <code>false</code> otherwise.
     */
    private static boolean matchWildcard(String name, String pattern) {
        // we have wildcard support in that hence you can match with: file* to match any file endpoints
        if (pattern.endsWith("*")) {
            String text = pattern.substring(0, pattern.length() - 1);
            return name.toLowerCase(Locale.ENGLISH).startsWith(text.toLowerCase(Locale.ENGLISH));
        }
        return false;
    }

    /**
     * Matches the name with the given pattern (case insensitive).
     * <p>
     * The match rules are applied in this order:
     * <ul>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param  name    the name
     * @param  pattern a pattern to match
     * @return         <code>true</code> if match, <code>false</code> otherwise.
     */
    public static boolean matchRegex(String name, String pattern) {
        // match by regular expression
        try {
            Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher matcher = compiled.matcher(name);
            return matcher.matches();
        } catch (PatternSyntaxException e) {
            // ignore
        }
        return false;
    }

    /**
     * Combines several patterns into one pattern, which {@link #matchPattern(String, String)} matches when any of the
     * patterns matches. This is how options that hold a single pattern, such as the excludePattern of removeHeaders,
     * keep several patterns.
     * <p>
     * A single pattern is returned as it is. Several patterns are joined with | into one regular expression: a name
     * stays as it is, a regular expression is put in a group, and a pattern that is not a valid regular expression is
     * quoted, so it keeps its exact or wildcard match. matchPattern reads a wildcard (text ending with a *) also as a
     * regular expression, so a wildcard keeps both: Camel* becomes Camel.*|(?:Camel*), which matches CamelFoo and Came,
     * and my.app* becomes \Qmy.app\E.*|(?:my.app*), which matches my.app.id. A wildcard such as kafka.* stays one
     * regular expression, which already matches every name that starts with kafka.
     * <p>
     * Two readings are not kept. A regular expression no longer matches its own text as an exact name: a+b matches aab,
     * not the name a+b. A property placeholder is combined before it is resolved: {{keep}} is quoted, so it keeps the
     * exact name it resolves to, and {{keep}}* the names that start with it, but not a wildcard or a regular expression
     * it resolves to.
     *
     * @param  patterns the patterns, <code>null</code> elements are ignored
     * @return          the combined pattern, or <code>null</code> if there is no pattern
     */
    public static String combinePatterns(String... patterns) {
        if (patterns == null) {
            return null;
        }
        List<String> list = Arrays.stream(patterns).filter(Objects::nonNull).toList();
        if (list.size() <= 1) {
            return list.isEmpty() ? null : list.get(0);
        }
        StringJoiner answer = new StringJoiner("|");
        for (String pattern : list) {
            answer.add(asRegex(pattern));
        }
        return answer.toString();
    }

    private static String asRegex(String pattern) {
        if (!pattern.endsWith("*")) {
            if (!hasRegexChars(pattern)) {
                return pattern;
            }
            return isRegex(pattern) ? "(?:" + pattern + ")" : Pattern.quote(pattern);
        }
        String text = pattern.substring(0, pattern.length() - 1);
        String wildcard = (hasRegexChars(text) ? Pattern.quote(text) : text) + ".*";
        if (text.isEmpty() || !isRegex(pattern)) {
            return wildcard;
        }
        String head = text.substring(0, text.length() - 1);
        if (text.endsWith(".") && matchRegex(head, head)) {
            // head.* matches every name that starts with head, so the names that start with head. too
            return "(?:" + pattern + ")";
        }
        return wildcard + "|(?:" + pattern + ")";
    }

    private static boolean hasRegexChars(String text) {
        return text.chars().anyMatch(c -> REGEX_METACHARS.indexOf(c) >= 0);
    }

    private static boolean isRegex(String pattern) {
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    public static boolean isExcludePatternMatch(String key, String... excludePatterns) {
        for (String pattern : excludePatterns) {
            if (matchPattern(key, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given a map, creates a set of all keys matching a pattern (except if explicitly excluded). This is usually used
     * to collect matching keys and properties for removal
     *
     * @param  map             A map
     * @param  pattern         The pattern to test
     * @param  excludePatterns An exclusion pattern that prevents a matching key to be added to the returned set
     * @return                 A {@link java.util.Set Set} instance with all the matching keys
     */
    public static Set<String> matchingSet(Map<String, Object> map, String pattern, String[] excludePatterns) {
        Set<String> toBeRemoved = null;
        // must use a set to store the keys to remove as we cannot walk using entrySet and remove at the same time
        // due concurrent modification error
        for (String key : map.keySet()) {
            if (PatternHelper.matchPattern(key, pattern)) {
                if (excludePatterns != null && PatternHelper.isExcludePatternMatch(key, excludePatterns)) {
                    continue;
                }
                if (toBeRemoved == null) {
                    toBeRemoved = new HashSet<>();
                }
                toBeRemoved.add(key);
            }
        }
        return toBeRemoved;
    }
}
