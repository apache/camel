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

import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.embedded.EmbeddedKafkaBroker;
import org.apache.camel.component.kafka.embedded.EmbeddedZookeeper;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BaseEmbeddedKafkaTest extends CamelTestSupport {

    @ClassRule
    public static EmbeddedZookeeper zookeeper = new EmbeddedZookeeper(
            AvailablePortFinder.getNextAvailable(23000));

    @ClassRule
    public static EmbeddedKafkaBroker kafkaBroker =
            new EmbeddedKafkaBroker(0,
                    AvailablePortFinder.getNextAvailable(24000),
                    zookeeper.getConnection(),
                    new Properties());

    private static final Logger LOG = LoggerFactory.getLogger(BaseEmbeddedKafkaTest.class);

    @BeforeClass
    public static void beforeClass() {
        LOG.info("### Embedded Zookeeper connection: " + zookeeper.getConnection());
        LOG.info("### Embedded Kafka cluster broker list: " + kafkaBroker.getBrokerList());
    }

    protected Properties getDefaultProperties() {
        Properties props = new Properties();
        LOG.info("Connecting to Kafka port {}", kafkaBroker.getPort());
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker.getBrokerList());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_PARTITIONER);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return props;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        Properties prop = new Properties();
        prop.setProperty("zookeeperPort", "" + getZookeeperPort());
        prop.setProperty("kafkaPort", "" + getKafkaPort());
        jndi.bind("prop", prop);
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("ref:prop"));

        KafkaComponent kafka = new KafkaComponent(context);
        kafka.setBrokers("localhost:" + getKafkaPort());
        context.addComponent("kafka", kafka);

        return context;
    }

    protected static int getZookeeperPort() {
        return zookeeper.getPort();
    }

    protected static int getKafkaPort() {
        return kafkaBroker.getPort();
    }

}
