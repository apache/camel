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

package org.apache.camel.component.kafka.consumer.support.resume;

import java.util.Collection;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResumeRebalanceListener implements ConsumerRebalanceListener {
    private static final Logger LOG = LoggerFactory.getLogger(ResumeRebalanceListener.class);

    private final String threadId;
    private final KafkaConfiguration configuration;
    private final CommitManager commitManager;
    private final KafkaResumeAdapter resumeAdapter;

    public ResumeRebalanceListener(String threadId, KafkaConfiguration configuration,
                                   CommitManager commitManager, Consumer<?, ?> consumer, ResumeStrategy resumeStrategy) {
        this.threadId = threadId;
        this.configuration = configuration;
        this.commitManager = commitManager;

        resumeAdapter = resumeStrategy.getAdapter(KafkaResumeAdapter.class);
        resumeAdapter.setConsumer(consumer);
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

        resumeAdapter.resume();
    }

}
