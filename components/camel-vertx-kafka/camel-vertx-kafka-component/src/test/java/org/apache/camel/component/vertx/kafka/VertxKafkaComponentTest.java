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
package org.apache.camel.component.vertx.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxKafkaComponentTest extends CamelTestSupport {

    @Test
    void testPropertiesSet() throws Exception {
        String uri = "vertx-kafka:mytopic?bootstrapServers=broker1:12345,broker2:12566&partitionerClass=com.class.Party";

        VertxKafkaEndpoint endpoint = context.getEndpoint(uri, VertxKafkaEndpoint.class);
        assertEquals("broker1:12345,broker2:12566", endpoint.getConfiguration().getBootstrapServers());
        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("com.class.Party", endpoint.getConfiguration().getPartitionerClass());
    }

    @Test
    void testBrokersOnComponent() throws Exception {
        VertxKafkaComponent kafka = context.getComponent("vertx-kafka", VertxKafkaComponent.class);
        kafka.getConfiguration().setBootstrapServers("broker1:12345,broker2:12566");

        String uri = "vertx-kafka:mytopic?partitionerClass=com.class.Party";

        VertxKafkaEndpoint endpoint = context.getEndpoint(uri, VertxKafkaEndpoint.class);
        assertEquals("broker1:12345,broker2:12566", endpoint.getConfiguration().getBootstrapServers());
        assertEquals("broker1:12345,broker2:12566", endpoint.getComponent().getConfiguration().getBootstrapServers());
        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("com.class.Party", endpoint.getConfiguration().getPartitionerClass());
    }

    @Test
    public void testCreateAdditionalPropertiesOnEndpointAndComponent() {
        final VertxKafkaComponent kafkaComponent = context.getComponent("vertx-kafka", VertxKafkaComponent.class);

        // also we set the configs on the component level
        final VertxKafkaConfiguration kafkaConfiguration = new VertxKafkaConfiguration();
        final Map<String, Object> params = new HashMap<>();

        params.put("extra.1", 789);
        params.put("extra.3", "test.extra.3");
        kafkaConfiguration.setAdditionalProperties(params);
        kafkaComponent.setConfiguration(kafkaConfiguration);

        final String uri
                = "vertx-kafka:mytopic?bootstrapServers=broker1:12345,broker2:12566&partitionerClass=com.class.Party&additionalProperties.extra.1=123&additionalProperties.extra.2=test";

        VertxKafkaEndpoint endpoint = context.getEndpoint(uri, VertxKafkaEndpoint.class);
        assertEquals("broker1:12345,broker2:12566", endpoint.getConfiguration().getBootstrapServers());
        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("com.class.Party", endpoint.getConfiguration().getPartitionerClass());
        assertEquals("123", endpoint.getConfiguration().getAdditionalProperties().get("extra.1"));
        assertEquals("test", endpoint.getConfiguration().getAdditionalProperties().get("extra.2"));
        assertEquals("test.extra.3", endpoint.getConfiguration().getAdditionalProperties().get("extra.3"));

        // test properties on producer keys
        final Properties producerProperties = endpoint.getConfiguration().createProducerConfiguration();
        assertEquals("123", producerProperties.getProperty("extra.1"));
        assertEquals("test", producerProperties.getProperty("extra.2"));
        assertEquals("test.extra.3", producerProperties.getProperty("extra.3"));

        // test properties on consumer keys
        final Properties consumerProperties = endpoint.getConfiguration().createConsumerConfiguration();
        assertEquals("123", consumerProperties.getProperty("extra.1"));
        assertEquals("test", consumerProperties.getProperty("extra.2"));
        assertEquals("test.extra.3", producerProperties.getProperty("extra.3"));
    }

    @Test
    public void testAllProducerConfigProperty() throws Exception {
        Map<String, Object> params = new HashMap<>();
        setProducerProperty(params);

        String uri = "vertx-kafka:mytopic?bootstrapServers=dev1:12345,dev2:12566";

        VertxKafkaEndpoint endpoint = (VertxKafkaEndpoint) context.getComponent("vertx-kafka").createEndpoint(uri, params);

        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("1", endpoint.getConfiguration().getAcks());
        assertEquals(1, endpoint.getConfiguration().getBufferMemory());
        assertEquals(10, endpoint.getConfiguration().getBatchSize());
        assertEquals(12, endpoint.getConfiguration().getConnectionsMaxIdleMs());
        assertEquals(1, endpoint.getConfiguration().getMaxBlockMs());
        assertEquals("testing", endpoint.getConfiguration().getClientId());
        assertEquals("none", endpoint.getConfiguration().getCompressionType());
        assertEquals(1, endpoint.getConfiguration().getLingerMs());
        assertEquals(100, endpoint.getConfiguration().getMaxRequestSize());
        assertEquals(100, endpoint.getConfiguration().getRequestTimeoutMs());
        assertEquals(1029, endpoint.getConfiguration().getMetadataMaxAgeMs());
        assertEquals(23, endpoint.getConfiguration().getReceiveBufferBytes());
        assertEquals(234, endpoint.getConfiguration().getReconnectBackoffMs());
        assertEquals(234, endpoint.getConfiguration().getReconnectBackoffMaxMs());
        assertEquals(0, endpoint.getConfiguration().getRetries());
        assertEquals(3782, endpoint.getConfiguration().getRetryBackoffMs());
        assertEquals(765, endpoint.getConfiguration().getSendBufferBytes());
        assertEquals(1, endpoint.getConfiguration().getMaxInFlightRequestsPerConnection());
        assertEquals("org.apache.camel.reporters.TestReport,org.apache.camel.reporters.SampleReport",
                endpoint.getConfiguration().getMetricReporters());
        assertEquals(3, endpoint.getConfiguration().getMetricsNumSamples());
        assertEquals(12344, endpoint.getConfiguration().getMetricsSampleWindowMs());
        assertEquals("org.apache.kafka.common.serialization.StringSerializer",
                endpoint.getConfiguration().getValueSerializer());
        assertEquals("org.apache.kafka.common.serialization.StringSerializer", endpoint.getConfiguration().getKeySerializer());
        assertEquals("testing", endpoint.getConfiguration().getSslKeyPassword());
        assertEquals("/abc", endpoint.getConfiguration().getSslKeystoreLocation());
        assertEquals("testing", endpoint.getConfiguration().getSslKeystorePassword());
        assertEquals("/abc", endpoint.getConfiguration().getSslTruststoreLocation());
        assertEquals("testing", endpoint.getConfiguration().getSslTruststorePassword());
        assertEquals("test", endpoint.getConfiguration().getSaslKerberosServiceName());
        assertEquals("PLAINTEXT", endpoint.getConfiguration().getSecurityProtocol());
        assertEquals("TLSv1.2", endpoint.getConfiguration().getSslEnabledProtocols());
        assertEquals("JKS", endpoint.getConfiguration().getSslKeystoreType());
        assertEquals("TLS", endpoint.getConfiguration().getSslProtocol());
        assertEquals("test", endpoint.getConfiguration().getSslProvider());
        assertEquals("JKS", endpoint.getConfiguration().getSslTruststoreType());
        assertEquals("/usr/bin/kinit", endpoint.getConfiguration().getSaslKerberosKinitCmd());
        assertEquals(60000, endpoint.getConfiguration().getSaslKerberosMinTimeBeforeRelogin());
        assertEquals(0.05, endpoint.getConfiguration().getSaslKerberosTicketRenewJitter());
        assertEquals(0.8, endpoint.getConfiguration().getSaslKerberosTicketRenewWindowFactor());
        assertEquals("MAC", endpoint.getConfiguration().getSslCipherSuites());
        assertEquals("test", endpoint.getConfiguration().getSslEndpointIdentificationAlgorithm());
        assertEquals("SunX509", endpoint.getConfiguration().getSslKeymanagerAlgorithm());
        assertEquals("PKIX", endpoint.getConfiguration().getSslTrustmanagerAlgorithm());
    }

    @Test
    public void testAllProducerKeys() throws Exception {
        Map<String, Object> params = new HashMap<>();

        String uri = "vertx-kafka:mytopic?bootstrapServers=dev1:12345,dev2:12566";

        VertxKafkaEndpoint endpoint = (VertxKafkaEndpoint) context.getComponent("vertx-kafka").createEndpoint(uri, params);

        assertTrue(endpoint.getConfiguration().createProducerConfiguration().keySet().containsAll(getProducerKeys().keySet()));
    }

    private Properties getProducerKeys() {
        Properties props = new Properties();

        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
        props.put(ProducerConfig.RETRIES_CONFIG, "0");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        props.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, "540000");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "60000");
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "1048576");
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, new VertxKafkaConfiguration().getPartitionerClass());
        props.put(ProducerConfig.RECEIVE_BUFFER_CONFIG, "32768");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");
        props.put(ProducerConfig.SEND_BUFFER_CONFIG, "131072");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        props.put(ProducerConfig.METADATA_MAX_AGE_CONFIG, "300000");
        props.put(ProducerConfig.METRICS_NUM_SAMPLES_CONFIG, "2");
        props.put(ProducerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG, "30000");
        props.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, "50");
        props.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, "1000");
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "100");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, new VertxKafkaConfiguration().getValueSerializer());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, new VertxKafkaConfiguration().getValueSerializer());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false");
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
        props.put(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.2, TLSv1.1, TLSv1");
        props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "JKS");
        props.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLS");
        props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS");
        props.put(SaslConfigs.SASL_KERBEROS_KINIT_CMD, "/usr/bin/kinit");
        props.put(SaslConfigs.SASL_KERBEROS_MIN_TIME_BEFORE_RELOGIN, "60000");
        props.put(SaslConfigs.SASL_KERBEROS_TICKET_RENEW_JITTER, "0.05");
        props.put(SaslConfigs.SASL_KERBEROS_TICKET_RENEW_WINDOW_FACTOR, "0.8");
        props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        props.put(SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, "SunX509");
        props.put(SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, "PKIX");
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");

        return props;
    }

    private void setProducerProperty(Map<String, Object> params) {
        params.put("acks", "1");
        params.put("bufferMemory", 1L);
        params.put("compressionType", "none");
        params.put("retries", 0);
        params.put("batchSize", 10);
        params.put("connectionsMaxIdleMs", 12L);
        params.put("lingerMs", 1L);
        params.put("maxBlockMs", 1L);
        params.put("maxRequestSize", 100);
        params.put("receiveBufferBytes", 23);
        params.put("requestTimeoutMs", 100);
        params.put("sendBufferBytes", 765);
        params.put("maxInFlightRequestsPerConnection", 1);
        params.put("metadataMaxAgeMs", 1029L);
        params.put("reconnectBackoffMs", 234L);
        params.put("reconnectBackoffMaxMs", 234L);
        params.put("retryBackoffMs", 3782L);
        params.put("metricsNumSamples", 3);
        params.put("metricReporters", "org.apache.camel.reporters.TestReport,org.apache.camel.reporters.SampleReport");
        params.put("metricsSampleWindowMs", 12344L);
        params.put("clientId", "testing");
        params.put("sslKeyPassword", "testing");
        params.put("sslKeystoreLocation", "/abc");
        params.put("sslKeystorePassword", "testing");
        params.put("sslTruststoreLocation", "/abc");
        params.put("sslTruststorePassword", "testing");
        params.put("saslKerberosServiceName", "test");
        params.put("saslMechanism", "PLAIN");
        params.put("securityProtocol", "PLAINTEXT");
        params.put("sslEnabledProtocols", "TLSv1.2");
        params.put("sslKeystoreType", "JKS");
        params.put("sslProtocol", "TLS");
        params.put("sslProvider", "test");
        params.put("sslTruststoreType", "JKS");
        params.put("saslKerberosKinitCmd", "/usr/bin/kinit");
        params.put("saslKerberosMinTimeBeforeRelogin", 60000L);
        params.put("saslKerberosTicketRenewJitter", 0.05);
        params.put("saslKerberosTicketRenewWindowFactor", 0.8);
        params.put("sslCipherSuites", "MAC");
        params.put("sslEndpointIdentificationAlgorithm", "test");
        params.put("sslKeymanagerAlgorithm", "SunX509");
        params.put("sslTrustmanagerAlgorithm", "PKIX");
    }
}
