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
package org.apache.camel.component.aws2.kinesis;

import java.nio.ByteBuffer;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the KCL consumer only checkpoints after a batch is processed successfully, so a processing failure leaves
 * the records to be redelivered rather than silently skipped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KclKinesis2ConsumerRecordProcessingTest {

    @Mock
    private Processor processor;
    @Mock
    private RecordProcessorCheckpointer checkpointer;
    @Mock
    private KinesisClientRecord record;

    private final DefaultCamelContext context = new DefaultCamelContext();
    private KclKinesis2Consumer.CamelKinesisRecordProcessor recordProcessor;

    @BeforeEach
    public void setup() {
        Kinesis2Component component = new Kinesis2Component(context);
        component.start();
        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setStreamName("stream");
        configuration.setApplicationName("app");
        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:stream", configuration, component);
        endpoint.start();
        KclKinesis2Consumer consumer = new KclKinesis2Consumer(endpoint, processor);
        recordProcessor = consumer.new CamelKinesisRecordProcessor(endpoint);

        when(record.data()).thenReturn(ByteBuffer.wrap("hello".getBytes()));
        when(record.partitionKey()).thenReturn("pk");
        when(record.sequenceNumber()).thenReturn("1");
    }

    private ProcessRecordsInput input() {
        return ProcessRecordsInput.builder().records(List.of(record)).checkpointer(checkpointer).build();
    }

    @Test
    public void checkpointsAfterSuccessfulBatch() throws Exception {
        recordProcessor.processRecords(input());

        verify(processor, times(1)).process(any(Exchange.class));
        verify(checkpointer, times(1)).checkpoint();
    }

    @Test
    public void doesNotCheckpointWhenProcessingFails() throws Exception {
        doThrow(new RuntimeException("processing failed")).when(processor).process(any(Exchange.class));

        recordProcessor.processRecords(input());

        // The failed batch must not be checkpointed, so KCL redelivers it from the last checkpoint.
        verify(checkpointer, never()).checkpoint();
    }
}
