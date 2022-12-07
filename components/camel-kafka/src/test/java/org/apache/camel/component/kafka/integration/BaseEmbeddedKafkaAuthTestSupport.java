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
package org.apache.camel.component.kafka.integration;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tags({ @Tag("non-abstract") })
public abstract class BaseEmbeddedKafkaAuthTestSupport extends AbstractKafkaTestSupport {
    @RegisterExtension
    public static ContainerLocalAuthKafkaService service = new ContainerLocalAuthKafkaService("/kafka-jaas.config");

    protected static AdminClient kafkaAdminClient;

    @BeforeAll
    public static void beforeClass() {
        AbstractKafkaTestSupport.setServiceProperties(service);
    }

    public static AdminClient createAuthAdminClient(KafkaService service) {
        final Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        properties.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        properties.put(SaslConfigs.SASL_JAAS_CONFIG,
                ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("admin", "admin-secret"));

        return AdminClient.create(properties);
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = createAdminClient();
        }
    }

    protected Properties getDefaultProperties() {
        return getDefaultProperties(service);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createCamelContextFromService(service);
    }

    protected static String getBootstrapServers() {
        return service.getBootstrapServers();
    }

    private static AdminClient createAdminClient() {
        return createAuthAdminClient(service);
    }

    protected static Map<String, ConsumerGroupDescription> getConsumerGroupInfo(String groupId)
            throws InterruptedException, ExecutionException, TimeoutException {
        return kafkaAdminClient.describeConsumerGroups(Collections.singletonList(groupId)).all().get(30, TimeUnit.SECONDS);
    }

    protected static void assertGroupIsConnected(String groupId) {
        final Map<String, ConsumerGroupDescription> allGroups = assertDoesNotThrow(() -> getConsumerGroupInfo(groupId));

        Assert.assertTrue("There should be at least one group named" + groupId, allGroups.size() >= 1);

        final ConsumerGroupDescription groupInfo = allGroups.get("KafkaConsumerAuthIT");
        Assert.assertNotNull("There should be at least one group named KafkaConsumerAuthIT", groupInfo);
    }
}
