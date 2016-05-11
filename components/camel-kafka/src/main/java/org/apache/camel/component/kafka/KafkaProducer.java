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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class KafkaProducer extends DefaultProducer implements AsyncProcessor {

    private org.apache.kafka.clients.producer.KafkaProducer kafkaProducer;
    private final KafkaEndpoint endpoint;

    public KafkaProducer(KafkaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    Properties getProps() {
        Properties props = endpoint.getConfiguration().createProducerProperties();
        if (endpoint.getBrokers() != null) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, endpoint.getBrokers());
        }
        return props;
    }

    public org.apache.kafka.clients.producer.KafkaProducer getKafkaProducer() {
        return kafkaProducer;
    }

    /**
     * To use a custom {@link org.apache.kafka.clients.producer.KafkaProducer} instance.
     */
    public void setKafkaProducer(org.apache.kafka.clients.producer.KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    protected void doStop() throws Exception {
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
    }

    @Override
    protected void doStart() throws Exception {
        Properties props = getProps();
        if (kafkaProducer == null) {
            kafkaProducer = new org.apache.kafka.clients.producer.KafkaProducer(props);
        }
    }

    protected ProducerRecord createRecorder(Exchange exchange) throws CamelException {
        String topic = endpoint.getTopic();
        if (!endpoint.isBridgeEndpoint()) {
            topic = exchange.getIn().getHeader(KafkaConstants.TOPIC, topic, String.class);
        }
        if (topic == null) {
            throw new CamelExchangeException("No topic key set", exchange);
        }
        Object partitionKey = exchange.getIn().getHeader(KafkaConstants.PARTITION_KEY);
        boolean hasPartitionKey = partitionKey != null;

        Object messageKey = exchange.getIn().getHeader(KafkaConstants.KEY);
        boolean hasMessageKey = messageKey != null;

        Object msg = exchange.getIn().getBody();

        ProducerRecord record;
        if (hasPartitionKey && hasMessageKey) {
            record = new ProducerRecord(topic, new Integer(partitionKey.toString()), messageKey, msg);
        } else if (hasMessageKey) {
            record = new ProducerRecord(topic, messageKey, msg);
        } else {
            log.warn("No message key or partition key set");
            record = new ProducerRecord(topic, msg);
        }
        return record;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws CamelException {

        ProducerRecord record = createRecorder(exchange);
        // Just send out the record in the sync way
        try {
            kafkaProducer.send(record).get();
        } catch (Exception e) {
            throw new CamelException(e);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            ProducerRecord record = createRecorder(exchange);
            kafkaProducer.send(record, new KafkaProducerCallBack(exchange, callback));
            // Finishing the processing in an async way
            return false;
        } catch (Exception ex) {
            // Just set the exception back to the client
            exchange.setException(ex);
            callback.done(true);
            return true;
        }
    }

    class KafkaProducerCallBack implements Callback {

        private Exchange exchange;
        private AsyncCallback callback;

        KafkaProducerCallBack(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
            if (e != null) {
                // Just set the exception back
                exchange.setException(e);
            }
            callback.done(false);
        }
    }
}
