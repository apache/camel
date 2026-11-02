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

import java.util.List;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommit;
import org.apache.camel.component.kafka.consumer.KafkaExactlyOnceMetadata;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        KafkaRecordStreamingProcessor.registerAsynchronousHandoffTracking(context);
        KafkaExactlyOnceMetadata metadata = newMetadata();
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
                    e -> {
                        e.getMessage().setBody("test");
                        e.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, metadata);
                    });

            assertTrue(metadata.hasAsynchronousKafkaHandoff(), asyncEndpoint);
            assertTrue(proc.hasAsynchronousHandoff(exchange), asyncEndpoint);
        } finally {
            KafkaRecordStreamingProcessor.unregisterAsynchronousHandoffTracking(context);
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
        KafkaRecordStreamingProcessor.registerAsynchronousHandoffTracking(context);
        KafkaExactlyOnceMetadata metadata = newMetadata();
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

            Exchange exchange = context.createProducerTemplate().send("direct:multiple-send-source",
                    e -> {
                        e.getMessage().setBody("test");
                        e.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, metadata);
                    });

            String lastEndpoint = exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT, String.class);
            assertTrue(lastEndpoint != null && lastEndpoint.startsWith("direct:"),
                    "The final TO_ENDPOINT should be the later direct send, but was " + lastEndpoint);
            assertTrue(metadata.hasAsynchronousKafkaHandoff(),
                    "The earlier SEDA send must be recorded on shared metadata");
            assertTrue(proc.hasAsynchronousHandoff(exchange),
                    "The earlier SEDA send must remain recorded after the later direct send");
        } finally {
            KafkaRecordStreamingProcessor.unregisterAsynchronousHandoffTracking(context);
            context.stop();
        }
    }

    @Test
    void recognizesAllFiveSupportedAsynchronousHandoffSchemes() {
        for (String scheme : List.of("seda", "vm", "disruptor", "disruptor-vm", "reactive-streams")) {
            assertTrue(KafkaRecordStreamingProcessor.isAsynchronousHandoffEndpoint(scheme + ":queue"), scheme);
        }
        assertFalse(KafkaRecordStreamingProcessor.isAsynchronousHandoffEndpoint("direct:sync"));
    }

    @Test
    void asynchronousHandoffOnAnExchangeCopyMarksTheSharedMetadata() throws Exception {
        CamelContext context = new DefaultCamelContext();
        KafkaRecordStreamingProcessor.registerAsynchronousHandoffTracking(context);
        KafkaExactlyOnceMetadata metadata = newMetadata();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:copy-handoff-source").process(exchange -> {
                        Exchange copy = exchange.copy();
                        context.createProducerTemplate().send("seda:copy-handoff", copy);
                    });
                    from("seda:copy-handoff").process(e -> {
                    });
                }
            });
            context.start();

            context.createProducerTemplate().send("direct:copy-handoff-source",
                    exchange -> exchange.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, metadata));

            assertTrue(metadata.hasAsynchronousKafkaHandoff(),
                    "The source must observe a hand-off raised by a copied exchange");
        } finally {
            KafkaRecordStreamingProcessor.unregisterAsynchronousHandoffTracking(context);
            context.stop();
        }
    }

    @Test
    void handoffNotifierIsReferenceCountedAndRemovedAfterTheLastConsumerStops() throws Exception {
        CamelContext context = new DefaultCamelContext();
        KafkaRecordStreamingProcessor.registerAsynchronousHandoffTracking(context);
        KafkaRecordStreamingProcessor.registerAsynchronousHandoffTracking(context);
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:lifecycle-handoff").to("seda:lifecycle-handoff");
                    from("seda:lifecycle-handoff").process(e -> {
                    });
                }
            });
            context.start();

            KafkaRecordStreamingProcessor.unregisterAsynchronousHandoffTracking(context);
            KafkaExactlyOnceMetadata first = newMetadata();
            context.createProducerTemplate().send("direct:lifecycle-handoff",
                    exchange -> exchange.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, first));
            assertTrue(first.hasAsynchronousKafkaHandoff(),
                    "The notifier must remain registered while another source consumer still references it");

            KafkaRecordStreamingProcessor.unregisterAsynchronousHandoffTracking(context);
            KafkaExactlyOnceMetadata afterStop = newMetadata();
            context.createProducerTemplate().send("direct:lifecycle-handoff",
                    exchange -> exchange.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, afterStop));
            assertFalse(afterStop.hasAsynchronousKafkaHandoff(),
                    "The notifier must be removed when the last source consumer stops");
        } finally {
            KafkaRecordStreamingProcessor.unregisterAsynchronousHandoffTracking(context);
            KafkaRecordStreamingProcessor.unregisterAsynchronousHandoffTracking(context);
            context.stop();
        }
    }

    private static KafkaExactlyOnceMetadata newMetadata() {
        ConsumerGroupMetadata group = mock(ConsumerGroupMetadata.class);
        when(group.groupId()).thenReturn("test-group");
        when(group.generationId()).thenReturn(1);
        when(group.memberId()).thenReturn("test-member");
        when(group.groupInstanceId()).thenReturn(Optional.empty());
        DefaultKafkaManualCommit manual = mock(DefaultKafkaManualCommit.class);
        when(manual.getPartition()).thenReturn(new TopicPartition("test-topic", 0));
        when(manual.getRecordOffset()).thenReturn(0L);
        when(manual.getLeaderEpoch()).thenReturn(Optional.empty());
        when(manual.getConsumerGroupMetadata()).thenReturn(group);
        return KafkaExactlyOnceMetadata.fromManualCommits(List.of(manual));
    }

}
