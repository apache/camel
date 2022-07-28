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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
