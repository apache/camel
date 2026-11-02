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

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternHelperTest {

    @Test
    void testSimplePatternExactMatch() {
        assertTrue(PatternHelper.matchSimplePattern("crm", "crm"));
        assertTrue(PatternHelper.matchSimplePattern("crm", "CRM"));
        assertTrue(PatternHelper.matchSimplePattern("CRM", "crm"));
    }

    @Test
    void testSimplePatternNoMatch() {
        assertFalse(PatternHelper.matchSimplePattern("crm", "erp"));
    }

    @Test
    void testSimplePatternWildcardAll() {
        assertTrue(PatternHelper.matchSimplePattern("crm", "*"));
        assertTrue(PatternHelper.matchSimplePattern("anything", "*"));
    }

    @Test
    void testSimplePatternWildcardPrefix() {
        assertTrue(PatternHelper.matchSimplePattern("crm-tools", "crm*"));
        assertTrue(PatternHelper.matchSimplePattern("CRM-Tools", "crm*"));
        assertFalse(PatternHelper.matchSimplePattern("erp-tools", "crm*"));
    }

    @Test
    void testSimplePatternNoRegexFallback() {
        // "a.b" as regex would match "axb" — simple pattern must NOT do this
        assertFalse(PatternHelper.matchSimplePattern("axb", "a.b"));
        // contrast with the full matchPattern which does fall back to regex
        assertTrue(PatternHelper.matchPattern("axb", "a.b"));
    }

    @Test
    void testSimplePatternNullSafety() {
        assertFalse(PatternHelper.matchSimplePattern(null, "crm"));
        assertFalse(PatternHelper.matchSimplePattern("crm", null));
        assertFalse(PatternHelper.matchSimplePattern(null, null));
    }

    @Test
    void testSimplePatternsMultiple() {
        String[] patterns = { "crm", "erp*" };
        assertTrue(PatternHelper.matchSimplePatterns("crm", patterns));
        assertTrue(PatternHelper.matchSimplePatterns("erp-tools", patterns));
        assertFalse(PatternHelper.matchSimplePatterns("billing", patterns));
    }

    @Test
    void testPatternTrailingStarAlsoMeansZeroOfThePreviousCharacter() {
        // removeHeaders("Camel*") must keep removing a header named Came, as * is also a regex quantifier
        assertTrue(PatternHelper.matchPattern("Came", "Camel*"));
        assertTrue(PatternHelper.matchPattern("came", "Camel*"));
        assertTrue(PatternHelper.matchPattern("", "a*"));
        assertFalse(PatternHelper.matchPattern("Cam", "Camel*"));
        assertFalse(PatternHelper.matchPattern("Camx", "Camel*"));
    }

    @Test
    void testPatternTrailingStarAppliesToTheWholeCodePoint() {
        // the previous character can be a surrogate pair, which is two chars in the name length
        assertTrue(PatternHelper.matchPattern("a", "a\uD83D\uDE00*"));
        assertFalse(PatternHelper.matchPattern("a\uD83D", "a\uD83D\uDE00*"));
    }

    @Test
    void testPatternRegexCaseFoldingIsAsciiOnly() {
        // equalsIgnoreCase folds these, the regex does not: the zero repeat form must not match
        assertFalse(PatternHelper.matchPattern("\u00E4", "\u00C4b*"));
    }

    @Test
    void testPatternKeepsMatchingAsRegex() {
        assertTrue(PatternHelper.matchPattern("b", "a|b"));
        assertTrue(PatternHelper.matchPattern("b", "[ab]"));
        assertTrue(PatternHelper.matchPattern("aa", "a{2}"));
        assertTrue(PatternHelper.matchPattern("a", "^a$"));
        assertTrue(PatternHelper.matchPattern("a", "(a)"));
        assertTrue(PatternHelper.matchPattern("aa", "a+"));
        assertTrue(PatternHelper.matchPattern("", "a?"));
        assertTrue(PatternHelper.matchPattern("5", "\\d"));
        assertTrue(PatternHelper.matchPattern("abc", "a.c"));
        assertTrue(PatternHelper.matchPattern("xbb", "x.b*"));
    }

    @Test
    void testPatternInvalidRegexDoesNotMatch() {
        assertFalse(PatternHelper.matchPattern("a", "["));
        assertFalse(PatternHelper.matchPattern("a", "(a"));
    }

    @Test
    void testPatternGivesTheSameResultAsAlwaysTryingTheRegex() {
        String[] names = {
                "", "a", "A", "b", "ab", "aB", "abb", "abc", "Came", "came", "Camel", "CAMEL", "Camell",
                "CamelFoo", "Cam", "Camx", "a.b", "a*", "*", "\u00E4", "\u00C4", "a\uD83D\uDE00", "\uD83D\uDE00", "a b" };
        String[] patterns = {
                "", "a", "ab", "abc", "a*", "ab*", "abc*", "Camel", "Camel*", "camel*", "Camel.*", "*",
                "**", "a**", "*a", "a.b", "a.b*", "a|b", "[ab]", "[ab]*", "(a)*", "a+", "a+*", "a?", "a?*", "\\*",
                "a\\*", "a{2}", "a}", "a]", "[", "(a", "\u00C4", "\u00C4b*", "\u00E4*", "a\uD83D\uDE00*",
                "\uD83D\uDE00*", "a b", "a b*" };
        for (String name : names) {
            for (String pattern : patterns) {
                assertEquals(alwaysTryingTheRegex(name, pattern), PatternHelper.matchPattern(name, pattern),
                        () -> "name=" + name + " pattern=" + pattern);
            }
        }
    }

    // the documented match rules: exact, match all, wildcard and last the regular expression for every other name
    private static boolean alwaysTryingTheRegex(String name, String pattern) {
        if (name.equalsIgnoreCase(pattern) || "*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith("*")) {
            String text = pattern.substring(0, pattern.length() - 1);
            if (name.toLowerCase(Locale.ENGLISH).startsWith(text.toLowerCase(Locale.ENGLISH))) {
                return true;
            }
        }
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(name).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
