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
package org.apache.camel.component.stitch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.models.StitchException;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StitchProducerTest extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    void testIfSendSingleMessage() throws InterruptedException {
        result.reset();

        result.expectedMessageCount(1);
        result.expectedHeaderReceived(StitchConstants.CODE, 200);
        result.expectedHeaderReceived(StitchConstants.STATUS, "OK");
        result.expectedBodiesReceived("All good!");
        result.setAssertPeriod(1000);

        template.send("direct:sendStitch", exchange -> {
            exchange.getMessage().setHeader(StitchConstants.SCHEMA,
                    StitchSchema.builder().addKeyword("field_1", "string").build());
            exchange.getMessage().setHeader(StitchConstants.KEY_NAMES, "field_1");

            exchange.getMessage().setBody(StitchMessage.builder()
                    .withData("field_1", "data")
                    .build());
        });

        result.assertIsSatisfied();

        final Message message = result.getExchanges().get(0).getMessage();

        assertEquals("test", message.getHeader(StitchConstants.HEADERS, Map.class).get("header-1"));
    }

    @Test
    void testIfSendMultipleMessages() throws InterruptedException {
        result.reset();

        result.expectedMessageCount(1);
        result.expectedHeaderReceived(StitchConstants.CODE, 200);
        result.expectedHeaderReceived(StitchConstants.STATUS, "OK");
        result.expectedBodiesReceived("All good!");
        result.setAssertPeriod(1000);

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

        template.send("direct:sendStitch", exchange -> {
            exchange.getMessage().setHeader(StitchConstants.SCHEMA,
                    StitchSchema.builder().addKeyword("field_1", "string").build());
            exchange.getMessage().setHeader(StitchConstants.KEY_NAMES, "field_1");

            exchange.getMessage().setBody(inputMessages);
        });

        result.assertIsSatisfied();

        final Message message = result.getExchanges().get(0).getMessage();

        assertEquals("test", message.getHeader(StitchConstants.HEADERS, Map.class).get("header-1"));
    }

    @Test
    void testIfSendSingleMessageWithError() throws InterruptedException {
        result.reset();
        result.setAssertPeriod(1000);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(StitchConstants.SCHEMA,
                StitchSchema.builder().addKeyword("field_1", "string").build());
        exchange.getMessage().setHeader(StitchConstants.KEY_NAMES, "field_1");

        exchange.getMessage().setBody(StitchMessage.builder()
                .withData("field_2", "data")
                .build());

        template.send("direct:sendErrorStitch", exchange);

        result.assertIsSatisfied();

        assertNotNull(exchange.getException());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("client", new TestClient());
        context.getRegistry().bind("errorClient", new TestErrorClient());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sendStitch")
                        .to("stitch:table_1?stitchClient=#client&token=dummy")
                        .to(result);

                from("direct:sendErrorStitch")
                        .to("stitch:table_1?stitchClient=#errorClient&token=dummy")
                        .to(result);
            }
        };
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
