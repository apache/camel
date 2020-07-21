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
package org.apache.camel.tooling.model;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class StringsTest {

    static Stream<Arguments> getClassShortNameTypeVarations() {
        return Stream.of(arguments("String", "String"), arguments("String", "java.lang.String"), arguments("List", "List<String>"), arguments("List", "java.util.List<String>"),
                arguments("List", "List<java.lang.String>"), arguments("List", "java.util.List.List<org.apache.camel.Exchange>"),
                arguments("List", "java.util.List<Map<String,Integer>>"), arguments("List", "java.util.List<Map<java.lang.String,Integer>>"),
                arguments("List", "java.util.List<Map<String,java.lang.Integer>>"), arguments("List", "java.util.List<Map<java.lang.String,java.lang.Integer>>"),
                arguments("List", "java.util.List<java.util.Map<java.lang.String,java.lang.Integer>>"));
    }

    @ParameterizedTest
    @MethodSource("getClassShortNameTypeVarations")
    public void getClassShortName(String expectedBaseClassName, String className) {
        assertEquals(expectedBaseClassName, Strings.getClassShortName(className));
    }

    @Test
    public void testWrap() {
        assertEquals("Hello WorldFoo Nar", wrap("HelloWorldFooNar", 8));
        assertEquals("UseMessageIDAs CorrelationID", wrap("useMessageIDAsCorrelationID", 25));
        assertEquals("ReplyToCacheLevelName", wrap("replyToCacheLevelName", 25));
        assertEquals("AllowReplyManagerQuick Stop", wrap("allowReplyManagerQuickStop", 25));
        assertEquals("AcknowledgementModeName", wrap("acknowledgementModeName", 25));
        assertEquals("ReplyToCacheLevelName", wrap("replyToCacheLevelName", 25));
        assertEquals("ReplyToOnTimeoutMax ConcurrentConsumers", wrap("replyToOnTimeoutMaxConcurrentConsumers", 25));
        assertEquals("ReplyToOnTimeoutMax ConcurrentConsumers", wrap("replyToOnTimeoutMaxConcurrentConsumers", 23));
        assertEquals("ReplyToMaxConcurrent Consumers", wrap("replyToMaxConcurrentConsumers", 23));

    }

    private String wrap(String str, int watermark) {
        return Strings.wrapCamelCaseWords(str, watermark, " ");
    }
}
