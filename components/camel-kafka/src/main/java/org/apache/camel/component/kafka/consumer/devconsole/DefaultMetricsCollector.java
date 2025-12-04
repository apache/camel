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

package org.apache.camel.component.kafka.consumer.devconsole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default collector if the dev console is enabled for Kafka
 */
public class DefaultMetricsCollector implements DevConsoleMetricsCollector {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultMetricsCollector.class);
    private final String threadId;

    public DefaultMetricsCollector(String threadId) {
        this.threadId = threadId;
    }

    // dev-console records and state

    private volatile GroupMetadata groupMetadata;
    private volatile KafkaTopicPosition lastRecord;
    private final List<KafkaTopicPosition> commitRecords = new ArrayList<>();
    private final AtomicBoolean commitRecordsRequested = new AtomicBoolean();
    private final AtomicReference<CountDownLatch> latch = new AtomicReference<>();

    @Override
    public void storeMetadata(Consumer<?, ?> consumer) {
        // store metadata
        ConsumerGroupMetadata meta = consumer.groupMetadata();
        if (meta != null) {
            groupMetadata = new GroupMetadata(
                    meta.groupId(), meta.groupInstanceId().orElse(""), meta.memberId(), meta.generationId());
        }
    }

    @Override
    public void storeLastRecord(ProcessingResult result) {
        // dev-console uses information from last processed record
        lastRecord = new KafkaTopicPosition(result.getTopic(), result.getPartition(), result.getOffset(), 0);
    }

    @Override
    public void collectCommitMetrics(Consumer<?, ?> consumer) {
        if (commitRecordsRequested.compareAndSet(true, false)) {
            try {
                Map<TopicPartition, OffsetAndMetadata> commits = consumer.committed(consumer.assignment());
                commitRecords.clear();
                for (var e : commits.entrySet()) {
                    KafkaTopicPosition p = new KafkaTopicPosition(
                            e.getKey().topic(),
                            e.getKey().partition(),
                            e.getValue().offset(),
                            e.getValue().leaderEpoch().orElse(0));
                    commitRecords.add(p);
                }
                CountDownLatch count = latch.get();
                if (count != null) {
                    count.countDown();
                }
            } catch (Exception e) {
                // ignore cannot get last commit details
                LOG.debug(
                        "Cannot get last offset committed from Kafka brokers due to: {}. This exception is ignored.",
                        e.getMessage(),
                        e);
            }
        }
    }

    // dev console information
    // ------------------------------------------------------------------------

    @Override
    public GroupMetadata getGroupMetadata() {
        return groupMetadata;
    }

    @Override
    public KafkaTopicPosition getLastRecord() {
        return lastRecord;
    }

    @Override
    public String getThreadId() {
        return threadId;
    }

    @Override
    public List<KafkaTopicPosition> getCommitRecords() {
        return Collections.unmodifiableList(commitRecords);
    }

    @Override
    public CountDownLatch fetchCommitRecords() {
        // use a latch to wait for commit records to be ready
        // as the consumer thread must be calling Kafka brokers to get this information
        // so this thread need to wait for that to be complete
        CountDownLatch answer = new CountDownLatch(1);
        latch.set(answer);
        commitRecordsRequested.set(true);
        return answer;
    }
}
