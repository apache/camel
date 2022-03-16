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

package org.apache.camel.component.kafka.consumer.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor.serializeOffsetKey;

public class PartitionAssignmentListener implements ConsumerRebalanceListener {
    private static final Logger LOG = LoggerFactory.getLogger(PartitionAssignmentListener.class);

    private final String threadId;
    private final KafkaConfiguration configuration;
    private final Map<String, Long> lastProcessedOffset;
    private final KafkaConsumerResumeStrategy resumeStrategy;
    private final CommitManager commitManager;
    private final Supplier<Boolean> stopStateSupplier;

    public PartitionAssignmentListener(String threadId, KafkaConfiguration configuration,
                                       Map<String, Long> lastProcessedOffset,
                                       Supplier<Boolean> stopStateSupplier, CommitManager commitManager,
                                       KafkaConsumerResumeStrategy resumeStrategy) {
        this.threadId = threadId;
        this.configuration = configuration;
        this.lastProcessedOffset = lastProcessedOffset;
        this.commitManager = commitManager;
        this.stopStateSupplier = stopStateSupplier;
        this.resumeStrategy = resumeStrategy;

    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {

        // if camel is stopping, or we are not running
        boolean stopping = stopStateSupplier.get();

        for (TopicPartition partition : partitions) {
            LOG.debug("onPartitionsRevoked: {} from {}", threadId, partition.topic());

            String offsetKey = serializeOffsetKey(partition);
            Long offset = lastProcessedOffset.get(offsetKey);
            if (offset == null) {
                offset = -1L;
            }
            try {
                // only commit offsets if the component has control
                if (!configuration.getAutoCommitEnable() && offset != -1L) {
                    if (stopping) {
                        commitManager.commitOffsetOnStop(partition, offset);
                    } else {
                        commitManager.commitOffset(partition, offset);
                    }

                }
            } catch (Exception e) {
                LOG.error("Error saving offset repository state {} from offsetKey {} with offset: {}", threadId, offsetKey,
                        offset);
                throw e;
            } finally {
                lastProcessedOffset.remove(offsetKey);
            }
        }
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {

        if (LOG.isDebugEnabled()) {
            partitions.forEach(p -> LOG.debug("onPartitionsAssigned: {} from {}", threadId, p.topic()));

        }
        List<KafkaResumable> resumables = partitions.stream()
                .map(p -> new KafkaResumable(String.valueOf(p.partition()), p.topic())).collect(Collectors.toList());

        resumables.forEach(r -> resumeStrategy.resume(r));
    }
}
