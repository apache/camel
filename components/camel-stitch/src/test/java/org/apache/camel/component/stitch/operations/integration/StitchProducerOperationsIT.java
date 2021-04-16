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
package org.apache.camel.component.stitch.operations.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.stitch.StitchConfiguration;
import org.apache.camel.component.stitch.StitchTestUtils;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.StitchClientBuilder;
import org.apache.camel.component.stitch.client.StitchRegion;
import org.apache.camel.component.stitch.client.models.StitchException;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.apache.camel.component.stitch.operations.StitchProducerOperations;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "token", matches = ".*", disabledReason = "Stitch token was not provided")
class StitchProducerOperationsIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(StitchProducerOperationsIT.class);

    private static StitchClient client;

    @BeforeAll
    static void prepare() throws Exception {
        client = StitchClientBuilder.builder()
                .withRegion(StitchRegion.EUROPE)
                .withToken(StitchTestUtils.loadStitchTokenFromJvmEnv().getProperty("token"))
                .build();
    }

    @Test
    void testIfSendIfStitchMessagesSet() {
        final StitchConfiguration configuration = new StitchConfiguration();
        configuration.setTableName("test_table");
        configuration.setStitchSchema(StitchSchema.builder().addKeyword("field_1", "string").build());
        configuration.setKeyNames("field_1");

        final StitchMessage message = StitchMessage.builder()
                .withData("field_1", "data")
                .build();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(message);

        sendEventAndAssert(exchange.getMessage(), configuration, response -> {
            assertNotNull(response);
            assertTrue(response.getHttpStatusCode() == 200 || response.getHttpStatusCode() == 201);
        });
    }

    @Test
    void testIfSendFromIterable() {
        final StitchConfiguration configuration = new StitchConfiguration();
        configuration.setTableName("table_1");
        configuration.setStitchSchema(StitchSchema.builder().addKeyword("field_1", "string").build());
        configuration.setKeyNames("field_1");

        final StitchMessage stitchMessage1 = StitchMessage.builder()
                .withData("field_1", "stitchMessage1")
                .build();

        final StitchMessage stitchMessage2 = StitchMessage.builder()
                .withData("field_1", "stitchMessage2-1")
                .build();

        final StitchRequestBody stitchMessage2RequestBody = StitchRequestBody.builder()
                .addMessage(stitchMessage2)
                .withSchema(StitchSchema.builder().addKeyword("field_1", "integer").build())
                .withTableName("table_1")
                .withKeyNames(Collections.singleton("field_1"))
                .build();

        final Map<String, Object> stitchMessage3 = new LinkedHashMap<>();
        stitchMessage3.put(StitchMessage.DATA, Collections.singletonMap("field_1", "stitchMessage3"));

        final StitchMessage stitchMessage4 = StitchMessage.builder()
                .withData("field_1", "stitchMessage4")
                .build();

        final Exchange stitchMessage4Exchange = new DefaultExchange(context);
        stitchMessage4Exchange.getMessage().setBody(stitchMessage4);

        final StitchMessage stitchMessage5 = StitchMessage.builder()
                .withData("field_1", "stitchMessage5")
                .build();

        final Message stitchMessage5Message = new DefaultExchange(context).getMessage();
        stitchMessage5Message.setBody(stitchMessage5);

        final List<Object> inputMessages = new LinkedList<>();
        inputMessages.add(stitchMessage1);
        inputMessages.add(stitchMessage2RequestBody);
        inputMessages.add(stitchMessage3);
        inputMessages.add(stitchMessage4Exchange);
        inputMessages.add(stitchMessage5Message);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(inputMessages);

        sendEventAndAssert(exchange.getMessage(), configuration, response -> {
            assertNotNull(response);
            assertTrue(response.getHttpStatusCode() == 200 || response.getHttpStatusCode() == 201);
        });

    }

    private void sendEventAndAssert(
            final Message message, final StitchConfiguration configuration, final Consumer<StitchResponse> fn) {
        try {
            final StitchResponse response = sendEventMono(message, configuration).block();
            fn.accept(response);
        } catch (StitchException exception) {
            LOG.error(exception.getMessage());
            throw exception;
        }
    }

    private Mono<StitchResponse> sendEventMono(final Message message, final StitchConfiguration configuration) {
        final StitchProducerOperations operations = new StitchProducerOperations(client, configuration);

        return Mono.create(sink -> operations.sendEvents(message, sink::success, doneSync -> {
            if (message.getExchange().getException() != null) {
                sink.error(message.getExchange().getException());
            }
        }));
    }
}
