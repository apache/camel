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
package org.apache.camel.component.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosPatternsTest {

    @Test
    void starMatchesAnyCharacters() {
        assertThat(ChaosPatterns.matches("orders", "orders")).isTrue();
        assertThat(ChaosPatterns.matches("orders", "orders2")).isFalse();
        assertThat(ChaosPatterns.matches("order*", "orders")).isTrue();
        assertThat(ChaosPatterns.matches("order*", "order")).isTrue();
        assertThat(ChaosPatterns.matches("*ders", "orders")).isTrue();
        assertThat(ChaosPatterns.matches("o*r*s", "orders")).isTrue();
        assertThat(ChaosPatterns.matches("o*x*s", "orders")).isFalse();
        assertThat(ChaosPatterns.matches("*", "")).isTrue();
        assertThat(ChaosPatterns.matches("**", "abc")).isTrue();
        assertThat(ChaosPatterns.matches("a", "")).isFalse();
    }

    @Test
    void otherCharactersAreLiteral() {
        // no regular expressions: . ? [ ( + are plain characters
        assertThat(ChaosPatterns.matches("a.c", "abc")).isFalse();
        assertThat(ChaosPatterns.matches("a.c", "a.c")).isTrue();
        assertThat(ChaosPatterns.matches("a?c", "abc")).isFalse();
        assertThat(ChaosPatterns.matches("(a+)+", "aaaa")).isFalse();
        assertThat(ChaosPatterns.matches("[ab]", "a")).isFalse();
    }

    @Test
    void matchesUriWithOrWithoutSlashesAndQuery() {
        assertThat(ChaosPatterns.matchesUri("kafka:orders*", "kafka://orders?brokers=b1")).isTrue();
        assertThat(ChaosPatterns.matchesUri("kafka://orders", "kafka:orders?brokers=b1")).isTrue();
        assertThat(ChaosPatterns.matchesUri("kafka:orders", "kafka://orders")).isTrue();
        assertThat(ChaosPatterns.matchesUri("kafka:orders", "kafka://orders2")).isFalse();
        assertThat(ChaosPatterns.matchesUri("kafka:orders?brokers=b1", "kafka://orders?brokers=b1")).isTrue();
        assertThat(ChaosPatterns.matchesUri("kafka:*", "file://inbox")).isFalse();
        assertThat(ChaosPatterns.matchesUri("*inbox*", "file://inbox?delay=10")).isTrue();
    }

    @Test
    void matchesSchemeOfUri() {
        assertThat(ChaosPatterns.matchesScheme("kafka", "kafka://orders")).isTrue();
        assertThat(ChaosPatterns.matchesScheme("kaf*", "kafka://orders")).isTrue();
        assertThat(ChaosPatterns.matchesScheme("kafka", "kafka2://orders")).isFalse();
        assertThat(ChaosPatterns.matchesScheme("netty-http", "netty-http:http://localhost")).isTrue();
        assertThat(ChaosPatterns.matchesScheme("noscheme", "noscheme")).isTrue();
        assertThat(ChaosPatterns.matchesScheme("*orders", "kafka://orders")).isFalse();
    }

    @Test
    void matchingWithoutNewStringsMatchesAsOnTheSubstrings() {
        // the hooks match the precomputed pattern against the URI without making the URI without slashes or query:
        // the answer must be the same as matching the substrings, as the core did
        Random random = new Random(42);
        String[] parts = { "a", "b", ":", "/", "//", "?", "*", "x=1", "kafka", "orders" };
        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            String uri = randomText(random, parts, 6);
            String pattern = randomText(random, parts, 4);
            String normalised = ChaosPatterns.withoutSlashes(pattern);
            if (ChaosPatterns.matchesUriPattern(normalised, uri) != matchesUriAsSubstrings(pattern, uri)) {
                mismatches.add("uri: pattern " + pattern + " uri " + uri);
            }
            if (ChaosPatterns.matchesScheme(pattern, uri) != ChaosPatterns.matches(pattern, ChaosPatterns.scheme(uri))) {
                mismatches.add("scheme: pattern " + pattern + " uri " + uri);
            }
        }
        assertThat(mismatches).isEmpty();
    }

    // matchesUri as the core had it: on the substrings without the slashes and the query
    private static boolean matchesUriAsSubstrings(String pattern, String uri) {
        String p = ChaosPatterns.withoutSlashes(pattern);
        String u = ChaosPatterns.withoutSlashes(uri);
        if (ChaosPatterns.matches(p, u)) {
            return true;
        }
        int query = u.indexOf('?');
        return query > 0 && ChaosPatterns.matches(p, u.substring(0, query));
    }

    private static String randomText(Random random, String[] parts, int max) {
        StringBuilder sb = new StringBuilder();
        int n = random.nextInt(max + 1);
        for (int i = 0; i < n; i++) {
            sb.append(parts[random.nextInt(parts.length)]);
        }
        return sb.toString();
    }

    @Test
    void schemeOfUri() {
        assertThat(ChaosPatterns.scheme("kafka://orders")).isEqualTo("kafka");
        assertThat(ChaosPatterns.scheme("netty-http:http://localhost")).isEqualTo("netty-http");
        assertThat(ChaosPatterns.scheme("noscheme")).isEqualTo("noscheme");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void adversarialPatternsTakeLinearTime() {
        // patterns that make a backtracking matcher exponential: many stars against a text that almost matches
        String text = "a".repeat(5000);
        String pattern = "*a".repeat(1000) + "b";
        assertThat(ChaosPatterns.matches(pattern, text)).isFalse();
        assertThat(ChaosPatterns.matches("*".repeat(2000) + "b", text)).isFalse();
        assertThat(ChaosPatterns.matches("*".repeat(2000), text)).isTrue();
    }
}
