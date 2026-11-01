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
package org.apache.camel.component.kafka.consumer.support.streaming;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaRecordStreamingProcessorTest {

    private final KafkaConfiguration configuration = mock(KafkaConfiguration.class);
    private final Processor processor = mock(Processor.class);
    private final CommitManager commitManager = mock(CommitManager.class);
    private final KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);

    private final TopicPartition topicPartition = new TopicPartition("test-topic", 0);
    private final ConsumerRecord<Object, Object> record = new ConsumerRecord<>("test-topic", 0, 42L, "key", "value");

    @BeforeEach
    void setup() {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(kafkaConsumer.createExchange(false)).thenReturn(exchange);
        when(exchange.getMessage()).thenReturn(message);
        when(exchange.getIn()).thenReturn(message);

        when(configuration.getHeaderFilterStrategy()).thenReturn(mock(HeaderFilterStrategy.class));
        when(configuration.getHeaderDeserializer()).thenReturn(mock(KafkaHeaderDeserializer.class));
    }

    @Test
    void manualCommitStillRecordsOffset() {
        when(configuration.isAllowManualCommit()).thenReturn(true);
        when(commitManager.getManualCommit(any(), any(), any())).thenReturn(mock(KafkaManualCommit.class));

        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        proc.processExchange(kafkaConsumer, topicPartition, false, false, record);

        verify(commitManager).recordOffset(topicPartition, 42L);
    }

    @Test
    void autoCommitRecordsOffset() {
        when(configuration.isAllowManualCommit()).thenReturn(false);

        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        proc.processExchange(kafkaConsumer, topicPartition, false, false, record);

        verify(commitManager).recordOffset(topicPartition, 42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "seda:async-handoff-test",
            "disruptor:async-handoff-test",
            "disruptor-vm:async-handoff-test",
            "reactive-streams:async-handoff-test"
    })
    void detectsAsynchronousHandoffUsingARealCamelRoute(String asyncEndpoint) throws Exception {
        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        CamelContext context = new DefaultCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:async-handoff-source").to(asyncEndpoint);
                    from(asyncEndpoint).process(e -> {
                    });
                }
            });
            context.start();

            Exchange exchange = context.createProducerTemplate().send("direct:async-handoff-source",
                    e -> e.getMessage().setBody("test"));

            assertTrue(proc.hasAsynchronousHandoff(exchange), asyncEndpoint);
        } finally {
            context.stop();
        }
    }

    @Test
    void doesNotTreatDirectEndpointAsAnAsynchronousHandoff() throws Exception {
        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        CamelContext context = new DefaultCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:synchronous-handoff-source").to("direct:synchronous-handoff-target");
                    from("direct:synchronous-handoff-target").process(e -> {
                    });
                }
            });
            context.start();

            Exchange exchange = context.createProducerTemplate().send("direct:synchronous-handoff-source",
                    e -> e.getMessage().setBody("test"));

            assertFalse(proc.hasAsynchronousHandoff(exchange),
                    "A synchronous direct route must not be classified as an asynchronous hand-off");
        } finally {
            context.stop();
        }
    }

    @Test
    void doesNotDetectAnAsynchronousEndpointThatThisExchangeDidNotReach() {
        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        Exchange exchange = mock(Exchange.class);
        when(exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT, String.class)).thenReturn("direct:audit");

        assertFalse(proc.hasAsynchronousHandoff(exchange),
                "An asynchronous endpoint on another route branch must not affect this exchange");
    }

    @Test
    void detectsVmSchemeFromExchangeEndpointProperty() {
        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        Exchange exchange = mock(Exchange.class);
        when(exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT, String.class))
                .thenReturn("vm:async-handoff-test");

        assertTrue(proc.hasAsynchronousHandoff(exchange),
                "A VM endpoint URI should be classified as an asynchronous hand-off");
    }

    @Test
    void detectsAsynchronousHandoffWhenAnotherEndpointIsSentLater() throws Exception {
        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        CamelContext context = new DefaultCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:multiple-send-source")
                            .to("seda:multiple-send-async")
                            .to("direct:multiple-send-sync");
                    from("seda:multiple-send-async").process(e -> {
                    });
                    from("direct:multiple-send-sync").process(e -> {
                    });
                }
            });
            context.start();

            // The Kafka consumer installs this while the CamelContext is already running.
            KafkaRecordStreamingProcessor.ensureAsynchronousHandoffTracking(context);

            Exchange exchange = context.createProducerTemplate().send("direct:multiple-send-source",
                    e -> e.getMessage().setBody("test"));

            String lastEndpoint = exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT, String.class);
            assertTrue(lastEndpoint != null && lastEndpoint.startsWith("direct:"),
                    "The final TO_ENDPOINT should be the later direct send, but was " + lastEndpoint);
            assertTrue(proc.hasAsynchronousHandoff(exchange),
                    "The earlier SEDA send must remain recorded after the later direct send");
        } finally {
            context.stop();
        }
    }

    @Test
    void usesAProportionalWireTapTimeoutMargin() {
        assertEquals(240_000L, KafkaRecordStreamingProcessor.wireTapWaitTimeoutMillis(null));
        assertEquals(240_000L, KafkaRecordStreamingProcessor.wireTapWaitTimeoutMillis(300_000));
        assertEquals(8_000L, KafkaRecordStreamingProcessor.wireTapWaitTimeoutMillis(10_000));
    }

}
