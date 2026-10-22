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

package org.apache.camel.component.aws2.kinesis.consumer;

import org.apache.camel.resume.Offset;
import org.apache.camel.resume.ResumeAction;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

/**
 * Subclasses must provide a public no-arg constructor — the consumer creates per-shard instances via reflection.
 */
public class KinesisResumeAction implements ResumeAction {

    private GetShardIteratorRequest.Builder builder;
    private String streamName;
    private String shardId;

    public KinesisResumeAction() {
    }

    public KinesisResumeAction(GetShardIteratorRequest.Builder builder) {
        this.builder = builder;
    }

    public void setBuilder(GetShardIteratorRequest.Builder builder) {
        this.builder = builder;
    }

    protected GetShardIteratorRequest.Builder getBuilder() {
        return builder;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getShardId() {
        return shardId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }

    /**
     * Starts the shard of this action after the sequence number stored for it in the resume cache. The adapter calls
     * this for every entry of the cache, so the entries of the other shards are left alone. The value is the sequence
     * number as a String, or the {@link KinesisOffset} that {@link KinesisResumeStrategy} stores.
     *
     * @return false, the entry is not invalidated: it is the position of the shard when it needs a new shard iterator
     *         again
     */
    @Override
    public boolean evalEntry(Object shardId, Object sequenceNumber) {
        if (this.shardId != null && !this.shardId.equals(shardId)) {
            return false;
        }
        Object value = sequenceNumber instanceof Offset<?> offset ? offset.getValue() : sequenceNumber;
        if (value == null) {
            return false;
        }
        builder.shardId(shardId.toString());
        builder.shardIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);
        builder.startingSequenceNumber(value.toString());
        return false;
    }
}
