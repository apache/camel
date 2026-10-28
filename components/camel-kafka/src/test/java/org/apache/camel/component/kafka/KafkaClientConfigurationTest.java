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
package org.apache.camel.component.kafka;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.camel.component.kafka.security.KafkaAuthType;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The options of {@link KafkaClientConfiguration} must be written the same way to every Kafka client properties.
 */
class KafkaClientConfigurationTest {

    @Test
    void commonClientOptionsAreAppliedToProducerAndConsumer() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setClientId("my-client");
        config.setConnectionMaxIdleMs(1000);
        config.setReceiveBufferBytes(2000);
        config.setSendBufferBytes(3000);
        config.setMetadataMaxAgeMs(4000);
        config.setMetricReporters("org.example.Reporter");
        config.setNoOfMetricsSample(5);
        config.setMetricsSampleWindowMs(6000);
        config.setReconnectBackoffMs(70);
        config.setReconnectBackoffMaxMs(800);
        config.setRetryBackoffMs(90);
        config.setRetryBackoffMaxMs(1000);
        config.setSchemaRegistryURL("http://registry:8081");

        Map<String, Object> expected = new TreeMap<>();
        expected.put(CommonClientConfigs.CLIENT_ID_CONFIG, "my-client");
        expected.put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 1000);
        expected.put(CommonClientConfigs.RECEIVE_BUFFER_CONFIG, 2000);
        expected.put(CommonClientConfigs.SEND_BUFFER_CONFIG, 3000);
        expected.put(CommonClientConfigs.METADATA_MAX_AGE_CONFIG, 4000);
        expected.put(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, "org.example.Reporter");
        expected.put(CommonClientConfigs.METRICS_NUM_SAMPLES_CONFIG, 5);
        expected.put(CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_CONFIG, 6000);
        expected.put(CommonClientConfigs.RECONNECT_BACKOFF_MS_CONFIG, 70);
        expected.put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, 800);
        expected.put(CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG, 90);
        expected.put(CommonClientConfigs.RETRY_BACKOFF_MAX_MS_CONFIG, 1000);
        expected.put("schema.registry.url", "http://registry:8081");

        assertEquals(expected, subset(config.createProducerProperties(), expected));
        assertEquals(expected, subset(config.createConsumerProperties(), expected));
    }

    @Test
    void sslAndSaslOptionsAreAppliedTheSameToProducerAndConsumer() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSecurityProtocol("SASL_SSL");
        config.setSslKeystoreLocation("/keystore.jks");
        config.setSslKeystorePassword("keystore-secret");
        config.setSslKeyPassword("key-secret");
        config.setSslTruststoreLocation("/truststore.jks");
        config.setSslTruststorePassword("truststore-secret");
        config.setSslCipherSuites("TLS_AES_128_GCM_SHA256");
        config.setSslKeystoreType("pkcs12");
        config.setSaslMechanism("PLAIN");
        config.setSaslJaasConfig("org.apache.kafka.common.security.plain.PlainLoginModule required;");

        Map<String, Object> producer = securityProperties(config.createProducerProperties());
        Map<String, Object> consumer = securityProperties(config.createConsumerProperties());

        assertEquals(producer, consumer);
        assertEquals("SASL_SSL", consumer.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("PKCS12", consumer.get(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG));
        assertEquals("/truststore.jks", consumer.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
        assertEquals("PLAIN", consumer.get(SaslConfigs.SASL_MECHANISM));
    }

    @Test
    void sslContextParametersAreAppliedTheSameToProducerAndConsumer() {
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("file:/keystore.jks");
        keyStore.setPassword("keystore-secret");
        KeyManagersParameters keyManagers = new KeyManagersParameters();
        keyManagers.setKeyStore(keyStore);
        keyManagers.setKeyPassword("key-secret");
        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setResource("classpath:/truststore.jks");
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStore);
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(keyManagers);
        sslContextParameters.setTrustManagers(trustManagers);
        sslContextParameters.setSecureSocketProtocol("TLSv1.3");

        KafkaConfiguration config = new KafkaConfiguration();
        config.setSslContextParameters(sslContextParameters);

        Map<String, Object> producer = securityProperties(config.createProducerProperties());
        Map<String, Object> consumer = securityProperties(config.createConsumerProperties());

        assertEquals(producer, consumer);
        assertEquals("SSL", consumer.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("/keystore.jks", consumer.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
        assertEquals("/truststore.jks", consumer.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
        assertEquals("TLSv1.3", consumer.get(SslConfigs.SSL_PROTOCOL_CONFIG));
    }

    @Test
    void saslAuthTypeIsAppliedTheSameToProducerAndConsumer() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSaslAuthType(KafkaAuthType.SCRAM_SHA_512);
        config.setSaslUsername("user");
        config.setSaslPassword("secret");

        Map<String, Object> producer = securityProperties(config.createProducerProperties());
        Map<String, Object> consumer = securityProperties(config.createConsumerProperties());

        assertEquals(producer, consumer);
        assertEquals("SASL_SSL", consumer.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("SCRAM-SHA-512", consumer.get(SaslConfigs.SASL_MECHANISM));
        assertTrue(((String) consumer.get(SaslConfigs.SASL_JAAS_CONFIG)).contains("username=\"user\""));
    }

    @Test
    void additionalPropertiesOverrideCommonOptions() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setClientId("my-client");
        config.getAdditionalProperties().put(CommonClientConfigs.CLIENT_ID_CONFIG, "overridden");
        config.getAdditionalProperties().put("custom.option", "value");

        Properties producer = config.createProducerProperties();
        Properties consumer = config.createConsumerProperties();

        assertEquals("overridden", producer.get(CommonClientConfigs.CLIENT_ID_CONFIG));
        assertEquals("overridden", consumer.get(CommonClientConfigs.CLIENT_ID_CONFIG));
        assertEquals("value", producer.get("custom.option"));
        assertEquals("value", consumer.get("custom.option"));
    }

    @Test
    void copyHasItsOwnAdditionalProperties() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setTopic("my-topic");
        config.setBrokers("localhost:9092");
        config.getAdditionalProperties().put("custom.option", "value");

        KafkaConfiguration copy = config.copy();
        copy.getAdditionalProperties().put("other.option", "value");

        assertNotSame(config.getAdditionalProperties(), copy.getAdditionalProperties());
        assertFalse(config.getAdditionalProperties().containsKey("other.option"));
        assertEquals("my-topic", copy.getTopic());
        assertEquals("localhost:9092", copy.getBrokers());
        assertEquals("value", copy.getAdditionalProperties().get("custom.option"));
    }

    private static Map<String, Object> subset(Properties props, Map<String, Object> keys) {
        Map<String, Object> answer = new TreeMap<>();
        keys.keySet().forEach(key -> answer.put(key, props.get(key)));
        return answer;
    }

    private static Map<String, Object> securityProperties(Properties props) {
        Map<String, Object> answer = new TreeMap<>();
        props.forEach((key, value) -> {
            String name = key.toString();
            if (name.startsWith("ssl.") || name.startsWith("sasl.")
                    || name.equals(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)) {
                answer.put(name, value);
            }
        });
        return answer;
    }
}
