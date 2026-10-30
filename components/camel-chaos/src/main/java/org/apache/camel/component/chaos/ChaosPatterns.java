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

/**
 * The patterns of the rule targets: {@code *} matches any characters (also none), every other character matches itself.
 * There are no regular expressions, so a pattern cannot backtrack: matching takes at most the length of the pattern
 * times the length of the text. Unlike {@code PatternHelper} (used by {@code interceptSendToEndpoint}), the match is
 * case-sensitive, a star can stand anywhere, and there is no fallback to a regular expression, as the rules can come
 * from a tool.
 */
final class ChaosPatterns {

    private ChaosPatterns() {
    }

    /**
     * Whether the pattern matches the whole text.
     */
    static boolean matches(String pattern, String text) {
        int p = 0;
        int t = 0;
        // the position after the last star in the pattern, and the position in the text it matched up to
        int star = -1;
        int mark = 0;
        while (t < text.length()) {
            if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = ++p;
                mark = t;
            } else if (p < pattern.length() && pattern.charAt(p) == text.charAt(t)) {
                p++;
                t++;
            } else if (star >= 0) {
                // let the last star match one more character, and try the rest of the pattern again from there
                p = star;
                t = ++mark;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

    /**
     * Whether the pattern matches the endpoint URI, with or without its query parameters. The {@code //} after the
     * scheme does not count, so {@code kafka:orders*} matches {@code kafka://orders?brokers=b1} too.
     */
    static boolean matchesUri(String pattern, String uri) {
        String p = withoutSlashes(pattern);
        String u = withoutSlashes(uri);
        if (matches(p, u)) {
            return true;
        }
        int query = u.indexOf('?');
        return query > 0 && matches(p, u.substring(0, query));
    }

    /**
     * The scheme of the URI (the text before the first colon), or the URI when it has none.
     */
    static String scheme(String uri) {
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : uri;
    }

    private static String withoutSlashes(String uri) {
        int colon = uri.indexOf(':');
        if (colon > 0 && uri.startsWith("//", colon + 1)) {
            return uri.substring(0, colon + 1) + uri.substring(colon + 3);
        }
        return uri;
    }
}
