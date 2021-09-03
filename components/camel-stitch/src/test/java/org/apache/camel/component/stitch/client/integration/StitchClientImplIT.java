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
package org.apache.camel.component.stitch.client.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.component.stitch.StitchTestUtils;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.StitchClientBuilder;
import org.apache.camel.component.stitch.client.StitchRegion;
import org.apache.camel.component.stitch.client.models.StitchException;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "token", matches = ".*", disabledReason = "Stitch token was not provided")
class StitchClientImplIT {

    @Test
    void testIfCreateBatch() throws Exception {
        final StitchClient client = StitchClientBuilder.builder()
                .withRegion(StitchRegion.EUROPE)
                .withToken(StitchTestUtils.loadStitchTokenFromJvmEnv().getProperty("token"))
                .build();

        final StitchMessage message1 = StitchMessage.builder()
                .withData("id", 2)
                .withData("name", "Jake")
                .withData("age", 6)
                .withData("has_magic", true)
                .withSequence(1565881320)
                .build();

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Collections.singletonMap("type", "integer"));
        properties.put("name", Collections.singletonMap("type", "string"));
        properties.put("age", Collections.singletonMap("type", "integer"));
        properties.put("has_magic", Collections.singletonMap("type", "boolean"));

        final StitchSchema schema = StitchSchema.builder()
                .addKeyword("properties", properties)
                .build();

        final StitchRequestBody body = StitchRequestBody.builder()
                .withTableName("test")
                .addMessage(message1)
                .withSchema(schema)
                .withKeyNames("id")
                .build();

        StitchResponse response = client.batch(body).block();

        assertNotNull(response);
        assertEquals(201, response.getHttpStatusCode());
    }

    @Test
    void testIfThrowError() throws Exception {
        final StitchClient client = StitchClientBuilder.builder()
                .withRegion(StitchRegion.EUROPE)
                .withToken(StitchTestUtils.loadStitchTokenFromJvmEnv().getProperty("token"))
                .build();

        final StitchMessage message1 = StitchMessage.builder()
                .withData("id", 2)
                .withData("name", "Jake")
                .withData("age", 6)
                .withData("has_magic", true)
                .withSequence(1565881320)
                .build();

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Collections.singletonMap("type", "integer"));
        properties.put("name", Collections.singletonMap("type", "string"));
        properties.put("age", Collections.singletonMap("type", "integer"));
        properties.put("has_magic", Collections.singletonMap("type", "integerr"));

        final StitchSchema schema = StitchSchema.builder()
                .addKeyword("properties", properties)
                .build();

        final StitchRequestBody body = StitchRequestBody.builder()
                .withTableName("test")
                .addMessage(message1)
                .withSchema(schema)
                .withKeyNames("id")
                .build();

        final Mono<StitchResponse> batch = client.batch(body);
        assertThrows(StitchException.class, () -> batch.block());
    }
}
