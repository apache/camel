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
package org.apache.camel.component.stitch.client.models;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StitchRequestBodyTest {

    @Test
    public void testNormalRequestBodyToJson() throws JsonProcessingException {
        final StitchMessage message1 = StitchMessage.builder()
                .withData("id", 2)
                .withData("name", "Jake")
                .withData("age", 6)
                .withData("has_magic", true)
                .withData("modified_at", "2020-01-13T21:25:03+0000")
                .withSequence(1565881320)
                .build();

        final StitchMessage message2 = StitchMessage.builder()
                .withData("id", 3)
                .withData("name", "Bubblegum")
                .withData("age", 17)
                .withData("has_magic", true)
                .withData("modified_at", "2020-01-14T13:34:25+0000")
                .withSequence(1565838645)
                .build();

        final Map<String, String> modifiedAtSchema = new LinkedHashMap<>();
        modifiedAtSchema.put("type", "string");
        modifiedAtSchema.put("format", "date-time");

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Collections.singletonMap("type", "integer"));
        properties.put("name", Collections.singletonMap("type", "string"));
        properties.put("age", Collections.singletonMap("type", "integer"));
        properties.put("has_magic", Collections.singletonMap("type", "boolean"));
        properties.put("modified_at", modifiedAtSchema);

        final StitchSchema schema = StitchSchema.builder()
                .addKeyword("properties", properties)
                .build();

        final StitchRequestBody body = StitchRequestBody.builder()
                .withTableName("customers")
                .addMessage(message1)
                .addMessage(message2)
                .withSchema(schema)
                .withKeyNames("id")
                .build();

        final String expectedJson
                = "{\"table_name\":\"customers\",\"schema\":{\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"},\""
                  + "has_magic\":{\"type\":\"boolean\"},\"modified_at\":{\"type\":\"string\",\"format\":\"date-time\"}}},\"messages\":[{\"action\":\"upsert\",\"sequence\":1565881320,\"data\":"
                  + "{\"id\":2,\"name\":\"Jake\",\"age\":6,\"has_magic\":true,\"modified_at\":\"2020-01-13T21:25:03+0000\"}},{\"action\":\"upsert\",\"sequence\":1565838645,\"data\":{\"id\":3,"
                  + "\"name\":\"Bubblegum\",\"age\":17,\"has_magic\":true,\"modified_at\":\"2020-01-14T13:34:25+0000\"}}],\"key_names\":[\"id\"]}";

        assertEquals(expectedJson, new ObjectMapper().writeValueAsString(body.toMap()));
    }

    @Test
    void testIfNotCreateRequestBodyFromInvalidMap() {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put(StitchRequestBody.TABLE_NAME, "table");
        data.put(StitchRequestBody.SCHEMA, 1);
        data.put(StitchRequestBody.MESSAGES, Collections.emptyList());
        data.put(StitchRequestBody.KEY_NAMES, Collections.emptySet());

        assertThrows(IllegalArgumentException.class, () -> StitchRequestBody.fromMap(data));

        final Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put(StitchRequestBody.TABLE_NAME, "table");
        data2.put(StitchRequestBody.SCHEMA, Collections.emptyMap());
        data2.put(StitchRequestBody.MESSAGES, 12);
        data2.put(StitchRequestBody.KEY_NAMES, Collections.emptySet());

        assertThrows(IllegalArgumentException.class, () -> StitchRequestBody.fromMap(data2));
    }

    @Test
    void testIfCreateRequestBodyFromMap() {
        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Collections.singletonMap("type", "integer"));
        properties.put("name", Collections.singletonMap("type", "string"));
        properties.put("age", Collections.singletonMap("type", "integer"));
        properties.put("has_magic", Collections.singletonMap("type", "boolean"));

        final Map<String, Object> data = new LinkedHashMap<>();
        data.put(StitchRequestBody.TABLE_NAME, "my_table");
        data.put(StitchRequestBody.SCHEMA, Collections.singletonMap("properties", properties));
        data.put(StitchRequestBody.MESSAGES,
                Collections.singletonList(Collections.singletonMap("data", Collections.singletonMap("id", 2))));
        data.put(StitchRequestBody.KEY_NAMES, Collections.singletonList("test_key"));

        final StitchRequestBody requestBody = StitchRequestBody
                .fromMap(data)
                .build();

        assertEquals("my_table", requestBody.getTableName());
        assertEquals(Collections.singletonMap("properties", properties), requestBody.getSchema().getKeywords());
        assertEquals(Collections.singletonMap("id", 2), requestBody.getMessages().stream().findFirst().get().getData());
        assertEquals("test_key", requestBody.getKeyNames().stream().findFirst().get());
    }

}
