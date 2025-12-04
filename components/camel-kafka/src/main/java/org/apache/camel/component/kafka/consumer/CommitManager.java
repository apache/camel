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

import org.apache.camel.Exchange;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

public interface CommitManager {

    KafkaManualCommit getManualCommit(
            Exchange exchange, TopicPartition partition, ConsumerRecord<Object, Object> consumerRecord);

    /**
     * Commits everything that has been cached
     */
    void commit();

    /**
     * Commits the offsets of the given partition
     *
     * @param partition the partition to commit the offsets
     */
    void commit(TopicPartition partition);

    /**
     * Forcefully commits the offset of the given partition
     *
     * @param partition           the partition to commit the offsets
     * @param partitionLastOffset the last offset to commit
     */
    void forceCommit(TopicPartition partition, long partitionLastOffset);

    /**
     * Record the last processed offset for future commit
     *
     * @param partition           the partition to commit the offsets
     * @param partitionLastOffset the last offset to commit
     */
    void recordOffset(TopicPartition partition, long partitionLastOffset);
}
