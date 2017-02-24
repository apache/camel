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
package org.apache.camel.component.kafka.embedded;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import kafka.admin.AdminUtils;
import kafka.admin.RackAwareMode;
import kafka.metrics.KafkaMetricsReporter;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.ZkUtils;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.mutable.Buffer;

import static org.apache.camel.component.kafka.embedded.TestUtils.*;

public class EmbeddedKafkaCluster extends ExternalResource {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final List<Integer> ports;
    private final String zkConnection;
    private final Properties baseProperties;

    private final String brokerList;

    private final List<KafkaServer> kafkaServers;
    private final List<File> logDirs;

    public EmbeddedKafkaCluster(String zkConnection) {
        this(zkConnection, new Properties());
    }

    public EmbeddedKafkaCluster(String zkConnection, Properties baseProperties) {
        this(zkConnection, baseProperties, Collections.singletonList(-1));
    }

    public EmbeddedKafkaCluster(String zkConnection, Properties baseProperties, Integer port) {
        this(zkConnection, baseProperties, Collections.singletonList(port));
    }

    public EmbeddedKafkaCluster(String zkConnection, Properties baseProperties, List<Integer> ports) {
        this.zkConnection = zkConnection;
        this.ports = resolvePorts(ports);
        this.baseProperties = baseProperties;
        this.kafkaServers = new ArrayList<>();
        this.logDirs = new ArrayList<>();

        this.brokerList = constructBrokerList(this.ports);
    }

    public ZkUtils getZkUtils() {
        // gets the first instance
        for (KafkaServer kafkaServer : kafkaServers) {
            return kafkaServer.zkUtils();
        }
        return null;
    }

    public void createTopic(String topic, int partitionCount) {
        log.info("createTopic");
        AdminUtils.createTopic(getZkUtils(), topic, partitionCount, 1, new Properties(), RackAwareMode.Enforced$.MODULE$);
    }

    public void createTopics(String... topics) {
        for (String topic : topics) {
            AdminUtils.createTopic(getZkUtils(), topic, 2, 1, new Properties(), RackAwareMode.Enforced$.MODULE$);
        }
    }

    private List<Integer> resolvePorts(List<Integer> ports) {
        List<Integer> resolvedPorts = new ArrayList<Integer>(ports.size());
        for (Integer port : ports) {
            resolvedPorts.add(resolvePort(port));
        }
        return resolvedPorts;
    }

    private int resolvePort(int port) {
        if (port == -1) {
            return TestUtils.getAvailablePort();
        }
        return port;
    }

    private String constructBrokerList(List<Integer> ports) {
        StringBuilder sb = new StringBuilder();
        for (Integer port : ports) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append("localhost:").append(port);
        }
        return sb.toString();
    }

    public void before() {
        log.info("before");
        for (int i = 0; i < ports.size(); i++) {
            Integer port = ports.get(i);
            File logDir = constructTempDir(perTest("kafka-log"));

            Properties properties = new Properties();
            properties.putAll(baseProperties);
            properties.setProperty("zookeeper.connect", zkConnection);
            properties.setProperty("broker.id", String.valueOf(i + 1));
            properties.setProperty("host.name", "localhost");
            properties.setProperty("port", Integer.toString(port));
            properties.setProperty("log.dir", logDir.getAbsolutePath());
            properties.setProperty("num.partitions", String.valueOf(1));
            properties.setProperty("auto.create.topics.enable", String.valueOf(Boolean.TRUE));
            log.info("log directory: " + logDir.getAbsolutePath());
            properties.setProperty("log.flush.interval.messages", String.valueOf(1));

            KafkaServer broker = startBroker(properties);

            kafkaServers.add(broker);
            logDirs.add(logDir);
        }
    }


    private KafkaServer startBroker(Properties props) {
        List<KafkaMetricsReporter> kmrList = new ArrayList<>();
        Buffer<KafkaMetricsReporter> metricsList = scala.collection.JavaConversions.asScalaBuffer(kmrList);
        KafkaServer server = new KafkaServer(new KafkaConfig(props), new SystemTime(), Option.<String>empty(), metricsList);
        server.startup();
        return server;
    }

    public Properties getProps() {
        Properties props = new Properties();
        props.putAll(baseProperties);
        props.put("metadata.broker.list", brokerList);
        props.put("zookeeper.connect", zkConnection);
        return props;
    }

    public String getBrokerList() {
        return brokerList;
    }

    public List<Integer> getPorts() {
        return ports;
    }

    public String getZkConnection() {
        return zkConnection;
    }

    public void after() {
        for (KafkaServer broker : kafkaServers) {
            try {
                broker.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (File logDir : logDirs) {
            try {
                TestUtils.deleteFile(logDir);
            } catch (FileNotFoundException e) {
                // do nothing
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EmbeddedKafkaCluster{");
        sb.append("brokerList='").append(brokerList).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
