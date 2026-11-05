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
package org.apache.camel.component.vertx;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A message without a body is sent to the event bus with a null body (the event bus has a codec for null), with send,
 * publish and request, and a consumer receives it.
 */
public class VertxNullBodyTest extends VertxBaseTestSupport {

    @Test
    public void testSendNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:send");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();

        template.sendBody("vertx:foo.send", null);

        mock.assertIsSatisfied();
    }

    @Test
    public void testPublishNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:publish");
        // both consumers of the address receive it
        mock.expectedMessageCount(2);
        mock.allMessages().body().isNull();

        template.sendBody("vertx:foo.publish?pubSub=true", null);

        mock.assertIsSatisfied();
    }

    @Test
    public void testRequestNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:request");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();

        Object reply = template.requestBody("vertx:foo.request", (Object) null);

        mock.assertIsSatisfied();
        assertEquals("Pong", reply);
    }

    @Test
    public void testRequestNullBodyWithNullReply() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:echo");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();

        Exchange out = template.request("vertx:foo.echo", e -> e.getIn().setBody(null));

        mock.assertIsSatisfied();
        assertNull(out.getException());
        assertNull(out.getMessage().getBody());
    }

    @Test
    public void testEventBusHandlerReceivesNullBody() throws Exception {
        EventBus eventBus = context.getComponent("vertx", VertxComponent.class).getVertx().eventBus();
        CompletableFuture<Message<Object>> received = new CompletableFuture<>();
        MessageConsumer<Object> consumer = eventBus.consumer("foo.handler", received::complete);
        consumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        try {
            template.sendBody("vertx:foo.handler", null);

            Message<Object> message = received.get(10, TimeUnit.SECONDS);
            assertNull(message.body());
            assertNull(message.replyAddress());
        } finally {
            consumer.unregister();
        }
    }

    @Test
    public void testEventBusHandlerRepliesToNullBodyRequest() throws Exception {
        EventBus eventBus = context.getComponent("vertx", VertxComponent.class).getVertx().eventBus();
        CompletableFuture<Object> received = new CompletableFuture<>();
        MessageConsumer<Object> consumer = eventBus.consumer("foo.handler.request", message -> {
            received.complete(message.body());
            message.reply(message.body() == null ? "Pong" : "Unexpected");
        });
        consumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        try {
            Object reply = template.sendBody("vertx:foo.handler.request", ExchangePattern.InOut, null);

            assertNull(received.get(10, TimeUnit.SECONDS));
            assertEquals("Pong", reply);
        } finally {
            consumer.unregister();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("vertx:foo.send").to("mock:send");

                from("vertx:foo.publish").to("mock:publish");
                from("vertx:foo.publish").to("mock:publish");

                from("vertx:foo.request").to("mock:request").setBody(constant("Pong"));

                // replies with the body it received, which is null
                from("vertx:foo.echo").to("mock:echo");
            }
        };
    }
}
