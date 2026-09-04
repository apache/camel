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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpHelperStripUriPrefixTest {

    static Stream<Arguments> stripCases() {
        return Stream.of(
                Arguments.of("/reverse-proxy/get", "/reverse-proxy", "/get"),
                Arguments.of("/reverse-proxy", "/reverse-proxy", "/"),
                Arguments.of("/reverse-proxy/", "/reverse-proxy", "/"),
                Arguments.of("/reverse-proxy/a/b/", "/reverse-proxy", "/a/b/"),
                // leading slash on the consumer path is optional
                Arguments.of("/reverse-proxy/get", "reverse-proxy", "/get"),
                // Vert.x style trailing wildcard on the consumer path
                Arguments.of("/reverse-proxy/get", "/reverse-proxy*", "/get"),
                // boundary: only strip on a '/' (or end of string) boundary
                Arguments.of("/reverse-proxyfoo", "/reverse-proxy", "/reverse-proxyfoo"),
                Arguments.of("/other/x", "/reverse-proxy", "/other/x"),
                // case-insensitive literal segment match
                Arguments.of("/Reverse-Proxy/get", "/reverse-proxy", "/get"),
                Arguments.of("/REVERSE-PROXY", "/reverse-proxy", "/"),
                // REST-DSL {name} placeholder segment matches any single non-empty segment
                Arguments.of("/user/123/orders", "/user/{id}", "/orders"),
                Arguments.of("/user/123", "/user/{id}", "/"),
                // request shorter than consumer path never matches
                Arguments.of("/reverse", "/reverse-proxy", "/reverse"),
                Arguments.of("/", "/reverse-proxy/sub", "/"),
                // "no prefix to strip" cases: consumer path of "/", "" or null always returns input unchanged -
                // this is what makes platform-http:proxy (whose getPath() is "/") provably unaffected
                Arguments.of("/reverse-proxy/get", "/", "/reverse-proxy/get"),
                Arguments.of("/reverse-proxy/get", "", "/reverse-proxy/get"),
                Arguments.of("/reverse-proxy/get", null, "/reverse-proxy/get"),
                Arguments.of("/reverse-proxy/get", "   ", "/reverse-proxy/get"));
    }

    @ParameterizedTest
    @MethodSource("stripCases")
    void stripsAsExpected(String requestPath, String consumerPath, String expected) {
        assertEquals(expected, HttpHelper.stripUriPrefix(requestPath, consumerPath));
    }

    @Test
    void nullRequestPathReturnsNull() {
        assertNull(HttpHelper.stripUriPrefix(null, "/reverse-proxy"));
        assertNull(HttpHelper.stripUriPrefix(null, "/"));
        assertNull(HttpHelper.stripUriPrefix(null, null));
    }
}
