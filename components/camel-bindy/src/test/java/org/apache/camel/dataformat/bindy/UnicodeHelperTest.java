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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.camel.dataformat.bindy.UnicodeHelper.Method;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Assert.assertEquals(1, lh.length());
        
        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.CODEPOINTS);
        Assert.assertEquals(1, lh2.length());

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.CODEPOINTS);
        Assert.assertEquals(5, lh3.length());

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.CODEPOINTS);
        Assert.assertEquals(7, lh4.length());
        
        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.CODEPOINTS);
        Assert.assertEquals(3, lh5.length());
    }    

    @Test
    public void testLengthGrapheme() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        Assert.assertEquals(1, lh.length());
        
        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.GRAPHEME);
        Assert.assertEquals(1, lh2.length());

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.GRAPHEME);
        Assert.assertEquals(1, lh3.length());

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.GRAPHEME);
        Assert.assertEquals(3, lh4.length());
        
        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.GRAPHEME);
        Assert.assertEquals(2, lh5.length());
    }
    
    @Test
    public void testSubstringCPs() throws FileNotFoundException, IOException {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.CODEPOINTS);
        Assert.assertEquals("a", lh.substring(0));

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.CODEPOINTS);
        Assert.assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0));

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.CODEPOINTS);
        Assert.assertEquals(UCSTR, lh3.substring(0));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.CODEPOINTS);
        Assert.assertEquals(UCSTR + "A", lh4.substring(1));
        Assert.assertEquals(new String(Character.toChars(0x1f3ff)) + "\u200d\u2642\ufe0fA", lh4.substring(2));
        
        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.CODEPOINTS);
        Assert.assertEquals("\u035fh", lh5.substring(1));
    }    

    @Test
    public void testSubstringGrapheme() throws FileNotFoundException, IOException {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        Assert.assertEquals("a", lh.substring(0));

        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.GRAPHEME);
        Assert.assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0));

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.GRAPHEME);
        Assert.assertEquals(UCSTR, lh3.substring(0));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.GRAPHEME);
        Assert.assertEquals(UCSTR + "A", lh4.substring(1));
        Assert.assertEquals("A", lh4.substring(2));
        
        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.GRAPHEME);
        Assert.assertEquals("h", lh5.substring(1));
    }    
    
    @Test
    public void testSubstringCPs2() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.CODEPOINTS);
        Assert.assertEquals("a", lh.substring(0, 1));
        
        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.CODEPOINTS);
        Assert.assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0, 1));    

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.CODEPOINTS);
        Assert.assertEquals(new String(Character.toChars(0x1f645)), lh3.substring(0, 1));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.CODEPOINTS);
        Assert.assertEquals("a", lh4.substring(0, 1));
        Assert.assertEquals(new String(Character.toChars(0x1f645)), lh4.substring(1, 2));
        Assert.assertEquals(new String(Character.toChars(0x1f3ff)), lh4.substring(2, 3));
        Assert.assertEquals("a" + new String(Character.toChars(0x1f645)), lh4.substring(0, 2));
        
        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.CODEPOINTS);
        Assert.assertEquals("k", lh5.substring(0, 1));
        Assert.assertEquals("\u035f", lh5.substring(1, 2));        
    }        
    
    @Test
    public void testSubstringGrapheme2() {

        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        Assert.assertEquals("a", lh.substring(0, 1));
        
        final UnicodeHelper lh2 = new UnicodeHelper(new String(Character.toChars(0x1f600)), Method.GRAPHEME);
        Assert.assertEquals(new String(Character.toChars(0x1f600)), lh2.substring(0, 1));    

        final UnicodeHelper lh3 = new UnicodeHelper(UCSTR, Method.GRAPHEME);
        Assert.assertEquals(UCSTR, lh3.substring(0, 1));

        final UnicodeHelper lh4 = new UnicodeHelper("a" + UCSTR + "A", Method.GRAPHEME);
        Assert.assertEquals("a", lh4.substring(0, 1));
        Assert.assertEquals(UCSTR, lh4.substring(1, 2));
        Assert.assertEquals("A", lh4.substring(2, 3));
        Assert.assertEquals("a" + UCSTR, lh4.substring(0, 2));
        
        final UnicodeHelper lh5 = new UnicodeHelper("k\u035fh", Method.GRAPHEME);
        Assert.assertEquals("k\u035f", lh5.substring(0, 1));
        Assert.assertEquals("h", lh5.substring(1, 2));        
    }
    
    @Test
    public void testIndexOf() {
        final UnicodeHelper lh = new UnicodeHelper("a", Method.CODEPOINTS);
        Assert.assertEquals(-1, lh.indexOf("b"));

        final UnicodeHelper lh2 = new UnicodeHelper(
            "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A" + "k\u035fh" + "z"
            + "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A" + "k\u035fh" + "z", 
            Method.CODEPOINTS);

        Assert.assertEquals(1, lh2.indexOf(new String(Character.toChars(0x1f600))));
        Assert.assertEquals(14, lh2.indexOf(new String(Character.toChars(0x1f600)), 13));
        
        Assert.assertEquals(3, lh2.indexOf(UCSTR));
        Assert.assertEquals(16, lh2.indexOf(UCSTR, 13));

        Assert.assertEquals(10, lh2.indexOf("\u035f"));
        Assert.assertEquals(23, lh2.indexOf("\u035f", 13));
    }

    @Test
    public void testIndexOf2() {
        final UnicodeHelper lh = new UnicodeHelper("a", Method.GRAPHEME);
        Assert.assertEquals(-1, lh.indexOf("b"));

        final UnicodeHelper lh2 = new UnicodeHelper(
            "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A" + "k\u035fh" + "z"
            + "a" + new String(Character.toChars(0x1f600)) + "a" + UCSTR + "A" + "k\u035fh" + "z", 
            Method.GRAPHEME
        );

        Assert.assertEquals(1, lh2.indexOf(new String(Character.toChars(0x1f600))));
        Assert.assertEquals(9, lh2.indexOf(new String(Character.toChars(0x1f600)), 8));

        Assert.assertEquals(3, lh2.indexOf(UCSTR));
        Assert.assertEquals(11, lh2.indexOf(UCSTR), 8);
        
        final UnicodeHelper lh3 = new UnicodeHelper("mm̂mm̂m", Method.GRAPHEME);
        Assert.assertEquals(0, lh3.indexOf("m"));
        Assert.assertEquals(2, lh3.indexOf("m", 1));
        Assert.assertEquals(3, lh3.indexOf("m̂", 2));
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
