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

import org.junit.jupiter.api.Test;

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
}
