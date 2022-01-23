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
package org.apache.camel.component.dynamicrouter;

import java.util.stream.Stream;

import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.SubscribeMessageBuilder;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.UnsubscribeMessageBuilder;
import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.apache.camel.support.builder.PredicateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicRouterControlMessageTest extends DynamicRouterTestSupport {

    private static Stream<Arguments> provideSubscribeMessageArguments() {
        return Stream.of(
                Arguments.of(null, DYNAMIC_ROUTER_CHANNEL, TEST_PRIORITY, "mock:test", PredicateBuilder.constant(true),
                        "Missing subscription ID"),
                Arguments.of(TEST_ID, null, TEST_PRIORITY, "mock:test", PredicateBuilder.constant(true),
                        "Missing subscription channel"),
                Arguments.of(TEST_ID, DYNAMIC_ROUTER_CHANNEL, TEST_PRIORITY, null, PredicateBuilder.constant(true),
                        "Missing URI"),
                Arguments.of(TEST_ID, DYNAMIC_ROUTER_CHANNEL, TEST_PRIORITY, "mock:test", null, "Missing predicate"));
    }

    private static Stream<Arguments> provideUnsubscribeMessageArguments() {
        return Stream.of(
                Arguments.of(null, DYNAMIC_ROUTER_CHANNEL, "Missing subscription ID"),
                Arguments.of(TEST_ID, null, "Missing subscription channel"));
    }

    @Test
    void testBuildSubscribeMessage() {
        assertDoesNotThrow(() -> new SubscribeMessageBuilder()
                .id(TEST_ID)
                .channel(DYNAMIC_ROUTER_CHANNEL)
                .priority(TEST_PRIORITY)
                .endpointUri("mock:test")
                .predicate(PredicateBuilder.constant(true))
                .build());
    }

    @ParameterizedTest
    @MethodSource("provideSubscribeMessageArguments")
    void testBuildSubscribeMessageWithError(
            String id, String channel, int priority, String uri, Predicate predicate, String message) {
        assertThrows(IllegalArgumentException.class, () -> new SubscribeMessageBuilder()
                .id(id)
                .channel(channel)
                .priority(priority)
                .endpointUri(uri)
                .predicate(predicate)
                .build(), message);
    }

    @Test
    void testBuildUnsubscribeMessage() {
        assertDoesNotThrow(() -> new UnsubscribeMessageBuilder()
                .id(TEST_ID)
                .channel(DYNAMIC_ROUTER_CHANNEL)
                .build());
    }

    @ParameterizedTest
    @MethodSource("provideUnsubscribeMessageArguments")
    void testBuildUnsubscribeMessageWithError(String id, String channel, String message) {
        assertThrows(IllegalArgumentException.class, () -> new UnsubscribeMessageBuilder()
                .id(id)
                .channel(channel)
                .build(), message);
    }
}
