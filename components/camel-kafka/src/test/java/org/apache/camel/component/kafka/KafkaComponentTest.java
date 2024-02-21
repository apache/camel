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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class KafkaComponentTest {

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private final CamelContext context = contextExtension.getContext();

    @AfterEach
    void clear() {
        context.removeComponent("kafka");
    }

    @Test
    public void testPropertiesSet() {
        String uri = "kafka:mytopic?brokers=broker1:12345,broker2:12566&partitioner=com.class.Party";

        KafkaEndpoint endpoint = context.getEndpoint(uri, KafkaEndpoint.class);
        assertEquals("broker1:12345,broker2:12566", endpoint.getConfiguration().getBrokers());
        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("com.class.Party", endpoint.getConfiguration().getPartitioner());
    }

    @Test
    public void testBrokersOnComponent() {
        KafkaComponent kafka = context.getComponent("kafka", KafkaComponent.class);
        kafka.getConfiguration().setBrokers("broker1:12345,broker2:12566");

        String uri = "kafka:mytopic?partitioner=com.class.Party";

        KafkaEndpoint endpoint = context.getEndpoint(uri, KafkaEndpoint.class);
        assertEquals("broker1:12345,broker2:12566", endpoint.getConfiguration().getBrokers());
        assertEquals("broker1:12345,broker2:12566", endpoint.getComponent().getConfiguration().getBrokers());
        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("com.class.Party", endpoint.getConfiguration().getPartitioner());
    }

    @Test
    public void testCreateAdditionalPropertiesOnEndpointAndComponent() {
        final KafkaComponent kafkaComponent = context.getComponent("kafka", KafkaComponent.class);

        // update with options on component level and restart
        // also we set the configs on the component level
        final KafkaConfiguration kafkaConfiguration = new KafkaConfiguration();
        final Map<String, Object> params = new HashMap<>();
        params.put("extra.1", 789);
        params.put("extra.3", "test.extra.3");
        kafkaConfiguration.setAdditionalProperties(params);
        kafkaComponent.setConfiguration(kafkaConfiguration);
        kafkaComponent.stop();
        kafkaComponent.start();

        final String uri
                = "kafka:mytopic?brokers=broker1:12345,broker2:12566&partitioner=com.class.Party&additionalProperties.extra.1=123&additionalProperties.extra.2=test";

        KafkaEndpoint endpoint = context.getEndpoint(uri, KafkaEndpoint.class);
        assertEquals("broker1:12345,broker2:12566", endpoint.getConfiguration().getBrokers());
        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("com.class.Party", endpoint.getConfiguration().getPartitioner());
        assertEquals("123", endpoint.getConfiguration().getAdditionalProperties().get("extra.1"));
        assertEquals("test", endpoint.getConfiguration().getAdditionalProperties().get("extra.2"));
        assertEquals("test.extra.3", endpoint.getConfiguration().getAdditionalProperties().get("extra.3"));

        // test properties on producer keys
        final Properties producerProperties = endpoint.getConfiguration().createProducerProperties();
        assertEquals("123", producerProperties.getProperty("extra.1"));
        assertEquals("test", producerProperties.getProperty("extra.2"));
        assertEquals("test.extra.3", producerProperties.getProperty("extra.3"));

        // test properties on consumer keys
        final Properties consumerProperties = endpoint.getConfiguration().createConsumerProperties();
        assertEquals("123", consumerProperties.getProperty("extra.1"));
        assertEquals("test", consumerProperties.getProperty("extra.2"));
        assertEquals("test.extra.3", producerProperties.getProperty("extra.3"));
    }

    @Test
    public void testAllProducerConfigProperty() throws Exception {
        Map<String, Object> params = new HashMap<>();
        setProducerProperty(params);

        String uri = "kafka:mytopic?brokers=dev1:12345,dev2:12566";

        KafkaEndpoint endpoint = (KafkaEndpoint) context.getComponent("kafka").createEndpoint(uri, params);

        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("1", endpoint.getConfiguration().getRequestRequiredAcks());
        assertEquals(Integer.valueOf(1), endpoint.getConfiguration().getBufferMemorySize());
        assertEquals(Integer.valueOf(10), endpoint.getConfiguration().getProducerBatchSize());
        assertEquals(Integer.valueOf(12), endpoint.getConfiguration().getConnectionMaxIdleMs());
        assertEquals(Integer.valueOf(1), endpoint.getConfiguration().getMaxBlockMs());
        assertEquals(Integer.valueOf(1), endpoint.getConfiguration().getBufferMemorySize());
        assertEquals("testing", endpoint.getConfiguration().getClientId());
        assertEquals("none", endpoint.getConfiguration().getCompressionCodec());
        assertEquals(Integer.valueOf(1), endpoint.getConfiguration().getLingerMs());
        assertEquals(Integer.valueOf(100), endpoint.getConfiguration().getMaxRequestSize());
        assertEquals(100, endpoint.getConfiguration().getRequestTimeoutMs().intValue());
        assertEquals(200, endpoint.getConfiguration().getDeliveryTimeoutMs().intValue());
        assertEquals(Integer.valueOf(1029), endpoint.getConfiguration().getMetadataMaxAgeMs());
        assertEquals(Integer.valueOf(23), endpoint.getConfiguration().getReceiveBufferBytes());
        assertEquals(Integer.valueOf(234), endpoint.getConfiguration().getReconnectBackoffMs());
        assertEquals(Integer.valueOf(234), endpoint.getConfiguration().getReconnectBackoffMaxMs());
        assertEquals(Integer.valueOf(0), endpoint.getConfiguration().getRetries());
        assertEquals(3782, endpoint.getConfiguration().getRetryBackoffMs().intValue());
        assertEquals(765, endpoint.getConfiguration().getSendBufferBytes().intValue());
        assertEquals(Integer.valueOf(1), endpoint.getConfiguration().getMaxInFlightRequest());
        assertEquals("org.apache.camel.reporters.TestReport,org.apache.camel.reporters.SampleReport",
                endpoint.getConfiguration().getMetricReporters());
        assertEquals(Integer.valueOf(3), endpoint.getConfiguration().getNoOfMetricsSample());
        assertEquals(Integer.valueOf(12344), endpoint.getConfiguration().getMetricsSampleWindowMs());
        assertEquals(KafkaConstants.KAFKA_DEFAULT_SERIALIZER, endpoint.getConfiguration().getValueSerializer());
        assertEquals(KafkaConstants.KAFKA_DEFAULT_SERIALIZER, endpoint.getConfiguration().getKeySerializer());
        assertEquals("testing", endpoint.getConfiguration().getSslKeyPassword());
        assertEquals("/abc", endpoint.getConfiguration().getSslKeystoreLocation());
        assertEquals("testing", endpoint.getConfiguration().getSslKeystorePassword());
        assertEquals("/abc", endpoint.getConfiguration().getSslTruststoreLocation());
        assertEquals("testing", endpoint.getConfiguration().getSslTruststorePassword());
        assertEquals("test", endpoint.getConfiguration().getSaslKerberosServiceName());
        assertEquals("PLAINTEXT", endpoint.getConfiguration().getSecurityProtocol());
        assertEquals("TLSv1.3", endpoint.getConfiguration().getSslEnabledProtocols());
        assertEquals("JKS", endpoint.getConfiguration().getSslKeystoreType());
        assertEquals("TLS", endpoint.getConfiguration().getSslProtocol());
        assertEquals("test", endpoint.getConfiguration().getSslProvider());
        assertEquals("JKS", endpoint.getConfiguration().getSslTruststoreType());
        assertEquals("/usr/bin/kinit", endpoint.getConfiguration().getKerberosInitCmd());
        assertEquals(Integer.valueOf(60000), endpoint.getConfiguration().getKerberosBeforeReloginMinTime());
        assertEquals(Double.valueOf(0.05), endpoint.getConfiguration().getKerberosRenewJitter());
        assertEquals(Double.valueOf(0.8), endpoint.getConfiguration().getKerberosRenewWindowFactor());
        assertEquals("MAC", endpoint.getConfiguration().getSslCipherSuites());
        assertEquals("test", endpoint.getConfiguration().getSslEndpointAlgorithm());
        assertEquals("SunX509", endpoint.getConfiguration().getSslKeymanagerAlgorithm());
        assertEquals("PKIX", endpoint.getConfiguration().getSslTrustmanagerAlgorithm());
    }

    @Test
    public void testAllProducerKeysPlainText() throws Exception {
        Map<String, Object> params = new HashMap<>();

        String uri = "kafka:mytopic?brokers=dev1:12345,dev2:12566";

        KafkaEndpoint endpoint = (KafkaEndpoint) context.getComponent("kafka").createEndpoint(uri, params);
        assertEquals(endpoint.getConfiguration().createProducerProperties().keySet(), getProducerKeys().keySet());
    }

    private Properties getProducerKeys() {
        Properties props = new Properties();

        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        props.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, "540000");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "60000");
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "1048576");
        props.put(ProducerConfig.RECEIVE_BUFFER_CONFIG, "32768");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000");
        props.put(ProducerConfig.SEND_BUFFER_CONFIG, "131072");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        props.put(ProducerConfig.METADATA_MAX_AGE_CONFIG, "300000");
        props.put(ProducerConfig.METRICS_NUM_SAMPLES_CONFIG, "2");
        props.put(ProducerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG, "30000");
        props.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, "50");
        props.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, "1000");
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "100");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false");
        props.put(ProducerConfig.PARTITIONER_IGNORE_KEYS_CONFIG, "false");
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");

        return props;
    }

    private Properties getProducerKeysSASL() {
        Properties props = getProducerKeys();

        props.put(SaslConfigs.SASL_KERBEROS_KINIT_CMD, "/usr/bin/kinit");
        props.put(SaslConfigs.SASL_KERBEROS_MIN_TIME_BEFORE_RELOGIN, "60000");
        props.put(SaslConfigs.SASL_KERBEROS_TICKET_RENEW_JITTER, "0.05");
        props.put(SaslConfigs.SASL_KERBEROS_TICKET_RENEW_WINDOW_FACTOR, "0.8");
        props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");

        return props;
    }

    private Properties getProducerKeysSSL() {
        Properties props = getProducerKeys();

        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        props.put(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.3,TLSv1.2,TLSv1.1,TLSv1");
        props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "JKS");
        props.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLS");
        props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS");
        props.put(SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, "SunX509");
        props.put(SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, "PKIX");
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");

        return props;
    }

    @Test
    public void testAllProducerKeysPlainTextSsl() throws Exception {
        Map<String, Object> params = new HashMap<>();

        String uri = "kafka:mytopic?brokers=dev1:12345,dev2:12566&securityProtocol=SSL";

        KafkaEndpoint endpoint = (KafkaEndpoint) context.getComponent("kafka").createEndpoint(uri, params);
        assertEquals(endpoint.getConfiguration().createProducerProperties().keySet(), getProducerKeysSSL().keySet());
    }

    @Test
    public void testAllProducerKeysPlainTextSasl() throws Exception {
        Map<String, Object> params = new HashMap<>();

        String uri = "kafka:mytopic?brokers=dev1:12345,dev2:12566&securityProtocol=SASL_PLAINTEXT";

        KafkaEndpoint endpoint = (KafkaEndpoint) context.getComponent("kafka").createEndpoint(uri, params);
        assertEquals(endpoint.getConfiguration().createProducerProperties().keySet(), getProducerKeysSASL().keySet());
    }

    private void setProducerProperty(Map<String, Object> params) {
        params.put("requestRequiredAcks", "1");
        params.put("bufferMemorySize", 1);
        params.put("compressionCodec", "none");
        params.put("retries", 0);
        params.put("producerBatchSize", 10);
        params.put("connectionMaxIdleMs", 12);
        params.put("lingerMs", 1);
        params.put("maxBlockMs", 1);
        params.put("maxRequestSize", 100);
        params.put("receiveBufferBytes", 23);
        params.put("requestTimeoutMs", 100);
        params.put("deliveryTimeoutMs", 200);
        params.put("sendBufferBytes", 765);
        params.put("maxInFlightRequest", 1);
        params.put("metadataMaxAgeMs", 1029);
        params.put("reconnectBackoffMs", 234);
        params.put("reconnectBackoffMaxMs", 234);
        params.put("retryBackoffMs", 3782);
        params.put("noOfMetricsSample", 3);
        params.put("metricReporters", "org.apache.camel.reporters.TestReport,org.apache.camel.reporters.SampleReport");
        params.put("metricsSampleWindowMs", 12344);
        params.put("clientId", "testing");
        params.put("sslKeyPassword", "testing");
        params.put("sslKeystoreLocation", "/abc");
        params.put("sslKeystorePassword", "testing");
        params.put("sslTruststoreLocation", "/abc");
        params.put("sslTruststorePassword", "testing");
        params.put("saslKerberosServiceName", "test");
        params.put("saslMechanism", "PLAIN");
        params.put("securityProtocol", "PLAINTEXT");
        params.put("sslEnabledProtocols", "TLSv1.3");
        params.put("sslKeystoreType", "JKS");
        params.put("sslProtocol", "TLS");
        params.put("sslProvider", "test");
        params.put("sslTruststoreType", "JKS");
        params.put("kerberosInitCmd", "/usr/bin/kinit");
        params.put("kerberosBeforeReloginMinTime", 60000);
        params.put("kerberosRenewJitter", 0.05);
        params.put("kerberosRenewWindowFactor", 0.8);
        params.put("sslCipherSuites", "MAC");
        params.put("sslEndpointAlgorithm", "test");
        params.put("sslKeymanagerAlgorithm", "SunX509");
        params.put("sslTrustmanagerAlgorithm", "PKIX");
    }

    @Test
    public void testCreateProducerConfigTruststorePassword() {
        KeyStoreParameters keyStoreParameters = new KeyStoreParameters();
        keyStoreParameters.setPassword("my-password");

        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setKeyStore(keyStoreParameters);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(trustManagersParameters);

        KafkaConfiguration kcfg = new KafkaConfiguration();
        kcfg.setSslContextParameters(sslContextParameters);

        Properties props = kcfg.createProducerProperties();

        assertEquals("my-password", props.getProperty("ssl.truststore.password"));
        assertNull(props.getProperty("ssl.keystore.password"));
    }

    @Test
    public void testCreateConsumerConfigTruststorePassword() {
        KeyStoreParameters keyStoreParameters = new KeyStoreParameters();
        keyStoreParameters.setPassword("my-password");

        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setKeyStore(keyStoreParameters);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(trustManagersParameters);

        KafkaConfiguration kcfg = new KafkaConfiguration();
        kcfg.setSslContextParameters(sslContextParameters);

        Properties props = kcfg.createConsumerProperties();

        assertEquals("my-password", props.getProperty("ssl.truststore.password"));
        assertNull(props.getProperty("ssl.keystore.password"));
    }

    @Test
    public void testCreateAdditionalPropertiesResolvePlaceholders() {
        context.getPropertiesComponent().addOverrideProperty("foo", "123");
        context.getPropertiesComponent().addOverrideProperty("bar", "test");

        final String uri
                = "kafka:mytopic?brokers=broker1:12345,broker2:12566&partitioner=com.class.Party&additionalProperties.extra.1={{foo}}&additionalProperties.extra.2={{bar}}";

        KafkaEndpoint endpoint = context.getEndpoint(uri, KafkaEndpoint.class);
        assertEquals("broker1:12345,broker2:12566", endpoint.getConfiguration().getBrokers());
        assertEquals("mytopic", endpoint.getConfiguration().getTopic());
        assertEquals("com.class.Party", endpoint.getConfiguration().getPartitioner());
        assertEquals("123", endpoint.getConfiguration().getAdditionalProperties().get("extra.1"));
        assertEquals("test", endpoint.getConfiguration().getAdditionalProperties().get("extra.2"));

        // test properties on producer keys
        final Properties producerProperties = endpoint.getConfiguration().createProducerProperties();
        assertEquals("123", producerProperties.getProperty("extra.1"));
        assertEquals("test", producerProperties.getProperty("extra.2"));

        // test properties on consumer keys
        final Properties consumerProperties = endpoint.getConfiguration().createConsumerProperties();
        assertEquals("123", consumerProperties.getProperty("extra.1"));
        assertEquals("test", consumerProperties.getProperty("extra.2"));
    }

}
