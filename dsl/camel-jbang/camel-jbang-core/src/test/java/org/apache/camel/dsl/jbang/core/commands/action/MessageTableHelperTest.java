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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTableHelperTest {

    @Test
    void testBodyAppearsInOutput() {
        MessageTableHelper helper = new MessageTableHelper();

        JsonObject root = new JsonObject();
        root.put("exchangeType", "DefaultExchange");
        root.put("messageType", "DefaultMessage");

        JsonObject body = new JsonObject();
        body.put("type", "String");
        body.put("value", "Hello World");
        root.put("body", body);

        String result = helper.getDataAsTable("exch-1", "InOnly", null, null, null, root, null);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("Hello World"), "Output should contain the body value");
    }

    @Test
    void testHeadersAppearsWhenEnabled() {
        MessageTableHelper helper = new MessageTableHelper();
        helper.setShowHeaders(true);

        JsonObject root = new JsonObject();
        root.put("exchangeType", "DefaultExchange");

        JsonArray headers = new JsonArray();
        JsonObject header = new JsonObject();
        header.put("key", "Content-Type");
        header.put("type", "String");
        header.put("value", "application/json");
        headers.add(header);
        root.put("headers", headers);

        String result = helper.getDataAsTable("exch-2", "InOnly", null, null, null, root, null);

        assertNotNull(result);
        assertTrue(result.contains("Content-Type"), "Output should contain header name when showHeaders=true");
        assertTrue(result.contains("application/json"), "Output should contain header value");
    }

    @Test
    void testHeadersHiddenWhenDisabled() {
        MessageTableHelper helper = new MessageTableHelper();
        helper.setShowHeaders(false);

        JsonObject root = new JsonObject();
        root.put("exchangeType", "DefaultExchange");

        JsonArray headers = new JsonArray();
        JsonObject header = new JsonObject();
        header.put("key", "X-Custom-Header");
        header.put("type", "String");
        header.put("value", "hidden-value");
        headers.add(header);
        root.put("headers", headers);

        String result = helper.getDataAsTable("exch-3", "InOnly", null, null, null, root, null);

        assertNotNull(result);
        assertFalse(result.contains("X-Custom-Header"), "Headers should not appear when showHeaders=false");

    }

    @Test
    void testNullRootReturnsEmptyTable() {
        MessageTableHelper helper = new MessageTableHelper();

        String result = helper.getDataAsTable("exch-4", "InOnly", null, null, null, null, null);

        assertNotNull(result, "Result should not be null even for null root");
        assertTrue(result.isBlank(), "Should return empty output when root is null");
    }

    @Test
    void testExchangeIdAppearsInOutput() {
        MessageTableHelper helper = new MessageTableHelper();

        JsonObject root = new JsonObject();
        root.put("exchangeType", "DefaultExchange");

        String result = helper.getDataAsTable("my-exchange-id-12345", "InOut", null, null, null, root, null);

        assertNotNull(result);
        assertTrue(result.contains("my-exchange-id-12345"), "Exchange ID should appear in output");
    }
}
