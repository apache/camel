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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StringQuoteHelperTest {

    @Test
    public void testJsonQuoteEscapesSpecialCharacters() {
        Assertions.assertEquals("\"He said \\\"hi\\\"\"", StringQuoteHelper.jsonQuote("He said \"hi\""));
        Assertions.assertEquals("\"line1\\nline2\"", StringQuoteHelper.jsonQuote("line1\nline2"));
        Assertions.assertEquals("\"a\\\\b\"", StringQuoteHelper.jsonQuote("a\\b"));
    }

    @Test
    public void testSplitBeanParametersTrim() throws Exception {
        String[] arr = StringQuoteHelper.splitSafeQuote("String.class ${body}, String.class Mars", ',', true, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("String.class ${body}", arr[0]);
        Assertions.assertEquals("String.class Mars", arr[1]);

        arr = StringQuoteHelper.splitSafeQuote("  String.class ${body}  , String.class Mars   ", ',', true, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("String.class ${body}", arr[0]);
        Assertions.assertEquals("String.class Mars", arr[1]);
    }

    @Test
    public void testSplitTrimKeepsSpacesInsideQuotes() {
        // the quoted text is kept as-is whether or not it is the last value
        String[] arr = StringQuoteHelper.splitSafeQuote("' a ', ' b '", ',', true, false);
        Assertions.assertArrayEquals(new String[] { " a ", " b " }, arr);

        arr = StringQuoteHelper.splitSafeQuote("  \" a \"  ,  \" b \"  ,c", ',', true, false);
        Assertions.assertArrayEquals(new String[] { " a ", " b ", "c" }, arr);

        // spaces outside the quotes are still trimmed
        arr = StringQuoteHelper.splitSafeQuote("x, ' b '  ", ',', true, false);
        Assertions.assertArrayEquals(new String[] { "x", " b " }, arr);

        // with keepQuotes the quotes protect the text
        arr = StringQuoteHelper.splitSafeQuote(" ' a ' , ' b ' ", ',', true, true);
        Assertions.assertArrayEquals(new String[] { "' a '", "' b '" }, arr);

        // space as separator (such as exec arguments)
        arr = StringQuoteHelper.splitSafeQuote("'  a  ' b", ' ', true, false);
        Assertions.assertArrayEquals(new String[] { "  a  ", "b" }, arr);
    }

    @Test
    public void testSplitBeanParametersNoTrim() throws Exception {
        String[] arr = StringQuoteHelper.splitSafeQuote("String.class ${body}, String.class Mars", ',', false, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("String.class ${body}", arr[0]);
        Assertions.assertEquals(" String.class Mars", arr[1]);

        arr = StringQuoteHelper.splitSafeQuote("  String.class ${body}  , String.class Mars   ", ',', false, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("  String.class ${body}  ", arr[0]);
        Assertions.assertEquals(" String.class Mars   ", arr[1]);
    }

    @Test
    public void testSplitNested() {
        // a comma inside parenthesis or curly brackets is not a separator
        Assertions.assertArrayEquals(new String[] { "${body.substring(0, 3)}" },
                StringQuoteHelper.splitSafeQuote("${body.substring(0, 3)}", ',', true, true, true));
        Assertions.assertArrayEquals(new String[] { "${body}", "${header.v.replace('a', 'b')}" },
                StringQuoteHelper.splitSafeQuote("${body}, ${header.v.replace('a', 'b')}", ',', true, true, true));
        Assertions.assertArrayEquals(new String[] { "${body.substring(0, 4).substring(1, 3)}", "${replace(a,z,${header.v})}" },
                StringQuoteHelper.splitSafeQuote("${body.substring(0, 4).substring(1, 3)} , ${replace(a,z,${header.v})}",
                        ',', true, true, true));
        Assertions.assertArrayEquals(new String[] { "String.class ${body.substring(0, 3)}", "5" },
                StringQuoteHelper.splitSafeQuote("String.class ${body.substring(0, 3)}, 5", ',', true, true, true));
        Assertions.assertArrayEquals(new String[] { "{a, b}", "c" },
                StringQuoteHelper.splitSafeQuote("{a, b}, c", ',', true, true, true));
        // without nested the comma splits
        Assertions.assertArrayEquals(new String[] { "${body.substring(0", "3)}" },
                StringQuoteHelper.splitSafeQuote("${body.substring(0, 3)}", ',', true, true, false));
        // brackets inside quotes are text
        Assertions.assertArrayEquals(new String[] { "'a(b'", "c" },
                StringQuoteHelper.splitSafeQuote("'a(b', c", ',', true, true, true));
        Assertions.assertArrayEquals(new String[] { "\"{\"", "c" },
                StringQuoteHelper.splitSafeQuote("\"{\", c", ',', true, true, true));
        // a closing bracket without an opening bracket does not stop the splitting
        Assertions.assertArrayEquals(new String[] { "a)", "b", "c}", "d" },
                StringQuoteHelper.splitSafeQuote("a), b, c}, d", ',', true, true, true));
        // no trim and no keep quotes
        Assertions.assertArrayEquals(new String[] { "f(a, b)", " 'x, y'" },
                StringQuoteHelper.splitSafeQuote("f(a, b), 'x, y'", ',', false, true, true));
        Assertions.assertArrayEquals(new String[] { "f(a, b)", "x, y" },
                StringQuoteHelper.splitSafeQuote("f(a, b), 'x, y'", ',', true, false, true));
    }

    @Test
    public void testSplitNestedSameAsNotNestedWithoutCommaInBrackets() {
        String[] inputs = {
                "${body}, ${header.foo}", "'a,b', 5", "${body}, ${header.foo?['key']}", "*, true", "'World'",
                "String.class ${body}, String.class Mars", "  String.class ${body}  , String.class Mars   ",
                "null, 'a,b'", "'', ${body}", "\"\", 'x'", "${body.substring(1)}, ${header.foo.toUpperCase()}", "a,,b",
                ", a,", "'it''s', b" };
        for (String input : inputs) {
            for (boolean trim : new boolean[] { true, false }) {
                for (boolean keepQuotes : new boolean[] { true, false }) {
                    Assertions.assertArrayEquals(StringQuoteHelper.splitSafeQuote(input, ',', trim, keepQuotes),
                            StringQuoteHelper.splitSafeQuote(input, ',', trim, keepQuotes, true), input);
                }
            }
        }
        Assertions.assertNull(StringQuoteHelper.splitSafeQuote(null, ',', true, true, true));
    }

}
