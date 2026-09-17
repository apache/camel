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
package org.apache.camel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the static {@link FunctionGraphUtils#extractJsonFieldAsString} helper. It must not assume 'body' is a
 * JSON object (HTTP-triggered functions return it as a JSON-encoded string/primitive) and must tolerate an absent/null
 * field and a null/blank result.
 */
public class FunctionGraphUtilsTest {

    @Test
    public void extractObjectBodyReturnsItsJson() {
        String result = FunctionGraphUtils.extractJsonFieldAsString("{\"body\":{\"orderId\":1,\"ok\":true}}", "body");
        assertEquals("{\"orderId\":1,\"ok\":true}", result);
    }

    @Test
    public void extractStringBodyReturnsTheRawString() {
        // previously threw ClassCastException because getAsJsonObject was forced on a string member
        String result = FunctionGraphUtils.extractJsonFieldAsString("{\"body\":\"hello world\"}", "body");
        assertEquals("hello world", result);
    }

    @Test
    public void extractNumericBodyReturnsItsValue() {
        String result = FunctionGraphUtils.extractJsonFieldAsString("{\"body\":42}", "body");
        assertEquals("42", result);
    }

    @Test
    public void extractAbsentFieldReturnsNull() {
        // previously threw NullPointerException
        assertNull(FunctionGraphUtils.extractJsonFieldAsString("{\"statusCode\":200}", "body"));
    }

    @Test
    public void extractNullJsonReturnsNull() {
        // a function that returns no result (response.getResult() == null) hits the root == null guard
        assertNull(FunctionGraphUtils.extractJsonFieldAsString(null, "body"));
    }
}
