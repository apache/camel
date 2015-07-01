/**
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
package org.apache.camel.component.kafka;

import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

/**
 *
 */
public class KafkaProducer<K, V> extends DefaultProducer {

    protected Producer<K, V> producer;
    private final KafkaEndpoint endpoint;

    public KafkaProducer(KafkaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        if (producer != null) {
            producer.close();
        }
    }

    Properties getProps() {
        Properties props = endpoint.getConfiguration().createProducerProperties();
        if (endpoint.getBrokers() != null) {
            props.put("metadata.broker.list", endpoint.getBrokers());
        }
        return props;
    }

    @Override
    protected void doStart() throws Exception {
        Properties props = getProps();
        ProducerConfig config = new ProducerConfig(props);
        producer = new Producer<K, V>(config);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws CamelException {
        String topic = endpoint.getTopic();
        if (!endpoint.isBridgeEndpoint()) {
            topic = exchange.getIn().getHeader(KafkaConstants.TOPIC, topic, String.class);
        }
        if (topic == null) {
            throw new CamelExchangeException("No topic key set", exchange);
        }
        K partitionKey = (K) exchange.getIn().getHeader(KafkaConstants.PARTITION_KEY);
        boolean hasPartitionKey = partitionKey != null;

        K messageKey = (K) exchange.getIn().getHeader(KafkaConstants.KEY);
        boolean hasMessageKey = messageKey != null;

        V msg = (V) exchange.getIn().getBody();
        KeyedMessage<K, V> data;

        if (hasPartitionKey && hasMessageKey) {
            data = new KeyedMessage<K, V>(topic, messageKey, partitionKey, msg);
        } else if (hasPartitionKey) {
            data = new KeyedMessage<K, V>(topic, partitionKey, msg);
        } else if (hasMessageKey) {
            data = new KeyedMessage<K, V>(topic, messageKey, msg);
        } else {
            log.warn("No message key or partition key set");
            data = new KeyedMessage<K, V>(topic, messageKey, partitionKey, msg);
        }
        producer.send(data);
    }

}
