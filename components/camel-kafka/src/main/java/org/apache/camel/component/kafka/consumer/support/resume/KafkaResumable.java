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

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.support.resume.OffsetKeys;
import org.apache.camel.support.resume.Offsets;

public final class KafkaResumable implements Resumable {
    private final String addressable;
    private final Long offset;

    private KafkaResumable(String addressable, Long offset) {
        this.addressable = addressable;
        this.offset = offset;
    }

    @Override
    public OffsetKey<?> getOffsetKey() {
        return OffsetKeys.unmodifiableOf(addressable);
    }

    @Override
    public Offset<?> getLastOffset() {
        return Offsets.of(offset);
    }

    /**
     * Creates a new resumable for Kafka
     *
     * @param  exchange the exchange to create the resumable from
     * @return          a new KafkaResumable instance with the data from the exchange
     */
    public static KafkaResumable of(Exchange exchange) {
        String topic = exchange.getMessage().getHeader(KafkaConstants.TOPIC, String.class);
        Integer partition = exchange.getMessage().getHeader(KafkaConstants.PARTITION, Integer.class);
        Long offset = exchange.getMessage().getHeader(KafkaConstants.OFFSET, Long.class);

        String topicPartition = topic + "/" + partition;

        return new KafkaResumable(topicPartition, offset);
    }
}
