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

public final class CommitManagers {
    private CommitManagers() {
    }

    public static CommitManager createCommitManager(
            Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        KafkaConfiguration configuration = kafkaConsumer.getEndpoint().getConfiguration();

        if (!configuration.isAllowManualCommit() && configuration.getOffsetRepository() != null) {
            return new CommitToOffsetManager(consumer, kafkaConsumer, threadId, printableTopic);
        }

        if (configuration.isAutoCommitEnable()) {
            return new AsyncCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
        }

        KafkaManualCommitFactory manualCommitFactory = kafkaConsumer.getEndpoint().getKafkaManualCommitFactory();
        if (manualCommitFactory instanceof DefaultKafkaManualAsyncCommitFactory) {
            return new AsyncCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
        }

        return new NoopCommitManager(consumer, kafkaConsumer, threadId, printableTopic);
    }
}
