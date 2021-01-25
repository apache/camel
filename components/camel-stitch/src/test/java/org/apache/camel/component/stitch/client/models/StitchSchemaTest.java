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

import org.apache.camel.component.stitch.client.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StitchSchemaTest {

    @Test
    void testIfCreateSchema() {
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
                .addKeyword("has_map", true)
                .build();

        final String schemaAsJson = JsonUtils.convertMapToJson(schema.toMap());

        assertEquals("{\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},"
                     + "\"age\":{\"type\":\"integer\"},\"has_magic\":"
                     + "{\"type\":\"boolean\"},\"modified_at\":{\"type\":\"string\",\"format\":\"date-time\"}},\"has_map\":true}",
                schemaAsJson);
    }
}
