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

package org.apache.camel.component.kafka.integration.common;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.Assert;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public final class KafkaAdminUtil {

    private KafkaAdminUtil() {

    }

    public static AdminClient createAdminClient(KafkaService service) {
        final Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());

        return KafkaAdminClient.create(properties);
    }

    public static Map<String, ConsumerGroupDescription> getConsumerGroupInfo(String groupId, AdminClient kafkaAdminClient)
            throws InterruptedException, ExecutionException, TimeoutException {
        return kafkaAdminClient.describeConsumerGroups(Collections.singletonList(groupId)).all().get(30, TimeUnit.SECONDS);
    }

    public static void assertGroupIsConnected(String groupId, AdminClient kafkaAdminClient) {
        final Map<String, ConsumerGroupDescription> allGroups
                = assertDoesNotThrow(() -> getConsumerGroupInfo(groupId, kafkaAdminClient));

        Assert.assertTrue("There should be at least one group named" + groupId, allGroups.size() >= 1);

        final ConsumerGroupDescription groupInfo = allGroups.get("KafkaConsumerAuthIT");
        Assert.assertNotNull("There should be at least one group named KafkaConsumerAuthIT", groupInfo);
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
}
