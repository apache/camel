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
package org.apache.camel.component.as2.api.util;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AS2UtilsTest {

    @Test
    void createMessageIdShouldBeUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String id = AS2Utils.createMessageId("example.com");
            assertTrue(ids.add(id), "Duplicate message ID: " + id);
        }
    }

    @Test
    void createMessageIdShouldContainFqdn() {
        String id = AS2Utils.createMessageId("test.example.org");
        assertTrue(id.contains("@test.example.org>"), "Message ID should contain FQDN");
        assertTrue(id.startsWith("<"), "Message ID should start with <");
        assertTrue(id.endsWith(">"), "Message ID should end with >");
    }

    @Test
    void createMessageIdShouldBeRfc2822Format() {
        String id = AS2Utils.createMessageId("server.example.com");
        // RFC 2822 Message-ID format: <unique-part@fqdn>
        assertTrue(id.matches("<[^@]+@server\\.example\\.com>"), "Message ID should match RFC 2822 format: " + id);
    }
}
