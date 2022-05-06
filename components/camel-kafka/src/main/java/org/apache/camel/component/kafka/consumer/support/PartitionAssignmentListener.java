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
import java.util.stream.Collectors;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartitionAssignmentListener implements ConsumerRebalanceListener {
    private static final Logger LOG = LoggerFactory.getLogger(PartitionAssignmentListener.class);

    private final String threadId;
    private final KafkaConfiguration configuration;
    private final KafkaConsumerResumeAdapter resumeStrategy;
    private final CommitManager commitManager;

    public PartitionAssignmentListener(String threadId, KafkaConfiguration configuration,
                                       CommitManager commitManager,
                                       KafkaConsumerResumeAdapter resumeStrategy) {
        this.threadId = threadId;
        this.configuration = configuration;
        this.commitManager = commitManager;
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        for (TopicPartition partition : partitions) {
            LOG.debug("onPartitionsRevoked: {} from {}", threadId, partition.topic());

            // only commit offsets if the component has control
            if (!configuration.getAutoCommitEnable()) {
                commitManager.commit(partition);
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

        resumables.forEach(this::doResume);
    }

    private void doResume(KafkaResumable r) {
        resumeStrategy.setKafkaResumable(r);
        resumeStrategy.resume();
    }
}
