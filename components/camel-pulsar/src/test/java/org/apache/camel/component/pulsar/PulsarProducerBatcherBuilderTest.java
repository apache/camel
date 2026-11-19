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
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.apache.pulsar.client.impl.ProducerBuilderImpl;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * The batcherBuilder option is set from the names DEFAULT and KEY_BASED, as listed in the catalog.
 */
public class PulsarProducerBatcherBuilderTest extends CamelTestSupport {

    private static final String KEY_BASED_TOPIC = "persistent://public/default/camel-batcher-key-based";
    private static final String DEFAULT_TOPIC = "persistent://public/default/camel-batcher-default";

    private final List<ProducerBuilderImpl<byte[]>> builders = new ArrayList<>();

    private List<ProducerBuilderImpl<byte[]>> builders(String topic) {
        return builders.stream().filter(builder -> topic.equals(builder.getConf().getTopicName())).toList();
    }

    @Test
    public void testKeyBasedFromTheUri() {
        template.sendBody("direct:keyBased", "Hello");

        List<ProducerBuilderImpl<byte[]>> keyBased = builders(KEY_BASED_TOPIC);
        assertEquals(1, keyBased.size());
        assertSame(BatcherBuilder.KEY_BASED, keyBased.get(0).getConf().getBatcherBuilder());
    }

    @Test
    public void testDefaultFromTheUri() {
        template.sendBody("direct:default", "Hello");

        List<ProducerBuilderImpl<byte[]>> defaults = builders(DEFAULT_TOPIC);
        assertEquals(1, defaults.size());
        assertSame(BatcherBuilder.DEFAULT, defaults.get(0).getConf().getBatcherBuilder());
    }

    @Test
    public void testNameIsNotCaseSensitive() {
        PulsarEndpoint endpoint = context.getEndpoint("pulsar:" + KEY_BASED_TOPIC + "?batcherBuilder=key_based",
                PulsarEndpoint.class);
        assertSame(BatcherBuilder.KEY_BASED, endpoint.getPulsarConfiguration().getBatcherBuilder());
    }

    @Test
    public void testDefaultWhenNotSet() {
        PulsarEndpoint endpoint = context.getEndpoint("pulsar:" + DEFAULT_TOPIC + "?producerName=unset",
                PulsarEndpoint.class);
        assertSame(BatcherBuilder.DEFAULT, endpoint.getPulsarConfiguration().getBatcherBuilder());
    }

    @Test
    public void testUnknownNameFails() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("pulsar:" + DEFAULT_TOPIC + "?batcherBuilder=BY_KEY"));
        Throwable cause = e;
        while (cause.getCause() != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(iae.getMessage().contains("Unknown batcherBuilder: BY_KEY"), iae.getMessage());
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
                from("direct:keyBased")
                        .to("pulsar:" + KEY_BASED_TOPIC + "?producerName=camel-key-based&batcherBuilder=KEY_BASED");

                from("direct:default")
                        .to("pulsar:" + DEFAULT_TOPIC + "?producerName=camel-default&batcherBuilder=DEFAULT");
            }
        };
    }
}
