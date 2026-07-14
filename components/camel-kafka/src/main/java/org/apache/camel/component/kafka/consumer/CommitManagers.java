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

package org.apache.camel.component.kafka.consumer;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommitManagers {
    private static final Logger LOG = LoggerFactory.getLogger(CommitManagers.class);

    private CommitManagers() {
    }

    public static CommitManager createCommitManager(
            Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        KafkaConfiguration configuration = kafkaConsumer.getEndpoint().getConfiguration();

        if (configuration.isAllowManualCommit()) {
            LOG.debug("Allowing manual commit management");
            KafkaManualCommitFactory manualCommitFactory = kafkaConsumer.getEndpoint().getKafkaManualCommitFactory();
            if (manualCommitFactory instanceof DefaultKafkaManualAsyncCommitFactory) {
                LOG.debug("Using an async commit manager for manual commit management");
                return new AsyncCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
            } else {
                if (manualCommitFactory instanceof DefaultKafkaManualCommitFactory) {
                    LOG.debug("Using a sync commit manager for manual commit management");
                    return new SyncCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
                } else {
                    // This has been the default behavior for Camel
                    LOG.debug("Using an NO-OP commit manager for manual commit management");
                    return new NoopCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
                }
            }
        } else {
            if (configuration.getOffsetRepository() != null) {
                LOG.debug("Using a commit-to-offset manager for commit management");
                return new CommitToOffsetManager(consumer, kafkaConsumer, threadId, printableTopic);
            }

            if (configuration.isBatching()) {
                LOG.debug("Using an async commit manager for auto commit management with batch processing");
                return new AsyncCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
            }
        }

        LOG.debug("Using a NO-OP commit manager with auto-commit enabled on the Kafka consumer");
        return new NoopCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
    }
}
