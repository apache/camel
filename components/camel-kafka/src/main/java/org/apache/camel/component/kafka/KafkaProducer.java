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

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

/**
 * @author Stephen Samuel
 */
public class KafkaProducer extends DefaultProducer {

    private final KafkaEndpoint endpoint;
    Producer<String, String> producer;

    public KafkaProducer(KafkaEndpoint endpoint) throws ClassNotFoundException, IllegalAccessException,
            InstantiationException {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        if (producer != null)
            producer.close();
    }

    Properties getProps() {
        Properties props = new Properties();
        props.put("metadata.broker.list", endpoint.getBrokers());
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        props.put("partitioner.class", endpoint.getPartitioner());
        props.put("request.required.acks", "1");
        return props;
    }

    @Override
    protected void doStart() throws Exception {
        Properties props = getProps();
        ProducerConfig config = new ProducerConfig(props);
        producer = new Producer<String, String>(config);
    }

    @Override
    public void process(Exchange exchange) throws CamelException {

        Object partitionKey = exchange.getIn().getHeader(KafkaConstants.PARTITION_KEY);
        if (partitionKey == null)
            throw new CamelException("No partition key set");
        String msg = exchange.getIn().getBody(String.class);

        KeyedMessage<String, String> data =
                new KeyedMessage<String, String>(endpoint.getTopic(), partitionKey.toString(), msg);
        producer.send(data);
    }
}
