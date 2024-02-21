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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.util.KeyValueHolder;
import org.apache.kafka.clients.producer.ProducerRecord;

import static org.apache.camel.component.kafka.producer.support.ProducerUtil.tryConvertToSerializedType;

public class KeyValueHolderIterator implements Iterator<KeyValueHolder<Object, ProducerRecord<Object, Object>>> {
    private final Iterator<Object> msgList;
    private final Exchange exchange;
    private final KafkaConfiguration kafkaConfiguration;
    private final String msgTopic;
    private final PropagatedHeadersProvider propagatedHeadersProvider;

    public KeyValueHolderIterator(Iterator<Object> msgList, Exchange exchange, KafkaConfiguration kafkaConfiguration,
                                  String msgTopic, PropagatedHeadersProvider propagatedHeadersProvider) {
        this.msgList = msgList;
        this.exchange = exchange;
        this.kafkaConfiguration = kafkaConfiguration;
        this.msgTopic = msgTopic;
        this.propagatedHeadersProvider = propagatedHeadersProvider;
    }

    @Override
    public boolean hasNext() {
        return msgList.hasNext();
    }

    @Override
    public KeyValueHolder<Object, ProducerRecord<Object, Object>> next() {
        // must convert each entry of the iterator into the value
        // according to the serializer
        final Object body = msgList.next();

        if (body instanceof Exchange || body instanceof Message) {
            final Message innerMessage = getInnerMessage(body);
            final Exchange innerExchange = getInnerExchange(body);

            final String innerTopic = getInnerTopic(innerMessage);
            final Integer innerPartitionKey = getInnerPartitionKey(innerMessage);
            final Object innerKey = getInnerKey(innerExchange, innerMessage);
            final Long innerTimestamp = getOverrideTimestamp(innerMessage);

            final Exchange ex = innerExchange == null ? exchange : innerExchange;

            final Object value = tryConvertToSerializedType(ex, innerMessage.getBody(),
                    kafkaConfiguration.getValueSerializer());

            return new KeyValueHolder<>(
                    body,
                    new ProducerRecord<>(
                            innerTopic, innerPartitionKey, innerTimestamp, innerKey, value,
                            propagatedHeadersProvider.getHeaders(ex, innerMessage)));
        }

        return new KeyValueHolder<>(
                body,
                new ProducerRecord<>(
                        msgTopic, null, null, null, body, propagatedHeadersProvider.getDefaultHeaders()));
    }

    private Message getInnerMessage(Object body) {
        if (body instanceof Exchange) {
            return ((Exchange) body).getIn();
        }

        return (Message) body;
    }

    private Exchange getInnerExchange(Object body) {
        if (body instanceof Exchange) {
            return (Exchange) body;
        }

        return null;
    }

    private Long getOverrideTimestamp(Message innerMessage) {
        Long timeStamp = null;
        Object overrideTimeStamp = innerMessage.removeHeader(KafkaConstants.OVERRIDE_TIMESTAMP);
        if (overrideTimeStamp != null) {
            timeStamp = exchange.getContext().getTypeConverter().convertTo(Long.class, exchange, overrideTimeStamp);
        }
        return timeStamp;
    }

    private String getInnerTopic(Message innerMessage) {
        if (innerMessage.getHeader(KafkaConstants.OVERRIDE_TOPIC) != null) {
            return (String) innerMessage.removeHeader(KafkaConstants.OVERRIDE_TOPIC);
        }

        return msgTopic;
    }

    private Object getInnerKey(Exchange innerExchange, Message innerMessage) {
        Object innerKey = innerMessage.getHeader(KafkaConstants.KEY);
        if (innerKey != null) {

            innerKey = kafkaConfiguration.getKey() != null ? kafkaConfiguration.getKey() : innerKey;

            if (innerKey != null) {
                innerKey = tryConvertToSerializedType(innerExchange, innerKey,
                        kafkaConfiguration.getKeySerializer());
            }

            return innerKey;
        }

        return null;
    }

    private Integer getInnerPartitionKey(Message innerMessage) {
        Integer partitionKey = innerMessage.getHeader(KafkaConstants.PARTITION_KEY, Integer.class);

        return kafkaConfiguration.getPartitionKey() != null
                ? kafkaConfiguration.getPartitionKey()
                : partitionKey;
    }

    @Override
    public void remove() {
        msgList.remove();
    }
}
