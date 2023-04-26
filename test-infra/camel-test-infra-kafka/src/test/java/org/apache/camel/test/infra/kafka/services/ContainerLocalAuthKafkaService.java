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

package org.apache.camel.test.infra.kafka.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class ContainerLocalAuthKafkaService implements KafkaService, ContainerService<KafkaContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ContainerLocalAuthKafkaService.class);
    private final KafkaContainer kafka;

    public static class TransientAuthenticatedKafkaContainer extends KafkaContainer {
        public TransientAuthenticatedKafkaContainer(String jaasConfigFile) {
            super(DockerImageName.parse(ContainerLocalKafkaService.KAFKA3_IMAGE_NAME));

            withEmbeddedZookeeper();

            final MountableFile mountableFile = MountableFile.forClasspathResource(jaasConfigFile);
            LOG.debug("Using mountable file at: {}", mountableFile.getFilesystemPath());
            withCopyFileToContainer(mountableFile, "/tmp/kafka-jaas.config");

            withEnv("KAFKA_OPTS", "-Djava.security.auth.login.config=/tmp/kafka-jaas.config")
                    .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9093,BROKER://0.0.0.0:9092")
                    .withEnv("KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL", "PLAIN")
                    .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:SASL_PLAINTEXT,BROKER:PLAINTEXT")
                    .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "PLAIN");
        }
    }

    public static class StaticKafkaContainer extends TransientAuthenticatedKafkaContainer {
        public StaticKafkaContainer(String jaasConfigFile) {
            super(jaasConfigFile);

            addFixedExposedPort(9093, 9093);
        }

        @Override
        public String getBootstrapServers() {
            return String.format("PLAINTEXT://%s:9093", this.getHost());
        }
    }

    public ContainerLocalAuthKafkaService(String jaasConfigFile) {
        kafka = initContainer(jaasConfigFile);
    }

    public ContainerLocalAuthKafkaService(KafkaContainer kafka) {
        this.kafka = kafka;
    }

    protected KafkaContainer initContainer(String jaasConfigFile) {
        return new TransientAuthenticatedKafkaContainer(jaasConfigFile);
    }

    public String getBootstrapServers() {
        return kafka.getBootstrapServers();
    }

    @Override
    public void registerProperties() {
        System.setProperty(KafkaProperties.KAFKA_BOOTSTRAP_SERVERS, getBootstrapServers());
    }

    @Override
    public void initialize() {
        kafka.start();
        registerProperties();

        LOG.info("Kafka bootstrap server running at address {}", kafka.getBootstrapServers());
    }

    @Override
    public void shutdown() {
        kafka.stop();
    }

    @Override
    public KafkaContainer getContainer() {
        return kafka;
    }

    /**
     * This method can be used by tests to get a sample 'sasl.jaas.config' configuration for the given user and password
     *
     * @param  username the user to create the config for
     * @param  password the password for the user
     * @return          A string with the configuration
     */
    public static String generateSimpleSaslJaasConfig(String username, String password) {
        return String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username='%s' password='%s';",
                username, password);
    }
}
