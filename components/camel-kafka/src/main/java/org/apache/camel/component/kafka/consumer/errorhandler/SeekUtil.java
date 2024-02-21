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

import java.util.Set;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SeekUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SeekUtil.class);

    private SeekUtil() {

    }

    public static void seekToNextOffset(Consumer<?, ?> consumer, long partitionLastOffset) {
        boolean logged = false;
        Set<TopicPartition> tps = consumer.assignment();
        if (tps != null && partitionLastOffset != -1) {
            long next = partitionLastOffset + 1;

            for (TopicPartition tp : tps) {
                if (LOG.isInfoEnabled()) {
                    LOG.info(
                            "Consumer seeking to next offset {} to continue polling next message from topic {} on partition {}",
                            next, tp.topic(), tp.partition());
                }

                consumer.seek(tp, next);
            }
        } else if (tps != null) {
            for (TopicPartition tp : tps) {
                long next = consumer.position(tp) + 1;
                if (!logged) {
                    LOG.info(
                            "Consumer seeking to next offset {} to continue polling next message from topic {} on partition {}",
                            next,
                            tp.topic(), tp.partition());
                    logged = true;
                }
                consumer.seek(tp, next);
            }
        }
    }
}
