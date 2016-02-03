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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.embedded.EmbeddedKafkaCluster;
import org.apache.camel.component.kafka.embedded.EmbeddedZookeeper;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseEmbeddedKafkaTest extends CamelTestSupport {
    static EmbeddedZookeeper embeddedZookeeper;
    static EmbeddedKafkaCluster embeddedKafkaCluster;
    
    private static final Logger LOG = LoggerFactory.getLogger(BaseEmbeddedKafkaTest.class);

    private static volatile int zookeeperPort;
    
    private static volatile int karfkaPort;
   
    @BeforeClass
    public static void beforeClass() {
        // start from somewhere in the 23xxx range
        zookeeperPort = AvailablePortFinder.getNextAvailable(23000);
        // find another ports for proxy route test
        karfkaPort = AvailablePortFinder.getNextAvailable(24000);
        
        embeddedZookeeper = new EmbeddedZookeeper(zookeeperPort);
        List<Integer> kafkaPorts = new ArrayList<Integer>();
        // -1 for any available port
        kafkaPorts.add(karfkaPort);
        embeddedKafkaCluster = new EmbeddedKafkaCluster(embeddedZookeeper.getConnection(), new Properties(), kafkaPorts);
        try {
            embeddedZookeeper.startup();
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("Embedded Zookeeper connection: " + embeddedZookeeper.getConnection());
        embeddedKafkaCluster.startup();
        LOG.info("Embedded Kafka cluster broker list: " + embeddedKafkaCluster.getBrokerList());
    }

    @AfterClass
    public static void afterClass() {
        embeddedKafkaCluster.shutdown();
        embeddedZookeeper.shutdown();
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        Properties prop = new Properties();
        prop.setProperty("zookeeperPort", "" + getZookeeperPort());
        prop.setProperty("karfkaPort", "" + getKarfkaPort());
        jndi.bind("prop", prop);
        return jndi;
    }
    

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("ref:prop"));
        return context;
    }
    

    protected static int getZookeeperPort() {
        return zookeeperPort;
    }
    
    protected static int getKarfkaPort() {
        return karfkaPort;
    }

}
