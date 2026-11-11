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
package org.apache.camel.component.opa;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.camel.Producer;
import org.apache.camel.component.opa.security.OpaSecurityPolicyHealthCheck;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OpaHealthProbeLifecycleTest {

    @Test
    void closesProducerAndPolicyProbeClientsIndependently() throws Exception {
        SSLContext ssl = SSLContext.getDefault();
        try (OpaProducerHealthCheck producer = new OpaProducerHealthCheck(
                "http://localhost:1", null, "scope", "endpoint", ssl);
             OpaSecurityPolicyHealthCheck policy = new OpaSecurityPolicyHealthCheck(
                     "http://localhost:1", null, "scope", ssl)) {
            for (HealthCheck check : List.of(producer, policy)) {
                check.setEnabled(true);
                assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);
            }
            HttpClient producerClient = client(producer);
            HttpClient policyClient = client(policy);
            producer.close();

            await().atMost(Duration.ofSeconds(5)).until(producerClient::isTerminated);
            assertThat(policyClient.isTerminated()).isFalse();
            assertThat(producer.call().getError()).isPresent()
                    .get().isInstanceOf(IllegalStateException.class);
            policy.close();
            await().atMost(Duration.ofSeconds(5)).until(policyClient::isTerminated);
        }
    }

    @Test
    void releasesEachNewTlsConfigurationOnClose() throws Exception {
        for (int i = 0; i < 5; i++) {
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, null, null);
            try (OpaProducerHealthCheck check = new OpaProducerHealthCheck(
                    "http://localhost:1", null, "scope", "endpoint", ssl)) {
                check.setEnabled(true);
                check.call();
                HttpClient client = client(check);
                check.close();
                check.close();
                await().atMost(Duration.ofSeconds(5)).until(client::isTerminated);
                assertThat(check.call().getState()).isEqualTo(HealthCheck.State.DOWN);
                assertThat(check.call().getError()).isPresent()
                        .get().isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test
    void producerRestartClosesTheOldClientAndCreatesAnIndependentProbe() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getComponent("opa", OpaComponent.class).setHealthCheckProducerEnabled(true);
            context.getRegistry().bind("probeTls", new SSLContextParameters());
            context.start();
            Producer producer = context.getEndpoint(
                    "opa:authz/allow?serverUrl=http://localhost:1&sslContextParameters=#probeTls").createProducer();
            context.addService(producer, true);
            try {
                WritableHealthCheckRepository repository = HealthCheckHelper.getHealthCheckRepository(
                        context, "producers", WritableHealthCheckRepository.class);
                assertThat(repository).isNotNull();
                repository.setEnabled(true);
                HealthCheck first = repository.stream()
                        .filter(check -> check.getId().startsWith("producer:opa-")).findFirst().orElseThrow();
                first.call();
                HttpClient firstClient = client(first);
                producer.stop();
                await().atMost(Duration.ofSeconds(5)).until(firstClient::isTerminated);
                assertThat(repository.stream().filter(check -> check.getId().startsWith("producer:opa-"))).isEmpty();

                producer.start();
                HealthCheck restarted = repository.stream()
                        .filter(check -> check.getId().startsWith("producer:opa-")).findFirst().orElseThrow();
                assertThat(restarted).isNotSameAs(first);
                restarted.call();
                HttpClient restartedClient = client(restarted);
                assertThat(restartedClient).isNotSameAs(firstClient);
                context.stop();
                await().atMost(Duration.ofSeconds(5)).until(restartedClient::isTerminated);
            } finally {
                producer.stop();
            }
        }
    }

    private static HttpClient client(HealthCheck check) throws Exception {
        Field probe = check.getClass().getDeclaredField("probe");
        probe.setAccessible(true);
        Object owner = probe.get(check);
        Field client = owner.getClass().getDeclaredField("client");
        client.setAccessible(true);
        return (HttpClient) client.get(owner);
    }
}
