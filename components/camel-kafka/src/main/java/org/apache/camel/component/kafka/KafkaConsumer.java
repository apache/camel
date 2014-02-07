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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;

/**
 * @author Stephen Samuel
 */
public class KafkaConsumer extends DefaultConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);

    private final KafkaEndpoint endpoint;
    private final Processor processor;

    ConsumerConnector consumer;
    ExecutorService executor;

    public KafkaConsumer(KafkaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        if (endpoint.getZookeeperHost() == null)
            throw new IllegalArgumentException("zookeeper host must be specified");
        if (endpoint.getZookeeperPort() == 0)
            throw new IllegalArgumentException("zookeeper port must be specified");
        if (endpoint.getGroupId() == null)
            throw new IllegalArgumentException("groupId must not be null");
    }

    Properties getProps() {
        Properties props = new Properties();
        props.put("zookeeper.connect", endpoint.getZookeeperHost() + ":" + endpoint.getZookeeperPort());
        props.put("group.id", endpoint.getGroupId());
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");
        return props;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Starting Kafka consumer");

        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(new ConsumerConfig(getProps()));

        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(endpoint.getTopic(), endpoint.getConsumerStreams());
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(endpoint.getTopic());

        executor = endpoint.createExecutor();
        for (final KafkaStream stream : streams) {
            executor.submit(new ConsumerTask(stream));
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Stopping Kafka consumer");

        if (consumer != null)
            consumer.shutdown();
        if (executor != null)
            executor.shutdown();
        executor = null;
    }

    class ConsumerTask implements Runnable {

        private KafkaStream stream;

        public ConsumerTask(KafkaStream stream) {
            this.stream = stream;
        }

        public void run() {
            ConsumerIterator<byte[], byte[]> it = stream.iterator();
            while ((Boolean) it.hasNext()) {
                MessageAndMetadata<byte[], byte[]> mm = it.next();
                Exchange exchange = endpoint.createKafkaExchange(mm);
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

