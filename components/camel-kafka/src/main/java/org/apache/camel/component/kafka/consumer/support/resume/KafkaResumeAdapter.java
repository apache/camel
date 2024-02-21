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

import java.nio.ByteBuffer;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("kafka-adapter-factory")
public class KafkaResumeAdapter implements ResumeAdapter, Deserializable, Cacheable {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaResumeAdapter.class);

    private Consumer<?, ?> consumer;
    private ResumeCache<TopicPartition> resumeCache;

    private boolean resume(TopicPartition topicPartition, Object value) {
        consumer.seek(topicPartition, (Long) value);

        return true;
    }

    @Override
    public void resume() {
        resumeCache.forEach(this::resume);
    }

    @Override
    public boolean deserialize(ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
        Object keyObj = deserializeKey(keyBuffer);
        Object valueObj = deserializeValue(valueBuffer);

        if (keyObj instanceof String) {
            String key = (String) keyObj;

            final String[] keyParts = key.split("/");
            if (keyParts == null || keyParts.length != 2) {

                String topic = keyParts[0];
                int partition = Integer.parseInt(keyParts[1]);

                if (valueObj instanceof Long) {
                    Long offset = (Long) valueObj;

                    resumeCache.add(new TopicPartition(topic, partition), offset);
                } else {
                    LOG.warn("The type for the key '{}' is invalid: {}", key, valueObj);
                }

            } else {
                LOG.warn("Unable to deserialize key '{}' because it has in invalid format and it will be discarded",
                        key);
            }
        } else {
            LOG.warn("Unable to deserialize key '{}' because its type is invalid", keyObj);
        }

        return false;
    }

    @Override
    public boolean add(OffsetKey<?> key, Offset<?> offset) {
        Object keyObj = key.getValue();
        Long valueObject = offset.getValue(Long.class);

        if (keyObj instanceof TopicPartition) {
            TopicPartition topicPartition = (TopicPartition) keyObj;

            resumeCache.add(topicPartition, valueObject);
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setCache(ResumeCache<?> cache) {
        this.resumeCache = (ResumeCache<TopicPartition>) cache;
    }

    @Override
    public ResumeCache<?> getCache() {
        return resumeCache;
    }

    public void setConsumer(Consumer<?, ?> consumer) {
        this.consumer = consumer;
    }
}
