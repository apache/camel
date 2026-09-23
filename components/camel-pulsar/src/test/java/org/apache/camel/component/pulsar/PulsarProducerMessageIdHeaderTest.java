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
package org.apache.camel.component.pulsar;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The send result used to replace the message body, which left the rest of the route without the payload it had sent.
 */
public class PulsarProducerMessageIdHeaderTest extends CamelTestSupport {

    private final MessageId messageId = mock(MessageId.class);

    @Test
    public void testTheBodySurvivesAndTheMessageIdIsAHeader() {
        final Exchange out = template.request("direct:start", exchange -> exchange.getIn().setBody("Hello World!"));

        assertEquals("Hello World!", out.getMessage().getBody(String.class),
                "the producer should leave the body alone");
        assertSame(messageId, out.getMessage().getHeader(PulsarMessageHeaders.MESSAGE_ID_OUT),
                "the send result should be reported as a header");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        final TypedMessageBuilder<byte[]> messageBuilder = mock(TypedMessageBuilder.class, RETURNS_SELF);
        when(messageBuilder.sendAsync()).thenReturn(CompletableFuture.completedFuture(messageId));

        final Producer<byte[]> pulsarProducer = mock(Producer.class);
        when(pulsarProducer.newMessage()).thenReturn(messageBuilder);

        final ProducerBuilder<byte[]> producerBuilder = mock(ProducerBuilder.class, RETURNS_SELF);
        when(producerBuilder.create()).thenReturn(pulsarProducer);

        final PulsarClient pulsarClient = mock(PulsarClient.class);
        when(pulsarClient.newProducer()).thenReturn(producerBuilder);

        final PulsarComponent component = new PulsarComponent(context);
        component.setPulsarClient(pulsarClient);
        context.addComponent("pulsar", component);

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("pulsar:persistent://public/default/camel-producer-test");
            }
        };
    }
}
