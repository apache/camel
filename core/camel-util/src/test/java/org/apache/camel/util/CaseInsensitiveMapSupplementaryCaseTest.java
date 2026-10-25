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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keys that are equal by {@link String#equalsIgnoreCase(String)} must be the same key in the map, also when they differ
 * in the case of a supplementary character (a surrogate pair), which equalsIgnoreCase folds as one code point.
 */
class CaseInsensitiveMapSupplementaryCaseTest {

    // DESERET CAPITAL LETTER LONG I (U+10400) and DESERET SMALL LETTER LONG I (U+10428)
    private static final String UPPER = new String(Character.toChars(0x10400));
    private static final String LOWER = new String(Character.toChars(0x10428));

    @Test
    void supplementaryLetterInTheOtherCaseIsTheSameKey() {
        assertTrue(UPPER.equalsIgnoreCase(LOWER));

        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("X-" + UPPER + "-Name", "a");
        assertEquals("a", map.get("x-" + LOWER + "-name"));
        assertTrue(map.containsKey("X-" + LOWER + "-NAME"));

        map.put("x-" + LOWER + "-name", "b");
        assertEquals(1, map.size());
        assertEquals("b", map.get("X-" + UPPER + "-Name"));
        assertEquals("b", map.remove("X-" + LOWER + "-Name"));
        assertTrue(map.isEmpty());
    }

    @Test
    void hashIsTheSameForEveryCodePointAndItsCaseVariants() {
        Set<String> inconsistent = new LinkedHashSet<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            String a = new String(Character.toChars(cp));
            int[] variants = {
                    Character.toUpperCase(cp), Character.toLowerCase(cp), Character.toTitleCase(cp),
                    Character.toLowerCase(Character.toUpperCase(cp)), Character.toUpperCase(Character.toLowerCase(cp)) };
            for (int v : variants) {
                String b = new String(Character.toChars(v));
                if (a.equalsIgnoreCase(b)
                        && CaseInsensitiveMap.caseInsensitiveHash(a) != CaseInsensitiveMap.caseInsensitiveHash(b)) {
                    inconsistent.add(Integer.toHexString(cp) + "/" + Integer.toHexString(v));
                }
            }
        }
        assertTrue(inconsistent.isEmpty(), inconsistent.size() + " pairs equal ignoring case hash differently, such as "
                                           + inconsistent.stream().limit(10).toList());
    }

    @Test
    void asciiAndBmpKeysKeepTheirBehaviour() {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("Content-Type", 1);
        // Turkish dotless i folds to i, as in equalsIgnoreCase
        map.put("t\u0131tle", 2);
        // KELVIN SIGN folds to k
        map.put("\u212Aey", 3);
        map.put("\u00C9t\u00E9", 4);
        map.put("\uD801", 5); // a lone surrogate is a key of its own

        assertEquals(1, map.get("content-type"));
        assertEquals(2, map.get("TITLE"));
        assertEquals(2, map.get("title"));
        assertEquals(3, map.get("KEY"));
        assertEquals(4, map.get("\u00E9T\u00C9"));
        assertEquals(5, map.get("\uD801"));
        assertEquals(List.of("Content-Type", "t\u0131tle", "\u212Aey", "\u00C9t\u00E9", "\uD801"),
                new ArrayList<>(map.keySet()));
    }
}
