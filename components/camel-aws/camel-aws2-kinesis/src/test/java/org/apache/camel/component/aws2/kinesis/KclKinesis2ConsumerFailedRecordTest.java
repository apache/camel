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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A record whose exchange fails in a real route. By default it is reported to the exception handler and checkpointed
 * with its batch. With {@code kclStopCheckpointOnFailure} the checkpoint must not move past it: KCL does not deliver a
 * batch again while the lease of the shard is held (it goes on with the next batch, and {@code checkpoint()} covers
 * every record delivered so far), so the failed record is only consumed again by the next holder of the lease, from the
 * checkpoint.
 */
class KclKinesis2ConsumerFailedRecordTest {

    // the records whose exchange fails (each time they are routed)
    private final Set<String> failing = new CopyOnWriteArraySet<>(Set.of("r1"));
    private final List<String> routed = new CopyOnWriteArrayList<>();
    private DefaultCamelContext context;
    private KclKinesis2Consumer consumer;
    private Kinesis2Component component;
    private Kinesis2Endpoint endpoint;
    private final ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);

    private void setup(boolean stopCheckpointOnFailure) throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:kcl").routeId("kcl").process(e -> {
                    String body = new String(e.getMessage().getBody(byte[].class), StandardCharsets.UTF_8);
                    routed.add(body);
                    if (failing.contains(body)) {
                        throw new IllegalStateException("cannot process " + body);
                    }
                });
            }
        });
        context.start();

        component = new Kinesis2Component(context);
        component.start();
        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setStreamName("stream");
        configuration.setApplicationName("app");
        // Provide a client so the endpoint does not build a real AWS client (which needs a region) on start.
        configuration.setAmazonKinesisClient(mock(KinesisClient.class));
        configuration.setKclStopCheckpointOnFailure(stopCheckpointOnFailure);
        endpoint = new Kinesis2Endpoint("aws2-kinesis:stream", configuration, component);
        endpoint.start();
        // the processor a route gives to its consumer: a failed exchange is returned with the exception set on it
        consumer = new KclKinesis2Consumer(endpoint, context.getRoute("kcl").getConsumer().getProcessor());
        consumer.setExceptionHandler(exceptionHandler);
    }

    @AfterEach
    void tearDown() {
        if (endpoint != null) {
            endpoint.stop();
        }
        if (component != null) {
            component.stop();
        }
        if (context != null) {
            context.stop();
        }
    }

    private KclKinesis2Consumer.CamelKinesisRecordProcessor newLeaseHolder() {
        KclKinesis2Consumer.CamelKinesisRecordProcessor processor = consumer.new CamelKinesisRecordProcessor(endpoint);
        processor.initialize(InitializationInput.builder().shardId("shardId-000000000000").build());
        return processor;
    }

    private static ProcessRecordsInput batch(RecordProcessorCheckpointer checkpointer, int from, int to) {
        List<KinesisClientRecord> records = new ArrayList<>();
        for (int i = from; i < to; i++) {
            records.add(KinesisClientRecord.builder().sequenceNumber(String.valueOf(100 + i)).subSequenceNumber(0)
                    .partitionKey("pk").data(ByteBuffer.wrap(("r" + i).getBytes(StandardCharsets.UTF_8))).build());
        }
        return ProcessRecordsInput.builder().records(records).checkpointer(checkpointer).build();
    }

    @Test
    void checkpointStopsBeforeFailedRecord() throws Exception {
        setup(true);
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        KclKinesis2Consumer.CamelKinesisRecordProcessor processor = newLeaseHolder();

        // r1 fails: only r0 may be checkpointed
        processor.processRecords(batch(checkpointer, 0, 3));
        assertEquals(List.of("r0", "r1", "r2"), routed);
        verify(checkpointer, never()).checkpoint();
        verify(checkpointer, times(1)).checkpoint("100", 0L);

        // the next batches of this lease are routed, but the checkpoint does not move past r1
        processor.processRecords(batch(checkpointer, 3, 5));
        assertEquals(List.of("r0", "r1", "r2", "r3", "r4"), routed);

        // nor when the route is stopped (KCL graceful shutdown)
        processor.shutdownRequested(ShutdownRequestedInput.builder().checkpointer(checkpointer).build());

        verify(checkpointer, never()).checkpoint();
        verify(checkpointer, times(1)).checkpoint(anyString(), anyLong());
    }

    @Test
    void failedFirstRecordIsNotCheckpointed() throws Exception {
        setup(true);
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        KclKinesis2Consumer.CamelKinesisRecordProcessor processor = newLeaseHolder();

        processor.processRecords(batch(checkpointer, 1, 3));
        processor.shutdownRequested(ShutdownRequestedInput.builder().checkpointer(checkpointer).build());

        assertEquals(List.of("r1", "r2"), routed);
        verify(checkpointer, never()).checkpoint();
        verify(checkpointer, never()).checkpoint(anyString(), anyLong());
    }

    @Test
    void nextLeaseHolderConsumesFailedRecordAgain() throws Exception {
        setup(true);
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        newLeaseHolder().processRecords(batch(checkpointer, 1, 3));
        verify(checkpointer, never()).checkpoint();

        // the next lease holder reads from the checkpoint, before r1; this time r1 succeeds
        failing.clear();
        RecordProcessorCheckpointer next = mock(RecordProcessorCheckpointer.class);
        newLeaseHolder().processRecords(batch(next, 1, 3));

        assertEquals(List.of("r1", "r2", "r1", "r2"), routed);
        verify(next, times(1)).checkpoint();
    }

    @Test
    void recordThatAlwaysFailsKeepsTheCheckpointBeforeIt() throws Exception {
        setup(true);
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        newLeaseHolder().processRecords(batch(checkpointer, 1, 3));

        // a record that fails each time is routed again by each lease holder, and the checkpoint stays before it
        RecordProcessorCheckpointer next = mock(RecordProcessorCheckpointer.class);
        newLeaseHolder().processRecords(batch(next, 1, 3));

        assertEquals(List.of("r1", "r2", "r1", "r2"), routed);
        verify(checkpointer, never()).checkpoint();
        verify(next, never()).checkpoint();
        verify(next, never()).checkpoint(anyString(), anyLong());
    }

    @Test
    void shardEndIsCheckpointedAfterFailure() throws Exception {
        setup(true);
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        KclKinesis2Consumer.CamelKinesisRecordProcessor processor = newLeaseHolder();
        processor.processRecords(batch(checkpointer, 1, 2));

        // KCL goes on with the child shards only once the end of the shard is checkpointed
        processor.shardEnded(ShardEndedInput.builder().checkpointer(checkpointer).build());

        verify(checkpointer, times(1)).checkpoint();
    }

    @Test
    void failedRecordIsReportedToTheExceptionHandler() throws Exception {
        setup(true);
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);

        newLeaseHolder().processRecords(batch(checkpointer, 0, 3));

        verify(exceptionHandler, times(1)).handleException(anyString(), any(Exchange.class), any(Throwable.class));
    }

    @Test
    void byDefaultFailedRecordIsReportedAndCheckpointed() throws Exception {
        setup(false);
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        KclKinesis2Consumer.CamelKinesisRecordProcessor processor = newLeaseHolder();

        // r1 fails: it is reported to the exception handler of the consumer, and the batch is checkpointed
        processor.processRecords(batch(checkpointer, 0, 3));
        assertEquals(List.of("r0", "r1", "r2"), routed);
        verify(exceptionHandler, times(1)).handleException(anyString(), any(Exchange.class), any(Throwable.class));
        verify(checkpointer, times(1)).checkpoint();

        processor.processRecords(batch(checkpointer, 3, 5));
        processor.shutdownRequested(ShutdownRequestedInput.builder().checkpointer(checkpointer).build());

        verify(checkpointer, times(3)).checkpoint();
        verify(checkpointer, never()).checkpoint(anyString(), anyLong());
    }
}
