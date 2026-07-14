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

package org.apache.camel.component.kafka.consumer.errorhandler;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SeekUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SeekUtil.class);

    private SeekUtil() {

    }

    public static void seekToNextOffset(Consumer<?, ?> consumer, Exception exception) {
        if (exception instanceof RecordDeserializationException rde) {
            TopicPartition tp = rde.topicPartition();
            long next = rde.offset() + 1;
            LOG.info("Consumer seeking to next offset {} to skip poison message on topic {} partition {}",
                    next, tp.topic(), tp.partition());
            consumer.seek(tp, next);
        } else {
            LOG.warn("Non-record exception caught during poll, not advancing any partition offsets: {}",
                    exception.getMessage());
        }
    }
}
