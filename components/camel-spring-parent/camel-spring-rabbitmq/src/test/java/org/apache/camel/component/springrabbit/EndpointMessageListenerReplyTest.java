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
package org.apache.camel.component.springrabbit;

import java.nio.charset.StandardCharsets;

import com.rabbitmq.client.Channel;
import org.apache.camel.Processor;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The reply a spring-rabbitmq consumer sends to the requester (no broker: the reply is captured from a mocked
 * template).
 */
public class EndpointMessageListenerReplyTest extends CamelTestSupport {

    private final RabbitTemplate template = mock(RabbitTemplate.class);

    @Test
    void byteArrayReplyToTextRequestIsOctetStream() throws Exception {
        Message reply = requestReply(textRequest("Question"),
                exchange -> exchange.getMessage().setBody("Answer".getBytes(StandardCharsets.UTF_8)));

        assertEquals(MessageProperties.CONTENT_TYPE_BYTES, reply.getMessageProperties().getContentType());
        assertEquals("cid-1", reply.getMessageProperties().getCorrelationId());
        assertArrayEquals("Answer".getBytes(StandardCharsets.UTF_8), reply.getBody());
    }

    @Test
    void textReplyToBinaryRequestIsTextPlain() throws Exception {
        Message request = MessageBuilder.withBody(new byte[] { 1, 2, 3 })
                .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                .setCorrelationId("cid-1")
                .setReplyTo("reply-queue")
                .build();
        Message reply = requestReply(request, exchange -> exchange.getMessage().setBody("Answer"));

        assertEquals(MessageProperties.CONTENT_TYPE_TEXT_PLAIN, reply.getMessageProperties().getContentType());
    }

    @Test
    void textReplyToTextRequestKeepsContentType() throws Exception {
        Message request = MessageBuilder.withBody("<question/>".getBytes(StandardCharsets.UTF_8))
                .setContentType("text/xml")
                .setContentEncoding("UTF-8")
                .setCorrelationId("cid-1")
                .setReplyTo("reply-queue")
                .build();
        Message reply = requestReply(request, exchange -> exchange.getMessage().setBody("<answer/>"));

        assertEquals("text/xml", reply.getMessageProperties().getContentType());
    }

    @Test
    void byteArrayJsonReplyToJsonRequestKeepsContentType() throws Exception {
        Message reply = requestReply(jsonRequest(),
                exchange -> exchange.getMessage().setBody("{\"answer\":42}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, reply.getMessageProperties().getContentType());
    }

    @Test
    void stringJsonReplyToJsonRequestKeepsContentType() throws Exception {
        Message reply = requestReply(jsonRequest(), exchange -> exchange.getMessage().setBody("{\"answer\":42}"));

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, reply.getMessageProperties().getContentType());
    }

    @Test
    void streamCacheJsonReplyToJsonRequestKeepsContentType() throws Exception {
        // marshal leaves a StreamCache as the body when stream caching is enabled
        Message reply = requestReply(jsonRequest(), exchange -> exchange.getMessage().setBody(
                new InputStreamCache("{\"answer\":42}".getBytes(StandardCharsets.UTF_8))));

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, reply.getMessageProperties().getContentType());
        assertArrayEquals("{\"answer\":42}".getBytes(StandardCharsets.UTF_8), reply.getBody());
    }

    @Test
    void streamCacheReplyToTextRequestIsOctetStream() throws Exception {
        Message reply = requestReply(textRequest("Question"), exchange -> exchange.getMessage().setBody(
                new InputStreamCache("Answer".getBytes(StandardCharsets.UTF_8))));

        assertEquals(MessageProperties.CONTENT_TYPE_BYTES, reply.getMessageProperties().getContentType());
    }

    @Test
    void contentTypeSetByRouteIsUsed() throws Exception {
        Message reply = requestReply(textRequest("Question"), exchange -> {
            exchange.getMessage().setBody("{\"answer\":42}");
            exchange.getMessage().setHeader(SpringRabbitMQConstants.CONTENT_TYPE, MessageProperties.CONTENT_TYPE_JSON);
        });

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, reply.getMessageProperties().getContentType());
    }

    @Test
    void messageReplyGetsCorrelationIdOfRequest() throws Exception {
        Message reply = requestReply(textRequest("Question"),
                exchange -> exchange.getMessage().setBody(
                        MessageBuilder.withBody("Hi from a Message".getBytes(StandardCharsets.UTF_8)).build()));

        assertEquals("cid-1", reply.getMessageProperties().getCorrelationId());
        assertArrayEquals("Hi from a Message".getBytes(StandardCharsets.UTF_8), reply.getBody());
    }

    @Test
    void messageReplyKeepsItsOwnCorrelationId() throws Exception {
        Message reply = requestReply(textRequest("Question"),
                exchange -> exchange.getMessage().setBody(
                        MessageBuilder.withBody("Hi".getBytes(StandardCharsets.UTF_8)).setCorrelationId("own").build()));

        assertEquals("own", reply.getMessageProperties().getCorrelationId());
    }

    private static Message jsonRequest() {
        return MessageBuilder.withBody("{\"question\":1}".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setCorrelationId("cid-1")
                .setReplyTo("reply-queue")
                .build();
    }

    private static Message textRequest(String text) {
        return MessageBuilder.withBody(text.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setContentEncoding("UTF-8")
                .setCorrelationId("cid-1")
                .setReplyTo("reply-queue")
                .build();
    }

    private Message requestReply(Message request, Processor route) throws Exception {
        SpringRabbitMQEndpoint endpoint = context.getEndpoint("spring-rabbitmq:default?queues=requests",
                SpringRabbitMQEndpoint.class);
        endpoint.setConnectionFactory(new CachingConnectionFactory("localhost"));
        SpringRabbitMQConsumer consumer = (SpringRabbitMQConsumer) endpoint.createConsumer(route);
        EndpointMessageListener listener = consumer.getEndpointMessageListener();
        listener.setTemplate(template);

        listener.onMessage(request, mock(Channel.class));

        ArgumentCaptor<Message> reply = ArgumentCaptor.forClass(Message.class);
        verify(template).send(anyString(), eq("reply-queue"), reply.capture());
        return reply.getValue();
    }
}
