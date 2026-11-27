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

public class DefaultKafkaManualAsyncCommit extends DefaultKafkaManualCommit implements KafkaAsyncManualCommit {
    private final CommitManager commitManager;
    // the assignment of the partition to the consumer in which the record was polled
    private final long assignment;

    public DefaultKafkaManualAsyncCommit(KafkaManualCommitFactory.CamelExchangePayload camelExchangePayload,
                                         KafkaManualCommitFactory.KafkaRecordPayload recordPayload,
                                         CommitManager commitManager) {
        super(camelExchangePayload, recordPayload);
        this.commitManager = commitManager;
        this.assignment = commitManager instanceof AsyncCommitManager async ? async.assignmentOf(recordPayload.partition) : 0;
    }

    @Override
    public void commit() {
        // can be called from another thread than the consumer, such as the one of an aggregator
        if (commitManager instanceof AsyncCommitManager async) {
            // not committed if the partition was revoked or lost since the record was polled
            async.requestCommit(getPartition(), getRecordOffset(), assignment);
        } else {
            commitManager.requestCommit(getPartition(), getRecordOffset());
        }
    }
}
