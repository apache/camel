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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonPayloadHelperTest extends ContextTestSupport {

    private static final String JSON = "{\"sku\": \"CAMEL-MUG\"}";

    @Test
    public void jsonTextIsAnObjectOrAnArray() {
        assertTrue(JsonPayloadHelper.isJsonText(JSON));
        assertTrue(JsonPayloadHelper.isJsonText("  [1, 2]\n"));
        assertFalse(JsonPayloadHelper.isJsonText("hello"));
        assertFalse(JsonPayloadHelper.isJsonText("{not closed"));
        assertFalse(JsonPayloadHelper.isJsonText("\"a string\""));
        assertFalse(JsonPayloadHelper.isJsonText(null));
    }

    @Test
    public void aPayloadIsWrittenAsItIs() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        byte[] bytes = JSON.getBytes(StandardCharsets.UTF_8);
        for (Object body : new Object[] { JSON, bytes, new ByteArrayInputStream(bytes) }) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(bytes.length, JsonPayloadHelper.writeIfAlreadyJson(exchange, body, out), "for " + body);
            assertEquals(JSON, out.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void anythingElseIsLeftToTheDataFormat() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(-1, JsonPayloadHelper.writeIfAlreadyJson(exchange, "hello", out));
        assertEquals(-1, JsonPayloadHelper.writeIfAlreadyJson(exchange, Map.of("sku", "CAMEL-MUG"), out));
        assertEquals(0, out.size());
    }
}
