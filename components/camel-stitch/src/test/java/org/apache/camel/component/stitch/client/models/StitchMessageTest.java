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

import org.apache.camel.component.stitch.client.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}
