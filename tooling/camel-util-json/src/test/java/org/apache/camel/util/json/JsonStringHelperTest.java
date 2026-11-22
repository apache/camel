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
package org.apache.camel.util.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonStringHelperTest {

    @Test
    public void testIsValid() {
        for (String json : new String[] {
                "{}", "[]", "{\"a\": 1}", " [1, 2.5, -3e+2, 0, -0.1E-3] ", "{\"a\": {\"b\": [true, false, null]}}",
                "\"text \\\"quoted\\\" \\u00e9 \\n\"", "123", "true", "null", "{\"\": \"\"}" }) {
            assertTrue(JsonStringHelper.isValid(json), json);
        }
        for (String json : new String[] {
                null, "", "  ", "Hello", "{", "}", "[1", "[1]]", "{\"a\":1,}", "[1,]", "[,1]", "{,}", "{'a': 1}",
                "{a: 1}", "{\"a\" 1}", "{\"a\":}", "{\"a\":1} x", "{}{}", "[01]", "[1.]", "[.5]", "[1e]", "[-]",
                "[+1]", "[NaN]", "[tru]", "[truex]", "\"unterminated", "\"bad \\x escape\"", "\"\\u12\"", "\"\\u12zz\"",
                "\"control \u0001 char\"", "[1 2]", "{\"a\":1 \"b\":2}" }) {
            assertFalse(JsonStringHelper.isValid(json), json);
        }
    }

    @Test
    public void testDeepNesting() {
        // no recursion: very deep input is checked without a StackOverflowError
        int depth = 100_000;
        String json = "[".repeat(depth) + "]".repeat(depth);
        assertTrue(JsonStringHelper.isValid(json));
        assertFalse(JsonStringHelper.isValid("[".repeat(depth)));
    }

    @Test
    public void testCompact() {
        String json = "{\n  \"name\" : \"Camel  in Action\",\n  \"ids\" : [ 1, 2.50, 1.0e3 ],\n  \"empty\" : { }\n}\n";
        assertEquals("{\"name\":\"Camel  in Action\",\"ids\":[1,2.50,1.0e3],\"empty\":{}}", JsonStringHelper.compact(json));

        // the inverse of pretty print
        String pretty = Jsoner.prettyPrint("{\"a\":[1,{\"b\":\"x y\"}],\"c\":true}");
        assertEquals("{\"a\":[1,{\"b\":\"x y\"}],\"c\":true}", JsonStringHelper.compact(pretty));

        assertEquals("\"a\\tb\"", JsonStringHelper.compact("  \"a\\tb\"  "));
        assertNull(JsonStringHelper.compact("{\"a\": 1,}"));
        assertNull(JsonStringHelper.compact(null));
    }
}
