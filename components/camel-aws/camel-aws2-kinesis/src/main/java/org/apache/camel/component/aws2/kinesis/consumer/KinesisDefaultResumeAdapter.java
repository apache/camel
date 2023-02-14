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

import java.nio.ByteBuffer;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.annotations.JdkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

@JdkService(ResumeAdapter.RESUME_ADAPTER_FACTORY)
public class KinesisDefaultResumeAdapter implements KinesisResumeAdapter, Cacheable, Deserializable {
    private static final Logger LOG = LoggerFactory.getLogger(KinesisDefaultResumeAdapter.class);

    private ResumeCache<String> cache;

    private GetShardIteratorRequest.Builder resumable;
    private String streamName;

    public void setRequestBuilder(GetShardIteratorRequest.Builder resumable) {
        this.resumable = resumable;
    }

    @Override
    public void resume() {
        assert streamName != null;
        assert resumable != null;

        KinesisOffset offset = cache.get(streamName, KinesisOffset.class);

        if (offset == null) {
            LOG.info("There is no offset for the stream {}", streamName);
            return;
        }

        final String sequenceNumber = offset.getValue();
        LOG.info("Resuming from offset {} for key {}", sequenceNumber, streamName);

        resumable.shardIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER);
        resumable.startingSequenceNumber(sequenceNumber);
    }

    private void add(Object key, Object offset) {
        KinesisOffset ko = (KinesisOffset) cache.computeIfAbsent((String) key, k -> new KinesisOffset());

        ko.update((String) offset);
    }

    @Override
    public boolean add(OffsetKey<?> key, Offset<?> offset) {
        add(key.getValue(), offset.getValue());

        return true;
    }

    @Override
    public void setCache(ResumeCache<?> cache) {
        this.cache = (ResumeCache<String>) cache;
    }

    @Override
    public ResumeCache<?> getCache() {
        return cache;
    }

    @Override
    public boolean deserialize(ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
        Object keyObj = deserializeKey(keyBuffer);
        Object valueObj = deserializeValue(valueBuffer);

        add(keyObj, valueObj);

        return true;
    }

    @Override
    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }
}
