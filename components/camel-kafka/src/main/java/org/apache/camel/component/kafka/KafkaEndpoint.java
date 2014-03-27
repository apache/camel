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

import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;

import kafka.message.MessageAndMetadata;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;

/**
 *
 */
public class KafkaEndpoint extends DefaultEndpoint {

    private String brokers;
    private KafkaConfiguration configuration = new KafkaConfiguration();

    public KafkaEndpoint() {
    }

    public KafkaEndpoint(String endpointUri,
                         String remaining,
                         KafkaComponent component) throws URISyntaxException {
        super(endpointUri, component);
        this.brokers = remaining.split("\\?")[0];
    }

    public KafkaConfiguration getConfiguration() {
        if(configuration == null) {
            configuration = createConfiguration();
        }
        return configuration;
    }

    public void setConfiguration(KafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    protected KafkaConfiguration createConfiguration() {
        return new KafkaConfiguration();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        KafkaConsumer consumer = new KafkaConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KafkaProducer(this);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "KafkaTopic[" + configuration.getTopic() + "]", configuration.getConsumerStreams());
    }

    public Exchange createKafkaExchange(MessageAndMetadata<byte[], byte[]> mm) {
        Exchange exchange = new DefaultExchange(getCamelContext(), getExchangePattern());

        Message message = new DefaultMessage();
        message.setHeader(KafkaConstants.PARTITION, mm.partition());
        message.setHeader(KafkaConstants.TOPIC, mm.topic());
        message.setHeader(KafkaConstants.KEY, new String(mm.key()));
        message.setBody(mm.message());
        exchange.setIn(message);

        return exchange;
    }


    // Delegated properties from the configuration
    //-------------------------------------------------------------------------

    public String getZookeeperHost() {
        return configuration.getZookeeperHost();
    }

    public void setZookeeperHost(String zookeeperHost) {
        configuration.setZookeeperHost(zookeeperHost);
    }

    public int getZookeeperPort() {
        return configuration.getZookeeperPort();
    }

    public void setZookeeperPort(int zookeeperPort) {
        configuration.setZookeeperPort(zookeeperPort);
    }

    public String getGroupId() {
        return configuration.getGroupId();
    }

    public void setGroupId(String groupId) {
        configuration.setGroupId(groupId);
    }

    public String getPartitioner() {
        return configuration.getPartitioner();
    }

    public void setPartitioner(String partitioner) {
        configuration.setPartitioner(partitioner);
    }

    public String getTopic() {
        return configuration.getTopic();
    }

    public void setTopic(String topic) {
        configuration.setTopic(topic);
    }

    public String getBrokers() {
        return brokers;
    }

    public int getConsumerStreams() {
        return configuration.getConsumerStreams();
    }

    public void setConsumerStreams(int consumerStreams) {
        configuration.setConsumerStreams(consumerStreams);
    }

}
