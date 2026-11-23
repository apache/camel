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

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

/**
 * A processing facade that allows processing consumer records in different ways
 */
public interface KafkaRecordProcessorFacade {

    /**
     * Sends a set of records polled from Kafka for processing
     *
     * @param  allRecords All records received from a call to the Kafka's consumer poll method
     * @return            The result of processing this set of records
     */
    ProcessingResult processPolledRecords(ConsumerRecords<Object, Object> allRecords);

    /**
     * Drops the records of the given partitions that were polled but are not processed yet, such as the records held
     * for an incomplete batch. Called when the partitions are revoked or lost: they must not be processed and committed
     * by this consumer any more, and their next owner reads them again from the committed offset. Also called when a
     * resume seeks the partitions back to their committed offset, from where the consumer reads them again.
     *
     * @param partitions the partitions that are no longer assigned to the consumer, or that are read again
     */
    default void discardPendingRecords(Collection<TopicPartition> partitions) {
        // NO-OP: by default the records of a poll are processed before the next poll
    }
}
