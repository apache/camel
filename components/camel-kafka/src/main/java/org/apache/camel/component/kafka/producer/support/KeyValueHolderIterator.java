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

package org.apache.camel.component.kafka.producer.support;

import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.util.KeyValueHolder;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import static org.apache.camel.component.kafka.producer.support.ProducerUtil.tryConvertToSerializedType;

public class KeyValueHolderIterator implements Iterator<KeyValueHolder<Object, ProducerRecord>> {
    private final Iterator<Object> msgList;
    private final Exchange exchange;
    private final KafkaConfiguration kafkaConfiguration;
    private final String msgTopic;
    private final List<Header> propagatedHeaders;

    public KeyValueHolderIterator(Iterator<Object> msgList, Exchange exchange, KafkaConfiguration kafkaConfiguration,
                                  String msgTopic, List<Header> propagatedHeaders) {
        this.msgList = msgList;
        this.exchange = exchange;
        this.kafkaConfiguration = kafkaConfiguration;
        this.msgTopic = msgTopic;
        this.propagatedHeaders = propagatedHeaders;
    }

    @Override
    public boolean hasNext() {
        return msgList.hasNext();
    }

    @Override
    public KeyValueHolder<Object, ProducerRecord> next() {
        // must convert each entry of the iterator into the value
        // according to the serializer
        Object next = msgList.next();
        String innerTopic = msgTopic;
        Object innerKey = null;
        Integer innerPartitionKey = null;
        Long innerTimestamp = null;

        Object value = next;
        Exchange ex = null;
        Object body = next;

        if (next instanceof Exchange || next instanceof Message) {
            Exchange innerExchange = null;
            Message innerMessage = null;
            if (next instanceof Exchange) {
                innerExchange = (Exchange) next;
                innerMessage = innerExchange.getIn();
            } else {
                innerMessage = (Message) next;
            }

            innerTopic = getInnerTopic(innerTopic, innerMessage);

            if (innerMessage.getHeader(KafkaConstants.PARTITION_KEY) != null) {
                innerPartitionKey = getInnerPartitionKey(innerMessage);
            }

            if (innerMessage.getHeader(KafkaConstants.KEY) != null) {
                innerKey = getInnerKey(innerExchange, innerMessage);
            }

            innerTimestamp = getOverrideTimestamp(innerTimestamp, innerMessage);

            ex = innerExchange == null ? exchange : innerExchange;
            value = tryConvertToSerializedType(ex, innerMessage.getBody(),
                    kafkaConfiguration.getValueSerializer());
        }

        return new KeyValueHolder(
                body,
                new ProducerRecord(
                        innerTopic, innerPartitionKey, innerTimestamp, innerKey, value, propagatedHeaders));
    }

    private boolean hasValidTimestampHeader(Message innerMessage) {
        if (innerMessage.getHeader(KafkaConstants.OVERRIDE_TIMESTAMP) != null) {
            return innerMessage.getHeader(KafkaConstants.OVERRIDE_TIMESTAMP) instanceof Long;
        }

        return false;
    }

    private Long getOverrideTimestamp(Long innerTimestamp, Message innerMessage) {
        if (hasValidTimestampHeader(innerMessage)) {
            innerTimestamp = (Long) innerMessage.removeHeader(KafkaConstants.OVERRIDE_TIMESTAMP);
        }

        return innerTimestamp;
    }

    private String getInnerTopic(String innerTopic, Message innerMessage) {
        if (innerMessage.getHeader(KafkaConstants.OVERRIDE_TOPIC) != null) {
            innerTopic = (String) innerMessage.removeHeader(KafkaConstants.OVERRIDE_TOPIC);
        }

        return innerTopic;
    }

    private Object getInnerKey(Exchange innerExchange, Message innerMmessage) {
        Object innerKey;
        innerKey = kafkaConfiguration.getKey() != null
                ? kafkaConfiguration.getKey() : innerMmessage.getHeader(KafkaConstants.KEY);
        if (innerKey != null) {
            innerKey = tryConvertToSerializedType(innerExchange, innerKey,
                    kafkaConfiguration.getKeySerializer());
        }
        return innerKey;
    }

    private Integer getInnerPartitionKey(Message innerMessage) {
        return kafkaConfiguration.getPartitionKey() != null
                ? kafkaConfiguration.getPartitionKey()
                : innerMessage.getHeader(KafkaConstants.PARTITION_KEY, Integer.class);
    }

    @Override
    public void remove() {
        msgList.remove();
    }
}
