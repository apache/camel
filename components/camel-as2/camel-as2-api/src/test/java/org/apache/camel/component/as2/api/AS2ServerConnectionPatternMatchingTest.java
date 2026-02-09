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
package org.apache.camel.component.as2.api;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for AS2ServerConnection pattern matching logic
 */
public class AS2ServerConnectionPatternMatchingTest {

    /**
     * Simulates the getCompiledPattern logic from AS2ServerConnection
     */
    private Pattern getCompiledPattern(String pattern) {
        String[] segments = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            regex.append(Pattern.quote(segments[i]));
            if (i < segments.length - 1) {
                regex.append(".*");
            }
        }
        return Pattern.compile(regex.toString());
    }

    @Test
    public void testWildcardPatternMatching() {
        Pattern pattern = getCompiledPattern("/consumer/*");

        assertTrue(pattern.matcher("/consumer/orders").matches());
        assertTrue(pattern.matcher("/consumer/invoices").matches());
        assertFalse(pattern.matcher("/admin/orders").matches());
    }

    @Test
    public void testRegexSpecialCharactersWithWildcard() {
        Pattern pattern = getCompiledPattern("/api/v2(3)/*");

        // Should match - parentheses are literal
        assertTrue(pattern.matcher("/api/v2(3)/orders").matches());
        assertTrue(pattern.matcher("/api/v2(3)/invoices").matches());

        // Should NOT match - parentheses are literal, not regex grouping
        assertFalse(pattern.matcher("/api/v23/orders").matches());
        assertFalse(pattern.matcher("/api/v2/orders").matches());
    }

    @Test
    public void testRegexSpecialCharactersExactMatch() {
        Pattern pattern = getCompiledPattern("/api/v1.2/endpoint");

        // Should match - dot is literal
        assertTrue(pattern.matcher("/api/v1.2/endpoint").matches());

        // Should NOT match - dot is literal, not regex "any character"
        assertFalse(pattern.matcher("/api/v1X2/endpoint").matches());
    }

    @Test
    public void testMultipleWildcards() {
        Pattern pattern = getCompiledPattern("/api/*/v2(3)/*");

        assertTrue(pattern.matcher("/api/v1/v2(3)/orders").matches());
        assertTrue(pattern.matcher("/api/test/v2(3)/invoices").matches());
        assertFalse(pattern.matcher("/api/v1/v23/orders").matches());
    }
}
