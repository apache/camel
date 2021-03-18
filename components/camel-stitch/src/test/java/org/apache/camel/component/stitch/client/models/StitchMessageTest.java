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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.component.stitch.client.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StitchMessageTest {

    @Test
    void testIfCreateNormalMessage() {
        final StitchMessage message = StitchMessage.builder()
                .withData("id", 2)
                .withData("name", "Jake")
                .withData("age", 6)
                .withData("has_magic", true)
                .withData("modified_at", "2020-01-13T21:25:03+0000")
                .withSequence(1565881320)
                .withAction(StitchMessage.Action.UPSERT)
                .build();

        final String messageAsJson = JsonUtils.convertMapToJson(message.toMap());

        assertEquals("{\"action\":\"upsert\",\"sequence\":1565881320,\"data\":"
                     + "{\"id\":2,\"name\":\"Jake\",\"age\":6,\"has_magic\":true,\"modified_at\":\"2020-01-13T21:25:03+0000\"}}",
                messageAsJson);
    }

    @Test
    void testIfNotCreateFromMapFromInvalidData() {
        final LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put(StitchMessage.ACTION, "upsert");
        data.put(StitchMessage.DATA, 1);
        data.put(StitchMessage.SEQUENCE, 1122544L);

        assertThrows(IllegalArgumentException.class, () -> StitchMessage
                .fromMap(data));
    }

    @Test
    void testIfCreateMap() {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", 2);
        data.put("name", "Jake");

        final Map<String, Object> message = new LinkedHashMap<>();
        message.put(StitchMessage.SEQUENCE, 123456L);
        message.put(StitchMessage.DATA, data);

        final StitchMessage stitchMessage = StitchMessage
                .fromMap(message)
                .build();

        assertEquals(StitchMessage.Action.UPSERT, stitchMessage.getAction());
        assertEquals(data, stitchMessage.getData());
        assertEquals(123456L, stitchMessage.getSequence());
    }
}
