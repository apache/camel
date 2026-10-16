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
package org.apache.camel.component.netty.http.handlers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A request path carrying {@code .} or {@code ..} segments is rejected when a security constraint is configured;
 * {@code removeDotSegments} (RFC 3986 section 5.2.4) resolves the path so those segments can be detected (the result
 * differs from the input). A path without dot-segments, including a leading-dot file name, is returned unchanged.
 * Excess {@code ..} segments cannot climb above the root, so the result never contains a {@code ..} segment.
 */
class HttpServerChannelHandlerDotSegmentTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # input                 | expected
            /foo/admin/x            | /foo/admin/x
            /foo/./admin/x          | /foo/admin/x
            /foo/../admin/x         | /admin/x
            /app/../admin/x         | /admin/x
            /foo/../../admin/x      | /admin/x
            /foo/sub/../admin/x     | /foo/admin/x
            /foo/a/../../../admin   | /admin
            /foo/admin/.            | /foo/admin/
            /foo/admin/..           | /foo/
            /..                     | /
            /admin/file.txt         | /admin/file.txt
            /admin/.hidden/secret   | /admin/.hidden/secret
            /                       | /
            """)
    void resolvesDotSegments(String input, String expected) {
        assertEquals(expected, HttpServerChannelHandler.removeDotSegments(input), () -> "input " + input);
    }
}
