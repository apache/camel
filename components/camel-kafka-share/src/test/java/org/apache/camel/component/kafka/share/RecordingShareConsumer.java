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
package org.apache.camel.component.kafka.share;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockShareConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicIdPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;

/**
 * A {@link MockShareConsumer} that records the acknowledgements and commits, and whose poll waits for records instead
 * of returning at once.
 */
class RecordingShareConsumer extends MockShareConsumer<Object, Object> {

    record Acknowledgement(long offset, AcknowledgeType type) {
    }

    private final List<Acknowledgement> acknowledgements = Collections.synchronizedList(new ArrayList<>());
    private volatile int commits;
    private volatile int polls;
    private volatile boolean closed;
    private RuntimeException pollFailure;
    private boolean hasRecords;

    static ConsumerRecord<Object, Object> record(String topic, long offset, Object key, Object value, short deliveryCount) {
        return new ConsumerRecord<>(
                topic, 0, offset, 1000L + offset, TimestampType.CREATE_TIME, -1, -1, key, value, new RecordHeaders(),
                Optional.empty(), Optional.of(deliveryCount));
    }

    @Override
    public synchronized void addRecord(ConsumerRecord<Object, Object> record) {
        super.addRecord(record);
        hasRecords = true;
        notifyAll();
    }

    @Override
    public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
        polls++;
        if (pollFailure != null) {
            RuntimeException failure = pollFailure;
            pollFailure = null;
            throw failure;
        }
        if (!hasRecords) {
            try {
                wait(Math.min(timeout.toMillis(), 100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        hasRecords = false;
        return super.poll(timeout);
    }

    @Override
    public synchronized void acknowledge(ConsumerRecord<Object, Object> record, AcknowledgeType type) {
        acknowledgements.add(new Acknowledgement(record.offset(), type));
    }

    @Override
    public synchronized Map<TopicIdPartition, Optional<KafkaException>> commitSync(Duration timeout) {
        commits++;
        return super.commitSync(timeout);
    }

    @Override
    public synchronized void close(Duration timeout) {
        closed = true;
        super.close(timeout);
    }

    /**
     * The next poll throws the exception.
     */
    synchronized void failNextPoll(RuntimeException failure) {
        this.pollFailure = failure;
    }

    int getPolls() {
        return polls;
    }

    boolean isClosed() {
        return closed;
    }

    List<Acknowledgement> getAcknowledgements() {
        return List.copyOf(acknowledgements);
    }

    int getCommits() {
        return commits;
    }
}
