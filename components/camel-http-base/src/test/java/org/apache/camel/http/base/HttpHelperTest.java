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

package org.apache.camel.http.base;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpHelperTest {

    @Test
    @DisplayName("Test that the placeholders can be parsed")
    void testEvalPlaceholdersCanParse() {
        Map<String, Object> headers = new HashMap<>();

        assertDoesNotThrow(() -> HttpHelper.evalPlaceholders(headers, "/some/url/value", "/some/url/{key}"),
                "Parsing the URL should not cause an exception");
        assertNotEquals(0, headers.size());
        assertEquals("value", headers.get("key"));
    }

    @Test
    @DisplayName("Test that the placeholders throw OutOfBoundsException if the sizes differ")
    void testEvalPlaceholdersOutOfBound() {
        Map<String, Object> headers = new HashMap<>();

        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> HttpHelper.evalPlaceholders(headers, "/some/url", "/some/url/{key}"),
                "The sizes of the URLs differ and it should throw an exception");
    }

    @Test
    @DisplayName("Test that the placeholders can eval if the given path is greater than the consumer path")
    void testEvalPlaceholdersOutOfBound2() {
        Map<String, Object> headers = new HashMap<>();

        assertDoesNotThrow(() -> HttpHelper.evalPlaceholders(headers, "/some/url/value", "/some/{key}"),
                "The provided path is greater than the consumer path, so it should not throw an exception");
        assertNotEquals(0, headers.size());
        assertEquals("url", headers.get("key"));
    }

    @Test
    void testDecodePathParameter() {
        assertEquals("café", HttpHelper.decodePathParameter("caf%C3%A9"));
        assertEquals("a/b", HttpHelper.decodePathParameter("a%2Fb"));
        assertEquals("a+b", HttpHelper.decodePathParameter("a%2Bb"));
        // a + is a literal in a path, and a malformed escape is kept
        assertEquals("a+b c", HttpHelper.decodePathParameter("a+b%20c"));
        assertEquals("100%", HttpHelper.decodePathParameter("100%"));
    }

    @Test
    void testDecodeFormComponent() {
        assertEquals("a b c", HttpHelper.decodeFormComponent("a+b%20c", StandardCharsets.UTF_8));
        assertEquals("café", HttpHelper.decodeFormComponent("caf%C3%A9", StandardCharsets.UTF_8));
        assertEquals("50%", HttpHelper.decodeFormComponent("50%25", StandardCharsets.UTF_8));
        assertEquals("plain", HttpHelper.decodeFormComponent("plain", StandardCharsets.UTF_8));
        // a % that is not followed by two hex digits is kept, where URLDecoder fails
        assertEquals("Save 50% now", HttpHelper.decodeFormComponent("Save 50% now", StandardCharsets.UTF_8));
        assertEquals("{\"discount\":\"50%\"}",
                HttpHelper.decodeFormComponent("{\"discount\":\"50%\"}", StandardCharsets.UTF_8));
        assertEquals("100%", HttpHelper.decodeFormComponent("100%", StandardCharsets.UTF_8));
        assertEquals("%4 %zz %é", HttpHelper.decodeFormComponent("%4+%zz+%é", StandardCharsets.UTF_8));
        assertEquals("é and %", HttpHelper.decodeFormComponent("%C3%A9+and+%", StandardCharsets.UTF_8));
        assertEquals("é", HttpHelper.decodeFormComponent("%E9", StandardCharsets.ISO_8859_1));
    }

    @Test
    void testIsValidHeaderName() {
        assertTrue(HttpHelper.isValidHeaderName("Content-Type"));
        assertTrue(HttpHelper.isValidHeaderName("X-Custom_Header.1"));
        assertTrue(HttpHelper.isValidHeaderName("!#$%&'*+-.^_`|~"));

        assertFalse(HttpHelper.isValidHeaderName(null));
        assertFalse(HttpHelper.isValidHeaderName(""));
        // the text of a form field without =, as sent by curl -d "some text"
        assertFalse(HttpHelper.isValidHeaderName("Hi, I'm alice@example.com. My order is late"));
        assertFalse(HttpHelper.isValidHeaderName("a b"));
        assertFalse(HttpHelper.isValidHeaderName("a:b"));
        assertFalse(HttpHelper.isValidHeaderName("a\r\nb"));
        assertFalse(HttpHelper.isValidHeaderName("café"));
    }
}
