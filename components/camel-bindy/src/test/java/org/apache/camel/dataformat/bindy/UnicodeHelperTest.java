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
package org.apache.camel.dataformat.bindy;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.camel.dataformat.bindy.UnicodeHelper.Method;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("javadoc")
public class UnicodeHelperTest {

    private static final Logger LOG = LoggerFactory.getLogger(UnicodeHelperTest.class);

    private static final String UCSTR = cps2String(
            0x1f645, // FACE WITH NO GOOD GESTURE; Basiszeichen (Geste)
            0x1f3ff, // EMOJI MODIFIER FITZPATRICK TYPE-6; Hautfarbe für #1
            0x200d,  // ZERO WIDTH JOINER [ZWJ]; Steuerzeichen zum Verbinden
            0x2642,  // MALE SIGN; Geschlecht für #1
            0xfe0f   // VARIATION SELECTOR-16 [VS16]; Darstellung als Piktogramm für #4
    );

    @Test
    public void testLengthCPs() {
        final UnicodeHelper lh = new UnicodeHelper("a", Method.CODEPOINTS);
        assertEquals(1, lh.length());

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.CODEPOINTS);
        assertEquals(1, lh2.length());

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.CODEPOINTS);
        assertEquals(5, lh3.length());

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.CODEPOINTS);
        assertEquals(7, lh4.length());

        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.CODEPOINTS);
        assertEquals(3, lh5.length());
    }

    @Test
    public void testLengthGrapheme() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        assertEquals(1, lh.length());

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.GRAPHEME);
        assertEquals(1, lh2.length());

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.GRAPHEME);
        assertEquals(1, lh3.length());

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.GRAPHEME);
        assertEquals(3, lh4.length());

        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.GRAPHEME);
        assertEquals(2, lh5.length());
    }

    @Test
    public void testSubstringCPs() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.CODEPOINTS);
        assertEquals("a", lh.substring(0));

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.CODEPOINTS);
        assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0));

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.CODEPOINTS);
        assertEquals(UCSTR, lh3.substring(0));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.CODEPOINTS);
        assertEquals(UCSTR + "A", lh4.substring(1));
        assertEquals(new String(Character.toChars(0x1f3ff)) + "\u200d\u2642\ufe0fA", lh4.substring(2));

        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.CODEPOINTS);
        assertEquals("\u035fh", lh5.substring(1));
    }

    @Test
    public void testSubstringGrapheme() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        assertEquals("a", lh.substring(0));

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.GRAPHEME);
        assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0));

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.GRAPHEME);
        assertEquals(UCSTR, lh3.substring(0));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.GRAPHEME);
        assertEquals(UCSTR + "A", lh4.substring(1));
        assertEquals("A", lh4.substring(2));

        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.GRAPHEME);
        assertEquals("h", lh5.substring(1));
    }

    @Test
    public void testSubstringCPs2() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.CODEPOINTS);
        assertEquals("a", lh.substring(0, 1));

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.CODEPOINTS);
        assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0, 1));

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.CODEPOINTS);
        assertEquals(new String(Character.toChars(0x1f645)), lh3.substring(0, 1));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.CODEPOINTS);
        assertEquals("a", lh4.substring(0, 1));
        assertEquals(new String(Character.toChars(0x1f645)), lh4.substring(1, 2));
        assertEquals(new String(Character.toChars(0x1f3ff)), lh4.substring(2, 3));
        assertEquals("a" + new String(Character.toChars(0x1f645)), lh4.substring(0, 2));

        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.CODEPOINTS);
        assertEquals("k", lh5.substring(0, 1));
        assertEquals("\u035f", lh5.substring(1, 2));
    }

    @Test
    public void testSubstringGrapheme2() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        assertEquals("a", lh.substring(0, 1));

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.GRAPHEME);
        assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0, 1));

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.GRAPHEME);
        assertEquals(UCSTR, lh3.substring(0, 1));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.GRAPHEME);
        assertEquals("a", lh4.substring(0, 1));
        assertEquals(UCSTR, lh4.substring(1, 2));
        assertEquals("A", lh4.substring(2, 3));
        assertEquals("a" + UCSTR, lh4.substring(0, 2));

        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.GRAPHEME);
        assertEquals("k\u035f", lh5.substring(0, 1));
        assertEquals("h", lh5.substring(1, 2));
    }

    @Test
    public void testIndexOf() {
        final UnicodeHelper lh = new UnicodeHelper("a", Method.CODEPOINTS);
        assertEquals(-1, lh.indexOf("b"));

        final UnicodeHelper lh2 = new UnicodeHelper(
                "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A" + "k\u035fh" + "z"
                                                    + "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A"
                                                    + "k\u035fh" + "z",
                Method.CODEPOINTS);

        assertEquals(1, lh2.indexOf(new String(Character.toChars(0x1f600))));
        assertEquals(14, lh2.indexOf(new String(Character.toChars(0x1f600)), 13));

        assertEquals(3, lh2.indexOf(UCSTR));
        assertEquals(16, lh2.indexOf(UCSTR, 13));

        assertEquals(10, lh2.indexOf("\u035f"));
        assertEquals(23, lh2.indexOf("\u035f", 13));
    }

    @Test
    public void testIndexOf2() {
        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        assertEquals(-1, lh.indexOf("b"));

        final UnicodeHelper lh2 = new UnicodeHelper(
                "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A" + "k\u035fh" + "z"
                                                    + "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A"
                                                    + "k\u035fh" + "z",
                Method.GRAPHEME);

        assertEquals(1, lh2.indexOf(new String(Character.toChars(0x1f600))));
        assertEquals(9, lh2.indexOf(new String(Character.toChars(0x1f600)), 8));

        assertEquals(3, lh2.indexOf(UCSTR));
        assertEquals(11, lh2.indexOf(UCSTR), 8);

        final UnicodeHelper lh3 = new UnicodeHelper("mm̂mm̂m", Method.GRAPHEME);
        assertEquals(0, lh3.indexOf("m"));
        assertEquals(2, lh3.indexOf("m", 1));
        assertEquals(3, lh3.indexOf("m̂", 2));
    }

    private static String cps2String(final int... cps) {
        final StringBuilder buf = new StringBuilder();
        for (int cp : cps) {
            buf.append(Character.toChars(cp));
        }
        final String result = buf.toString();

        if (LOG.isDebugEnabled()) {
            final String cpStr = Arrays.stream(cps).boxed()
                    .map(i -> "0x" + Integer.toString(i, 16))
                    .collect(Collectors.joining(", "));
            LOG.debug("Built string '{}' from CPs [ {} ].", result, cpStr);
        }

        return result;
    }
}
