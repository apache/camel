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
package org.apache.camel.component.stitch.operations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.stitch.StitchConfiguration;
import org.apache.camel.component.stitch.StitchConstants;
import org.apache.camel.component.stitch.client.JsonUtils;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.models.StitchException;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StitchProducerOperationsTest extends CamelTestSupport {

    @Test
    void testIfCreateIfStitchMessagesSet() {
        final StitchConfiguration configuration = new StitchConfiguration();
        configuration.setTableName("test_table");
        configuration.setStitchSchema(StitchSchema.builder().addKeyword("field_1", "integer").build());
        configuration.setKeyNames("field_1,field_2");

        final StitchMessage message = StitchMessage.builder()
                .withData("field_1", "data")
                .withSequence(0)
                .build();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(message);

        final StitchProducerOperations operations = new StitchProducerOperations(new TestClient(), configuration);

        assertEquals("{\"table_name\":\"test_table\",\"schema\":{\"field_1\":\"integer\"},\"messages\":[{\"action\":\"upsert\","
                     + "\"sequence\":0,\"data\":{\"field_1\":\"data\"}}],\"key_names\":[\"field_1\",\"field_2\"]}",
                JsonUtils.convertMapToJson(operations.createStitchRequestBody(exchange.getMessage()).toMap()));

        final StitchMessage message1 = StitchMessage.builder()
                .withData("field_1", "test_2")
                .withSequence(0)
                .build();

        exchange.getMessage().setHeader(StitchConstants.SCHEMA,
                StitchSchema.builder().addKeyword("field_1", "integer").addKeyword("field_2", "string").build());
        exchange.getMessage().setHeader(StitchConstants.TABLE_NAME, "test_table_2");
        exchange.getMessage().setHeader(StitchConstants.KEY_NAMES, "field_1,field_2");

        exchange.getMessage().setBody(message1);

        assertEquals("{\"table_name\":\"test_table_2\",\"schema\":{\"field_1\":\"integer\",\"field_2\":\"string\"},"
                     + "\"messages\":[{\"action\":\"upsert\",\"sequence\":0,\"data\":{\"field_1\":\"test_2\"}}],\"key_names\":[\"field_1\",\"field_2\"]}",
                JsonUtils.convertMapToJson(operations.createStitchRequestBody(exchange.getMessage()).toMap()));
    }

    @Test
    void testIfCreateIfStitchRequestBodySet() {
        final StitchConfiguration configuration = new StitchConfiguration();
        configuration.setTableName("table_2");

        final StitchMessage message1 = StitchMessage.builder()
                .withData("field_1", "test_2")
                .withSequence(0)
                .build();

        final StitchRequestBody requestBody = StitchRequestBody.builder()
                .addMessage(message1)
                .withSchema(StitchSchema.builder().addKeyword("field_1", "integer").build())
                .withTableName("table_1")
                .withKeyNames(Collections.singleton("field_1"))
                .build();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(requestBody);

        final StitchProducerOperations operations = new StitchProducerOperations(new TestClient(), configuration);

        assertEquals("{\"table_name\":\"table_2\",\"schema\":{\"field_1\":\"integer\"},\"messages\":"
                     + "[{\"action\":\"upsert\",\"sequence\":0,\"data\":{\"field_1\":\"test_2\"}}],\"key_names\":[\"field_1\"]}",
                JsonUtils.convertMapToJson(operations.createStitchRequestBody(exchange.getMessage()).toMap()));
    }

    @Test
    void testIfCreateIfMapSet() {
        final StitchConfiguration configuration = new StitchConfiguration();

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Collections.singletonMap("type", "integer"));
        properties.put("name", Collections.singletonMap("type", "string"));
        properties.put("age", Collections.singletonMap("type", "integer"));
        properties.put("has_magic", Collections.singletonMap("type", "boolean"));

        final Map<String, Object> message = new LinkedHashMap<>();
        message.put(StitchMessage.DATA, Collections.singletonMap("id", 2));
        message.put(StitchMessage.SEQUENCE, 1L);

        final Map<String, Object> data = new LinkedHashMap<>();
        data.put(StitchRequestBody.TABLE_NAME, "my_table");
        data.put(StitchRequestBody.SCHEMA, Collections.singletonMap("properties", properties));
        data.put(StitchRequestBody.MESSAGES,
                Collections.singletonList(message));
        data.put(StitchRequestBody.KEY_NAMES, Collections.singletonList("test_key"));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(data);

        final StitchProducerOperations operations = new StitchProducerOperations(new TestClient(), configuration);

        final String createdJson
                = JsonUtils.convertMapToJson(operations.createStitchRequestBody(exchange.getMessage()).toMap());

        assertEquals("{\"table_name\":\"my_table\",\"schema\":{\"properties\":{\"id\":{\"type\":\"integer\"},"
                     + "\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"},\"has_magic\""
                     + ":{\"type\":\"boolean\"}}},\"messages\":[{\"action\":\"upsert\",\"sequence\":1,"
                     + "\"data\":{\"id\":2}}],\"key_names\":[\"test_key\"]}",
                createdJson);
    }

    @Test
    void testIfCreateFromIterable() {
        final StitchConfiguration configuration = new StitchConfiguration();
        configuration.setTableName("table_1");
        configuration.setStitchSchema(StitchSchema.builder().addKeyword("field_1", "string").build());
        configuration.setKeyNames("field_1");

        final StitchMessage stitchMessage1 = StitchMessage.builder()
                .withData("field_1", "stitchMessage1")
                .withSequence(1)
                .build();

        final StitchMessage stitchMessage2 = StitchMessage.builder()
                .withData("field_1", "stitchMessage2-1")
                .withData("field_2", "stitchMessage2-2")
                .withSequence(2)
                .build();

        final StitchRequestBody stitchMessage2RequestBody = StitchRequestBody.builder()
                .addMessage(stitchMessage2)
                .withSchema(StitchSchema.builder().addKeyword("field_1", "integer").build())
                .withTableName("table_1")
                .withKeyNames(Collections.singleton("field_1"))
                .build();

        final Map<String, Object> stitchMessage3 = new LinkedHashMap<>();
        stitchMessage3.put(StitchMessage.DATA, Collections.singletonMap("field_1", "stitchMessage3"));
        stitchMessage3.put(StitchMessage.SEQUENCE, 3L);

        final StitchMessage stitchMessage4 = StitchMessage.builder()
                .withData("field_1", "stitchMessage4")
                .withSequence(4)
                .build();

        final Exchange stitchMessage4Exchange = new DefaultExchange(context);
        stitchMessage4Exchange.getMessage().setBody(stitchMessage4);

        final StitchMessage stitchMessage5 = StitchMessage.builder()
                .withData("field_1", "stitchMessage5")
                .withSequence(5)
                .build();

        final Message stitchMessage5Message = new DefaultExchange(context).getMessage();
        stitchMessage5Message.setBody(stitchMessage5);

        final List<Object> inputMessages = new LinkedList<>();
        inputMessages.add(stitchMessage1);
        inputMessages.add(stitchMessage2RequestBody);
        inputMessages.add(stitchMessage3);
        inputMessages.add(stitchMessage4Exchange);
        inputMessages.add(stitchMessage5Message);

        final StitchProducerOperations operations = new StitchProducerOperations(new TestClient(), configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(inputMessages);

        final String createdJson
                = JsonUtils.convertMapToJson(operations.createStitchRequestBody(exchange.getMessage()).toMap());

        assertEquals(
                "{\"table_name\":\"table_1\",\"schema\":{\"field_1\":\"string\"},\"messages\":[{\"action\":\"upsert\",\"sequence\":1,\"data\":{\"field_1\":\"stitchMessage1\"}},"
                     + "{\"action\":\"upsert\",\"sequence\":2,\"data\":{\"field_1\":\"stitchMessage2-1\",\"field_2\":\"stitchMessage2-2\"}},{\"action\":\"upsert\",\"sequence\":3,\"data\":{\"field_1\":"
                     + "\"stitchMessage3\"}},{\"action\":\"upsert\",\"sequence\":4,\"data\":{\"field_1\":\"stitchMessage4\"}},{\"action\":\"upsert\",\"sequence\":5,\"data\":{\"field_1\":\"stitchMessage5\"}}],"
                     + "\"key_names\":[\"field_1\"]}",
                createdJson);
    }

    @Test
    void testNormalSend() {
        final StitchConfiguration configuration = new StitchConfiguration();
        configuration.setTableName("table_1");
        configuration.setStitchSchema(StitchSchema.builder().addKeyword("field_1", "string").build());
        configuration.setKeyNames("field_1");

        final StitchMessage message = StitchMessage.builder()
                .withData("field_1", "data")
                .withSequence(0)
                .build();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(message);

        final StitchProducerOperations operations = new StitchProducerOperations(new TestClient(), configuration);
        final AtomicBoolean done = new AtomicBoolean(false);

        operations.sendEvents(exchange.getMessage(), response -> {
            assertEquals(200, response.getHttpStatusCode());
            assertEquals("OK", response.getStatus());
            assertEquals("All good!", response.getMessage());
            assertEquals(Collections.singletonMap("header-1", "test"), response.getHeaders());
            done.set(true);
        }, doneSync -> {
        });

        Awaitility
                .await()
                .atMost(1, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .untilTrue(done);
    }

    @Test
    void testErrorHandle() {
        final StitchConfiguration configuration = new StitchConfiguration();
        configuration.setTableName("table_1");
        configuration.setStitchSchema(StitchSchema.builder().addKeyword("field_1", "string").build());
        configuration.setKeyNames("field_1");

        final StitchMessage message = StitchMessage.builder()
                .withData("field_1", "data")
                .withSequence(0)
                .build();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(message);

        final StitchProducerOperations operations = new StitchProducerOperations(new TestErrorClient(), configuration);

        operations.sendEvents(exchange.getMessage(), response -> {
        }, doneSync -> {
        });

        assertNotNull(exchange.getException());
        assertTrue(exchange.getException() instanceof StitchException);
        assertNotNull(((StitchException) exchange.getException()).getResponse());
        assertEquals(400, ((StitchException) exchange.getException()).getResponse().getHttpStatusCode());
        assertEquals("Error", ((StitchException) exchange.getException()).getResponse().getStatus());
        assertEquals("Not good!", ((StitchException) exchange.getException()).getResponse().getMessage());
    }

    static class TestClient implements StitchClient {

        @Override
        public Mono<StitchResponse> batch(StitchRequestBody requestBody) {
            final StitchResponse response = new StitchResponse(
                    200,
                    Collections.singletonMap("header-1", "test"),
                    "OK",
                    "All good!");

            return Mono.just(response);
        }

        @Override
        public void close() {
            // noop
        }
    }

    static class TestErrorClient implements StitchClient {

        @Override
        public Mono<StitchResponse> batch(StitchRequestBody requestBody) {
            final StitchResponse response = new StitchResponse(
                    400,
                    Collections.singletonMap("header-1", "test"),
                    "Error",
                    "Not good!");

            final StitchException exception = new StitchException(response);

            return Mono.error(exception);
        }

        @Override
        public void close() {
            // noop
        }
    }
}
