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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.apache.pulsar.client.impl.ProducerBuilderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * The Pulsar client continues the sequence ids of a named producer after the last sequence id the broker stored for it
 * only when no initial sequence id is configured. A producer that always passed one (-1 by default) started again at
 * sequence id 0 after every start, and with deduplication enabled the broker dropped the messages as duplicates until
 * the sequence ids passed the ones of the previous run.
 */
public class PulsarProducerInitialSequenceIdTest extends CamelTestSupport {

    private static final String NAMED_TOPIC = "persistent://public/default/camel-sequence-test";
    private static final String CONFIGURED_TOPIC = "persistent://public/default/camel-sequence-test-2";

    private final List<ProducerBuilderImpl<byte[]>> builders = new ArrayList<>();

    private List<ProducerBuilderImpl<byte[]>> builders(String topic) {
        return builders.stream().filter(builder -> topic.equals(builder.getConf().getTopicName())).toList();
    }

    @Test
    public void testTheBrokerSequenceIdIsUsedByDefault() {
        template.sendBody("direct:named", "Hello");

        List<ProducerBuilderImpl<byte[]>> named = builders(NAMED_TOPIC);
        assertEquals(1, named.size());
        assertNull(named.get(0).getConf().getInitialSequenceId(),
                "no initial sequence id must be set, so that the client continues after the last sequence id of the broker");
    }

    @Test
    public void testTheBrokerSequenceIdIsUsedAfterARestart() throws Exception {
        template.sendBody("direct:named", "Hello");
        context.getRouteController().stopRoute("named");
        context.getRouteController().startRoute("named");
        template.sendBody("direct:named", "Hello again");

        List<ProducerBuilderImpl<byte[]>> named = builders(NAMED_TOPIC);
        assertEquals(2, named.size(), "the producer is created again after the restart");
        assertNull(named.get(1).getConf().getInitialSequenceId(),
                "no initial sequence id must be set, so that the client continues after the last sequence id of the broker");
    }

    @Test
    public void testAConfiguredInitialSequenceIdIsUsed() {
        template.sendBody("direct:configured", "Hello");

        List<ProducerBuilderImpl<byte[]>> configured = builders(CONFIGURED_TOPIC);
        assertEquals(1, configured.size());
        assertEquals(41L, configured.get(0).getConf().getInitialSequenceId());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        final TypedMessageBuilder<byte[]> messageBuilder = mock(TypedMessageBuilder.class, RETURNS_SELF);
        when(messageBuilder.sendAsync()).thenReturn(CompletableFuture.completedFuture(mock(MessageId.class)));

        final Producer<byte[]> pulsarProducer = mock(Producer.class);
        when(pulsarProducer.newMessage()).thenReturn(messageBuilder);

        final PulsarClient pulsarClient = mock(PulsarClient.class);
        when(pulsarClient.newProducer()).thenAnswer(invocation -> {
            // the real builder of the Pulsar client, which keeps the configuration the producer is created with
            final ProducerBuilderImpl<byte[]> builder = spy(new ProducerBuilderImpl<>(null, Schema.BYTES));
            doReturn(pulsarProducer).when(builder).create();
            builders.add(builder);
            return builder;
        });

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
                from("direct:named").routeId("named")
                        .to("pulsar:" + NAMED_TOPIC + "?producerName=camel-producer");

                from("direct:configured").routeId("configured")
                        .to("pulsar:" + CONFIGURED_TOPIC + "?producerName=camel-producer-2"
                            + "&initialSequenceId=41");
            }
        };
    }
}
