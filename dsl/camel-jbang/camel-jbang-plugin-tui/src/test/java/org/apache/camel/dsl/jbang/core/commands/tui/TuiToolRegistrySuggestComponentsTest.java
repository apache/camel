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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiToolRegistrySuggestComponentsTest {

    private static final List<String> NAMES = List.of("activemq", "aws2-s3", "aws2-sqs", "http", "jms", "kafka",
            "paho", "paho-mqtt5", "platform-http", "rest", "spring-rabbitmq", "timer");

    @Test
    void knownAliasesComeFirst() {
        assertEquals(List.of("paho-mqtt5", "paho"), TuiToolRegistry.suggestComponents("mqtt", NAMES));
        assertEquals(List.of("spring-rabbitmq"), TuiToolRegistry.suggestComponents("rabbitmq", NAMES));
        assertEquals(List.of("aws2-s3"), TuiToolRegistry.suggestComponents("s3", NAMES));
    }

    @Test
    void substringMatchesFillInWhenThereIsNoAlias() {
        assertEquals(List.of("http"), TuiToolRegistry.suggestComponents("https", NAMES));
        assertEquals(List.of("paho", "paho-mqtt5"), TuiToolRegistry.suggestComponents("paho-mqtt", NAMES));
        assertTrue(TuiToolRegistry.suggestComponents("xyz", NAMES).isEmpty());
        // very short schemes would match too much, so they only get alias hits
        assertTrue(TuiToolRegistry.suggestComponents("mq", NAMES).isEmpty());
    }
}
