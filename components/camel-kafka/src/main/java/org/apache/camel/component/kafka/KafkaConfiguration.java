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

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.CipherSuitesParameters;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.SecureSocketProtocolsParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;

@UriParams
public class KafkaConfiguration implements Cloneable {

    //Common configuration properties
    @UriPath(label = "common") @Metadata(required = "true")
    private String topic;
    @UriParam(label = "common")
    private String brokers;
    @UriParam(label = "common")
    private String clientId;

    @UriParam(label = "consumer")
    private boolean topicIsPattern;
    @UriParam(label = "consumer")
    private String groupId;
    @UriParam(label = "consumer", defaultValue = "10")
    private int consumerStreams = 10;
    @UriParam(label = "consumer", defaultValue = "1")
    private int consumersCount = 1;

    //interceptor.classes
    @UriParam(label = "common,monitoring")
    private String interceptorClasses;

    //key.deserializer
    @UriParam(label = "consumer", defaultValue = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER)
    private String keyDeserializer = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER;
    //value.deserializer
    @UriParam(label = "consumer", defaultValue = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER)
    private String valueDeserializer = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER;
    //fetch.min.bytes
    @UriParam(label = "consumer", defaultValue = "1")
    private Integer fetchMinBytes = 1;
    //fetch.min.bytes
    @UriParam(label = "consumer", defaultValue = "52428800")
    private Integer fetchMaxBytes = 50 * 1024 * 1024;
    //heartbeat.interval.ms
    @UriParam(label = "consumer", defaultValue = "3000")
    private Integer heartbeatIntervalMs = 3000;
    //max.partition.fetch.bytes
    @UriParam(label = "consumer", defaultValue = "1048576")
    private Integer maxPartitionFetchBytes = 1048576;
    //session.timeout.ms
    @UriParam(label = "consumer", defaultValue = "10000")
    private Integer sessionTimeoutMs = 10000;
    @UriParam(label = "consumer", defaultValue = "500")
    private Integer maxPollRecords;
    @UriParam(label = "consumer", defaultValue = "5000")
    private Long pollTimeoutMs = 5000L;
    @UriParam(label = "consumer")
    private Long maxPollIntervalMs;
    //auto.offset.reset1
    @UriParam(label = "consumer", defaultValue = "latest", enums = "latest,earliest,none")
    private String autoOffsetReset = "latest";
    //partition.assignment.strategy
    @UriParam(label = "consumer", defaultValue = KafkaConstants.PARTITIONER_RANGE_ASSIGNOR)
    private String partitionAssignor = KafkaConstants.PARTITIONER_RANGE_ASSIGNOR;
    //request.timeout.ms
    @UriParam(label = "consumer", defaultValue = "40000")
    private Integer consumerRequestTimeoutMs = 40000;
    //auto.commit.interval.ms
    @UriParam(label = "consumer", defaultValue = "5000")
    private Integer autoCommitIntervalMs = 5000;
    //check.crcs
    @UriParam(label = "consumer", defaultValue = "true")
    private Boolean checkCrcs = true;
    //fetch.max.wait.ms
    @UriParam(label = "consumer", defaultValue = "500")
    private Integer fetchWaitMaxMs = 500;
    @UriParam(label = "consumer", enums = "beginning,end")
    private String seekTo;

    //Consumer configuration properties
    @UriParam(label = "consumer", defaultValue = "true")
    private Boolean autoCommitEnable = true;
    @UriParam(label = "consumer")
    private boolean allowManualCommit;
    @UriParam(label = "consumer", defaultValue = "sync", enums = "sync,async,none")
    private String autoCommitOnStop = "sync";
    @UriParam(label = "consumer")
    private boolean breakOnFirstError;
    @UriParam(label = "consumer")
    private StateRepository<String, String> offsetRepository;

    //Producer Camel specific configuration properties
    @UriParam(label = "producer")
    private boolean bridgeEndpoint;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean circularTopicDetection = true;

    //Producer configuration properties
    @UriParam(label = "producer", defaultValue = KafkaConstants.KAFKA_DEFAULT_PARTITIONER)
    private String partitioner = KafkaConstants.KAFKA_DEFAULT_PARTITIONER;
    @UriParam(label = "producer", defaultValue = "100")
    private Integer retryBackoffMs = 100;

    @UriParam(label = "producer")
    private ExecutorService workerPool;
    @UriParam(label = "producer", defaultValue = "10")
    private Integer workerPoolCoreSize = 10;
    @UriParam(label = "producer", defaultValue = "20")
    private Integer workerPoolMaxSize = 20;

    //Async producer config
    @UriParam(label = "producer", defaultValue = "10000")
    private Integer queueBufferingMaxMessages = 10000;
    @UriParam(label = "producer", defaultValue = KafkaConstants.KAFKA_DEFAULT_SERIALIZER)
    private String serializerClass = KafkaConstants.KAFKA_DEFAULT_SERIALIZER;
    @UriParam(label = "producer", defaultValue = KafkaConstants.KAFKA_DEFAULT_SERIALIZER)
    private String keySerializerClass = KafkaConstants.KAFKA_DEFAULT_SERIALIZER;

    @UriParam(label = "producer")
    private String key;
    @UriParam(label = "producer")
    private Integer partitionKey;
    @UriParam(label = "producer", enums = "-1,0,1,all", defaultValue = "1")
    private String requestRequiredAcks = "1";
    //buffer.memory
    @UriParam(label = "producer", defaultValue = "33554432")
    private Integer bufferMemorySize = 33554432;
    //compression.type
    @UriParam(label = "producer", defaultValue = "none", enums = "none,gzip,snappy,lz4")
    private String compressionCodec = "none";
    //retries
    @UriParam(label = "producer", defaultValue = "0")
    private Integer retries = 0;
    //batch.size
    @UriParam(label = "producer", defaultValue = "16384")
    private Integer producerBatchSize = 16384;
    //connections.max.idle.ms
    @UriParam(label = "producer", defaultValue = "540000")
    private Integer connectionMaxIdleMs = 540000;
    //linger.ms
    @UriParam(label = "producer", defaultValue = "0")
    private Integer lingerMs = 0;
    //linger.ms
    @UriParam(label = "producer", defaultValue = "60000")
    private Integer maxBlockMs = 60000;
    //max.request.size
    @UriParam(label = "producer", defaultValue = "1048576")
    private Integer maxRequestSize = 1048576;
    //receive.buffer.bytes
    @UriParam(label = "producer", defaultValue = "65536")
    private Integer receiveBufferBytes = 65536;
    //request.timeout.ms
    @UriParam(label = "producer", defaultValue = "305000")
    private Integer requestTimeoutMs = 305000;
    //send.buffer.bytes
    @UriParam(label = "producer", defaultValue = "131072")
    private Integer sendBufferBytes = 131072;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean recordMetadata = true;
    //max.in.flight.requests.per.connection
    @UriParam(label = "producer", defaultValue = "5")
    private Integer maxInFlightRequest = 5;
    //metadata.max.age.ms
    @UriParam(label = "producer", defaultValue = "300000")
    private Integer metadataMaxAgeMs = 300000;
    //metric.reporters
    @UriParam(label = "producer")
    private String metricReporters;
    //metrics.num.samples
    @UriParam(label = "producer", defaultValue = "2")
    private Integer noOfMetricsSample = 2;
    //metrics.sample.window.ms
    @UriParam(label = "producer", defaultValue = "30000")
    private Integer metricsSampleWindowMs = 30000;
    //reconnect.backoff.ms
    @UriParam(label = "producer", defaultValue = "50")
    private Integer reconnectBackoffMs = 50;
    //enable.idempotence
    //reconnect.backoff.ms
    @UriParam(label = "producer", defaultValue = "false")
    private boolean enableIdempotence;
    //reconnect.backoff.max.ms
    @UriParam(label = "common", defaultValue = "1000")
    private Integer reconnectBackoffMaxMs = 1000;

    // SSL
    @UriParam(label = "common,security")
    private SSLContextParameters sslContextParameters;

    // SSL
    // ssl.key.password
    @UriParam(label = "producer,security", secret = true)
    private String sslKeyPassword;
    // ssl.keystore.location
    @UriParam(label = "producer,security")
    private String sslKeystoreLocation;
    // ssl.keystore.password
    @UriParam(label = "producer,security", secret = true)
    private String sslKeystorePassword;
    //ssl.truststore.location
    @UriParam(label = "producer,security")
    private String sslTruststoreLocation;
    //ssl.truststore.password
    @UriParam(label = "producer,security", secret = true)
    private String sslTruststorePassword;
    //SSL
    //ssl.enabled.protocols
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_ENABLED_PROTOCOLS)
    private String sslEnabledProtocols = SslConfigs.DEFAULT_SSL_ENABLED_PROTOCOLS;
    //ssl.keystore.type
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE)
    private String sslKeystoreType = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE;
    //ssl.protocol
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_PROTOCOL)
    private String sslProtocol = SslConfigs.DEFAULT_SSL_PROTOCOL;
    //ssl.provider
    @UriParam(label = "common,security")
    private String sslProvider;
    //ssl.truststore.type
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE)
    private String sslTruststoreType = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE;
    //SSL
    //ssl.cipher.suites
    @UriParam(label = "common,security")
    private String sslCipherSuites;
    //ssl.endpoint.identification.algorithm
    @UriParam(label = "common,security")
    private String sslEndpointAlgorithm;
    //ssl.keymanager.algorithm
    @UriParam(label = "common,security", defaultValue = "SunX509")
    private String sslKeymanagerAlgorithm = "SunX509";
    //ssl.trustmanager.algorithm
    @UriParam(label = "common,security", defaultValue = "PKIX")
    private String sslTrustmanagerAlgorithm = "PKIX";
    // SASL & sucurity Protocol
    //sasl.kerberos.service.name
    @UriParam(label = "common,security")
    private String saslKerberosServiceName;
    //security.protocol
    @UriParam(label = "common,security", defaultValue = CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL)
    private String securityProtocol = CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL;
    //SASL
    //sasl.kerberos.kinit.cmd
    @UriParam(label = "common,security", defaultValue = SaslConfigs.DEFAULT_SASL_MECHANISM)
    private String saslMechanism = SaslConfigs.DEFAULT_SASL_MECHANISM;
    //sasl.kerberos.kinit.cmd
    @UriParam(label = "common,security", defaultValue = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD)
    private String kerberosInitCmd = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD;
    //sasl.kerberos.min.time.before.relogin
    @UriParam(label = "common,security", defaultValue = "60000")
    private Integer kerberosBeforeReloginMinTime = 60000;
    //sasl.kerberos.ticket.renew.jitter
    @UriParam(label = "common,security", defaultValue = "0.05")
    private Double kerberosRenewJitter = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_JITTER;
    //sasl.kerberos.ticket.renew.window.factor
    @UriParam(label = "common,security", defaultValue = "0.8")
    private Double kerberosRenewWindowFactor = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_WINDOW_FACTOR;
    @UriParam(label = "common,security", defaultValue = "DEFAULT")
    //sasl.kerberos.principal.to.local.rules
    private String kerberosPrincipalToLocalRules; 
    @UriParam(label = "common,security", secret = true)
    //sasl.jaas.config
    private String saslJaasConfig;   

    public KafkaConfiguration() {
    }

    /**
     * Returns a copy of this configuration
     */
    public KafkaConfiguration copy() {
        try {
            KafkaConfiguration copy = (KafkaConfiguration) clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public Properties createProducerProperties() {
        Properties props = new Properties();
        addPropertyIfNotNull(props, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, getKeySerializerClass());
        addPropertyIfNotNull(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, getSerializerClass());
        addPropertyIfNotNull(props, ProducerConfig.ACKS_CONFIG, getRequestRequiredAcks());
        addPropertyIfNotNull(props, ProducerConfig.BUFFER_MEMORY_CONFIG, getBufferMemorySize());
        addPropertyIfNotNull(props, ProducerConfig.COMPRESSION_TYPE_CONFIG, getCompressionCodec());
        addPropertyIfNotNull(props, ProducerConfig.RETRIES_CONFIG, getRetries());
        addPropertyIfNotNull(props, ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, getInterceptorClasses());
        addPropertyIfNotNull(props, ProducerConfig.SEND_BUFFER_CONFIG, getRetries());
        addPropertyIfNotNull(props, ProducerConfig.BATCH_SIZE_CONFIG, getProducerBatchSize());
        addPropertyIfNotNull(props, ProducerConfig.CLIENT_ID_CONFIG, getClientId());
        addPropertyIfNotNull(props, ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, getConnectionMaxIdleMs());
        addPropertyIfNotNull(props, ProducerConfig.LINGER_MS_CONFIG, getLingerMs());
        addPropertyIfNotNull(props, ProducerConfig.MAX_BLOCK_MS_CONFIG, getMaxBlockMs());
        addPropertyIfNotNull(props, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, getMaxRequestSize());
        addPropertyIfNotNull(props, ProducerConfig.PARTITIONER_CLASS_CONFIG, getPartitioner());
        addPropertyIfNotNull(props, ProducerConfig.RECEIVE_BUFFER_CONFIG, getReceiveBufferBytes());
        addPropertyIfNotNull(props, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, getRequestTimeoutMs());
        addPropertyIfNotNull(props, ProducerConfig.SEND_BUFFER_CONFIG, getSendBufferBytes());
        addPropertyIfNotNull(props, ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, getMaxInFlightRequest());
        addPropertyIfNotNull(props, ProducerConfig.METADATA_MAX_AGE_CONFIG, getMetadataMaxAgeMs());
        addPropertyIfNotNull(props, ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, getMetricReporters());
        addPropertyIfNotNull(props, ProducerConfig.METRICS_NUM_SAMPLES_CONFIG, getNoOfMetricsSample());
        addPropertyIfNotNull(props, ProducerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG, getMetricsSampleWindowMs());
        addPropertyIfNotNull(props, ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, getReconnectBackoffMs());
        addPropertyIfNotNull(props, ProducerConfig.RETRY_BACKOFF_MS_CONFIG, getRetryBackoffMs());
        addPropertyIfNotNull(props, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, isEnableIdempotence());
        addPropertyIfNotNull(props, ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, getReconnectBackoffMaxMs());
        
        // SSL
        applySslConfiguration(props, getSslContextParameters());
        addPropertyIfNotNull(props, CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getSecurityProtocol());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEY_PASSWORD_CONFIG, getSslKeyPassword());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getSslKeystoreLocation());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getSslKeystorePassword());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getSslTruststoreLocation());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getSslTruststorePassword());
        addPropertyIfNotNull(props, SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, getSslEnabledProtocols());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, getSslKeystoreType());
        addPropertyIfNotNull(props, SslConfigs.SSL_PROTOCOL_CONFIG, getSslProtocol());
        addPropertyIfNotNull(props, SslConfigs.SSL_PROVIDER_CONFIG, getSslProvider());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, getSslTruststoreType());
        addPropertyIfNotNull(props, SslConfigs.SSL_CIPHER_SUITES_CONFIG, getSslCipherSuites());
        addPropertyIfNotNull(props, SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, getSslEndpointAlgorithm());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, getSslKeymanagerAlgorithm());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, getSslTrustmanagerAlgorithm());
        //SASL
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_SERVICE_NAME, getSaslKerberosServiceName());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_KINIT_CMD, getKerberosInitCmd());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_MIN_TIME_BEFORE_RELOGIN, getKerberosBeforeReloginMinTime());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_TICKET_RENEW_JITTER, getKerberosRenewJitter());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_TICKET_RENEW_WINDOW_FACTOR, getKerberosRenewWindowFactor());
        addListPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES, getKerberosPrincipalToLocalRules());
        addPropertyIfNotNull(props, SaslConfigs.SASL_MECHANISM, getSaslMechanism());
        addPropertyIfNotNull(props, SaslConfigs.SASL_JAAS_CONFIG, getSaslJaasConfig());

        return props;
    }

    public Properties createConsumerProperties() {
        Properties props = new Properties();
        addPropertyIfNotNull(props, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, getKeyDeserializer());
        addPropertyIfNotNull(props, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, getValueDeserializer());
        addPropertyIfNotNull(props, ConsumerConfig.FETCH_MIN_BYTES_CONFIG, getFetchMinBytes());
        addPropertyIfNotNull(props, ConsumerConfig.FETCH_MAX_BYTES_CONFIG, getFetchMaxBytes());
        addPropertyIfNotNull(props, ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, getHeartbeatIntervalMs());
        addPropertyIfNotNull(props, ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, getMaxPartitionFetchBytes());
        addPropertyIfNotNull(props, ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, getSessionTimeoutMs());
        addPropertyIfNotNull(props, ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, getMaxPollIntervalMs());
        addPropertyIfNotNull(props, ConsumerConfig.MAX_POLL_RECORDS_CONFIG, getMaxPollRecords());
        addPropertyIfNotNull(props, ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, getInterceptorClasses());
        addPropertyIfNotNull(props, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, getAutoOffsetReset());
        addPropertyIfNotNull(props, ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, getConnectionMaxIdleMs());
        addPropertyIfNotNull(props, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, isAutoCommitEnable());
        addPropertyIfNotNull(props, ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, getPartitionAssignor());
        addPropertyIfNotNull(props, ConsumerConfig.RECEIVE_BUFFER_CONFIG, getReceiveBufferBytes());
        addPropertyIfNotNull(props, ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, getConsumerRequestTimeoutMs());
        addPropertyIfNotNull(props, ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, getAutoCommitIntervalMs());
        addPropertyIfNotNull(props, ConsumerConfig.CHECK_CRCS_CONFIG, getCheckCrcs());
        addPropertyIfNotNull(props, ConsumerConfig.CLIENT_ID_CONFIG, getClientId());
        addPropertyIfNotNull(props, ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, getFetchWaitMaxMs());
        addPropertyIfNotNull(props, ConsumerConfig.METADATA_MAX_AGE_CONFIG, getMetadataMaxAgeMs());
        addPropertyIfNotNull(props, ConsumerConfig.METRIC_REPORTER_CLASSES_CONFIG, getMetricReporters());
        addPropertyIfNotNull(props, ConsumerConfig.METRICS_NUM_SAMPLES_CONFIG, getNoOfMetricsSample());
        addPropertyIfNotNull(props, ConsumerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG, getMetricsSampleWindowMs());
        addPropertyIfNotNull(props, ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, getReconnectBackoffMs());
        addPropertyIfNotNull(props, ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, getRetryBackoffMs());
        addPropertyIfNotNull(props, ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, getReconnectBackoffMaxMs());
        
        // SSL
        applySslConfiguration(props, getSslContextParameters());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEY_PASSWORD_CONFIG, getSslKeyPassword());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getSslKeystoreLocation());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getSslKeystorePassword());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getSslTruststoreLocation());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getSslTruststorePassword());
        addPropertyIfNotNull(props, SslConfigs.SSL_CIPHER_SUITES_CONFIG, getSslCipherSuites());
        addPropertyIfNotNull(props, SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, getSslEndpointAlgorithm());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, getSslKeymanagerAlgorithm());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, getSslTrustmanagerAlgorithm());
        addPropertyIfNotNull(props, SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, getSslEnabledProtocols());
        addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, getSslKeystoreType());
        addPropertyIfNotNull(props, SslConfigs.SSL_PROTOCOL_CONFIG, getSslProtocol());
        addPropertyIfNotNull(props, SslConfigs.SSL_PROVIDER_CONFIG, getSslProvider());
        addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, getSslTruststoreType());
        // Security protocol
        addPropertyIfNotNull(props, CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getSecurityProtocol());
        addPropertyIfNotNull(props, ProducerConfig.SEND_BUFFER_CONFIG, getSendBufferBytes());
        //SASL
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_SERVICE_NAME, getSaslKerberosServiceName());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_KINIT_CMD, getKerberosInitCmd());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_MIN_TIME_BEFORE_RELOGIN, getKerberosBeforeReloginMinTime());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_TICKET_RENEW_JITTER, getKerberosRenewJitter());
        addPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_TICKET_RENEW_WINDOW_FACTOR, getKerberosRenewWindowFactor());
        addListPropertyIfNotNull(props, SaslConfigs.SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES, getKerberosPrincipalToLocalRules());
        addPropertyIfNotNull(props, SaslConfigs.SASL_MECHANISM, getSaslMechanism());
        addPropertyIfNotNull(props, SaslConfigs.SASL_JAAS_CONFIG, getSaslJaasConfig());
        return props;
    }

    /**
     * Uses the standard camel {@link SSLContextParameters} object to fill the Kafka SSL properties
     *
     * @param props Kafka properties
     * @param sslContextParameters SSL configuration
     */
    private void applySslConfiguration(Properties props, SSLContextParameters sslContextParameters) {

        if (sslContextParameters != null) {
            addPropertyIfNotNull(props, SslConfigs.SSL_PROTOCOL_CONFIG, sslContextParameters.getSecureSocketProtocol());
            addPropertyIfNotNull(props, SslConfigs.SSL_PROVIDER_CONFIG, sslContextParameters.getProvider());

            CipherSuitesParameters cipherSuites = sslContextParameters.getCipherSuites();
            if (cipherSuites != null) {
                addCommaSeparatedList(props, SslConfigs.SSL_CIPHER_SUITES_CONFIG, cipherSuites.getCipherSuite());
            }

            SecureSocketProtocolsParameters secureSocketProtocols = sslContextParameters.getSecureSocketProtocols();
            if (secureSocketProtocols != null) {
                addCommaSeparatedList(props, SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, secureSocketProtocols.getSecureSocketProtocol());
            }

            KeyManagersParameters keyManagers = sslContextParameters.getKeyManagers();
            if (keyManagers != null) {
                addPropertyIfNotNull(props, SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, keyManagers.getAlgorithm());
                addPropertyIfNotNull(props, SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyManagers.getKeyPassword());

                KeyStoreParameters keyStore = keyManagers.getKeyStore();
                if (keyStore != null) {
                    addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, keyStore.getType());
                    addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStore.getResource());
                    addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStore.getPassword());
                }
            }

            TrustManagersParameters trustManagers = sslContextParameters.getTrustManagers();
            if (trustManagers != null) {
                addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, trustManagers.getAlgorithm());

                KeyStoreParameters keyStore = trustManagers.getKeyStore();
                if (keyStore != null) {
                    addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, keyStore.getType());
                    addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, keyStore.getResource());
                    addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStore.getPassword());
                }
            }
        }
    }

    private static <T> void addPropertyIfNotNull(Properties props, String key, T value) {
        if (value != null) {
            // Kafka expects all properties as String
            props.put(key, value.toString());
        }
    }

    private static <T> void addListPropertyIfNotNull(Properties props, String key, T value) {
        if (value != null) {
            // Kafka expects all properties as String
            String[] values = value.toString().split(",");
            List<String> list = Arrays.asList(values);
            props.put(key, list);
        }
    }

    private static void addCommaSeparatedList(Properties props, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            props.put(key, values.stream().collect(Collectors.joining(",")));
        }
    }

    public boolean isTopicIsPattern() {
        return topicIsPattern;
    }

    /**
     * Whether the topic is a pattern (regular expression).
     * This can be used to subscribe to dynamic number of topics matching the pattern.
     */
    public void setTopicIsPattern(boolean topicIsPattern) {
        this.topicIsPattern = topicIsPattern;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * A string that uniquely identifies the group of consumer processes to which this consumer belongs.
     * By setting the same group id multiple processes indicate that they are all part of the same consumer group.
     *
     * This option is required for consumers.
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If the option is true, then KafkaProducer will ignore the KafkaConstants.TOPIC header setting of the inbound message.
     */
    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public boolean isCircularTopicDetection() {
        return circularTopicDetection;
    }

    /**
     * If the option is true, then KafkaProducer will detect if the message is attempted to be sent back to the same topic
     * it may come from, if the message was original from a kafka consumer. If the KafkaConstants.TOPIC header is the
     * same as the original kafka consumer topic, then the header setting is ignored, and the topic of the producer
     * endpoint is used. In other words this avoids sending the same message back to where it came from.
     * This option is not in use if the option bridgeEndpoint is set to true.
     */
    public void setCircularTopicDetection(boolean circularTopicDetection) {
        this.circularTopicDetection = circularTopicDetection;
    }

    public String getPartitioner() {
        return partitioner;
    }

    /**
     * The partitioner class for partitioning messages amongst sub-topics. The default partitioner is based on the hash of the key.
     */
    public void setPartitioner(String partitioner) {
        this.partitioner = partitioner;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * Name of the topic to use.
     *
     * On the consumer you can use comma to separate multiple topics.
     * A producer can only send a message to a single topic.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getConsumerStreams() {
        return consumerStreams;
    }

    /**
     * Number of concurrent consumers on the consumer
     */
    public void setConsumerStreams(int consumerStreams) {
        this.consumerStreams = consumerStreams;
    }

    public int getConsumersCount() {
        return consumersCount;
    }

    /**
     * The number of consumers that connect to kafka server
     */
    public void setConsumersCount(int consumersCount) {
        this.consumersCount = consumersCount;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * The client id is a user-specified string sent in each request to help trace calls.
     * It should logically identify the application making the request.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Boolean isAutoCommitEnable() {
        return offsetRepository == null ? autoCommitEnable : false;
    }

    /**
     * If true, periodically commit to ZooKeeper the offset of messages already fetched by the consumer.
     * This committed offset will be used when the process fails as the position from which the new consumer will begin.
     */
    public void setAutoCommitEnable(Boolean autoCommitEnable) {
        this.autoCommitEnable = autoCommitEnable;
    }

    public boolean isAllowManualCommit() {
        return allowManualCommit;
    }

    /**
     * Whether to allow doing manual commits via {@link KafkaManualCommit}.
     * <p/>
     * If this option is enabled then an instance of {@link KafkaManualCommit} is stored on the {@link Exchange} message header,
     * which allows end users to access this API and perform manual offset commits via the Kafka consumer.
     */
    public void setAllowManualCommit(boolean allowManualCommit) {
        this.allowManualCommit = allowManualCommit;
    }

    public StateRepository<String, String> getOffsetRepository() {
        return offsetRepository;
    }

    /**
     * The offset repository to use in order to locally store the offset of each partition of the topic.
     * Defining one will disable the autocommit.
     */
    public void setOffsetRepository(StateRepository<String, String> offsetRepository) {
        this.offsetRepository = offsetRepository;
    }

    public Integer getAutoCommitIntervalMs() {
        return autoCommitIntervalMs;
    }

    /**
     * The frequency in ms that the consumer offsets are committed to zookeeper.
     */
    public void setAutoCommitIntervalMs(Integer autoCommitIntervalMs) {
        this.autoCommitIntervalMs = autoCommitIntervalMs;
    }

    public Integer getFetchMinBytes() {
        return fetchMinBytes;
    }

    /**
     * The minimum amount of data the server should return for a fetch request.
     * If insufficient data is available the request will wait for that much data to accumulate before answering the request.
     */
    public void setFetchMinBytes(Integer fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    /**
     * The maximum amount of data the server should return for a fetch request
     * This is not an absolute maximum, if the first message in the first non-empty partition of the fetch is larger than
     * this value, the message will still be returned to ensure that the consumer can make progress.
     * The maximum message size accepted by the broker is defined via message.max.bytes (broker config) or
     * max.message.bytes (topic config). Note that the consumer performs multiple fetches in parallel.
     */
    public Integer getFetchMaxBytes() {
        return fetchMaxBytes;
    }

    public void setFetchMaxBytes(Integer fetchMaxBytes) {
        this.fetchMaxBytes = fetchMaxBytes;
    }

    public Integer getFetchWaitMaxMs() {
        return fetchWaitMaxMs;
    }

    /**
     * The maximum amount of time the server will block before answering the fetch request if there isn't sufficient data to immediately satisfy fetch.min.bytes
     */
    public void setFetchWaitMaxMs(Integer fetchWaitMaxMs) {
        this.fetchWaitMaxMs = fetchWaitMaxMs;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    /**
     * What to do when there is no initial offset in ZooKeeper or if an offset is out of range:
     * smallest : automatically reset the offset to the smallest offset
     * largest : automatically reset the offset to the largest offset
     * fail: throw exception to the consumer
     */
    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public String getAutoCommitOnStop() {
        return autoCommitOnStop;
    }

    /**
     * Whether to perform an explicit auto commit when the consumer stops to ensure the broker
     * has a commit from the last consumed message. This requires the option autoCommitEnable is turned on.
     * The possible values are: sync, async, or none. And sync is the default value.
     */
    public void setAutoCommitOnStop(String autoCommitOnStop) {
        this.autoCommitOnStop = autoCommitOnStop;
    }

    public boolean isBreakOnFirstError() {
        return breakOnFirstError;
    }

    /**
     * This options controls what happens when a consumer is processing an exchange and it fails.
     * If the option is <tt>false</tt> then the consumer continues to the next message and processes it.
     * If the option is <tt>true</tt> then the consumer breaks out, and will seek back to offset of the
     * message that caused a failure, and then re-attempt to process this message. However this can lead
     * to endless processing of the same message if its bound to fail every time, eg a poison message.
     * Therefore its recommended to deal with that for example by using Camel's error handler.
     */
    public void setBreakOnFirstError(boolean breakOnFirstError) {
        this.breakOnFirstError = breakOnFirstError;
    }

    public String getBrokers() {
        return brokers;
    }

    /**
     * URL of the Kafka brokers to use.
     * The format is host1:port1,host2:port2, and the list can be a subset of brokers or a VIP pointing to a subset of brokers.
     * <p/>
     * This option is known as <tt>bootstrap.servers</tt> in the Kafka documentation.
     */
    public void setBrokers(String brokers) {
        this.brokers = brokers;
    }

    public String getCompressionCodec() {
        return compressionCodec;
    }

    /**
     * This parameter allows you to specify the compression codec for all data generated by this producer. Valid values are "none", "gzip" and "snappy".
     */
    public void setCompressionCodec(String compressionCodec) {
        this.compressionCodec = compressionCodec;
    }

    public Integer getRetryBackoffMs() {
        return retryBackoffMs;
    }

    /**
     * Before each retry, the producer refreshes the metadata of relevant topics to see if a new leader has been elected.
     * Since leader election takes a bit of time, this property specifies the amount of time that the producer waits before refreshing the metadata.
     */
    public void setRetryBackoffMs(Integer retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public Integer getSendBufferBytes() {
        return sendBufferBytes;
    }

    /**
     * Socket write buffer size
     */
    public void setSendBufferBytes(Integer sendBufferBytes) {
        this.sendBufferBytes = sendBufferBytes;
    }

    public Integer getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * The amount of time the broker will wait trying to meet the request.required.acks requirement before sending back an error to the client.
     */
    public void setRequestTimeoutMs(Integer requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public Integer getQueueBufferingMaxMessages() {
        return queueBufferingMaxMessages;
    }

    /**
     * The maximum number of unsent messages that can be queued up the producer when using async
     * mode before either the producer must be blocked or data must be dropped.
     */
    public void setQueueBufferingMaxMessages(Integer queueBufferingMaxMessages) {
        this.queueBufferingMaxMessages = queueBufferingMaxMessages;
    }

    public String getSerializerClass() {
        return serializerClass;
    }

    /**
     * The serializer class for messages.
     */
    public void setSerializerClass(String serializerClass) {
        this.serializerClass = serializerClass;
    }

    public String getKeySerializerClass() {
        return keySerializerClass;
    }

    /**
     * The serializer class for keys (defaults to the same as for messages if nothing is given).
     */
    public void setKeySerializerClass(String keySerializerClass) {
        this.keySerializerClass = keySerializerClass;
    }

    public String getKerberosInitCmd() {
        return kerberosInitCmd;
    }

    /**
     * Kerberos kinit command path. Default is /usr/bin/kinit
     */
    public void setKerberosInitCmd(String kerberosInitCmd) {
        this.kerberosInitCmd = kerberosInitCmd;
    }

    public Integer getKerberosBeforeReloginMinTime() {
        return kerberosBeforeReloginMinTime;
    }

    /**
     * Login thread sleep time between refresh attempts.
     */
    public void setKerberosBeforeReloginMinTime(Integer kerberosBeforeReloginMinTime) {
        this.kerberosBeforeReloginMinTime = kerberosBeforeReloginMinTime;
    }

    public Double getKerberosRenewJitter() {
        return kerberosRenewJitter;
    }

    /**
     * Percentage of random jitter added to the renewal time.
     */
    public void setKerberosRenewJitter(Double kerberosRenewJitter) {
        this.kerberosRenewJitter = kerberosRenewJitter;
    }

    public Double getKerberosRenewWindowFactor() {
        return kerberosRenewWindowFactor;
    }

    /**
     * Login thread will sleep until the specified window factor of time from last
     * refresh to ticket's expiry has been reached, at which time it will try to renew the ticket.
     */
    public void setKerberosRenewWindowFactor(Double kerberosRenewWindowFactor) {
        this.kerberosRenewWindowFactor = kerberosRenewWindowFactor;
    }

    public String getKerberosPrincipalToLocalRules() {
        return kerberosPrincipalToLocalRules;
    }

    /**
     * A list of rules for mapping from principal names to short names (typically operating system usernames).
     * The rules are evaluated in order and the first rule that matches a principal name is used to map it to a short name. Any later rules in the list are ignored.
     * By default, principal names of the form {username}/{hostname}@{REALM} are mapped to {username}.
     * For more details on the format please see <a href=\"#security_authz\"> security authorization and acls</a>.
     * <p/>
     * Multiple values can be separated by comma
     */
    public void setKerberosPrincipalToLocalRules(String kerberosPrincipalToLocalRules) {
        this.kerberosPrincipalToLocalRules = kerberosPrincipalToLocalRules;
    }

    public String getSslCipherSuites() {
        return sslCipherSuites;
    }

    /**
     * A list of cipher suites. This is a named combination of authentication, encryption,
     * MAC and key exchange algorithm used to negotiate the security settings for a network connection
     * using TLS or SSL network protocol.By default all the available cipher suites are supported.
     */
    public void setSslCipherSuites(String sslCipherSuites) {
        this.sslCipherSuites = sslCipherSuites;
    }

    public String getSslEndpointAlgorithm() {
        return sslEndpointAlgorithm;
    }

    /**
     * The endpoint identification algorithm to validate server hostname using server certificate.
     */
    public void setSslEndpointAlgorithm(String sslEndpointAlgorithm) {
        this.sslEndpointAlgorithm = sslEndpointAlgorithm;
    }

    public String getSslKeymanagerAlgorithm() {
        return sslKeymanagerAlgorithm;
    }

    /**
     * The algorithm used by key manager factory for SSL connections. Default value is the key
     * manager factory algorithm configured for the Java Virtual Machine.
     */
    public void setSslKeymanagerAlgorithm(String sslKeymanagerAlgorithm) {
        this.sslKeymanagerAlgorithm = sslKeymanagerAlgorithm;
    }

    public String getSslTrustmanagerAlgorithm() {
        return sslTrustmanagerAlgorithm;
    }

    /**
     * The algorithm used by trust manager factory for SSL connections. Default value is the
     * trust manager factory algorithm configured for the Java Virtual Machine.
     */
    public void setSslTrustmanagerAlgorithm(String sslTrustmanagerAlgorithm) {
        this.sslTrustmanagerAlgorithm = sslTrustmanagerAlgorithm;
    }

    public String getSslEnabledProtocols() {
        return sslEnabledProtocols;
    }

    /**
     * The list of protocols enabled for SSL connections. TLSv1.2, TLSv1.1 and TLSv1 are enabled by default.
     */
    public void setSslEnabledProtocols(String sslEnabledProtocols) {
        this.sslEnabledProtocols = sslEnabledProtocols;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    /**
     * The file format of the key store file. This is optional for client. Default value is JKS
     */
    public void setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    /**
     * The SSL protocol used to generate the SSLContext. Default setting is TLS, which is fine for most cases.
     * Allowed values in recent JVMs are TLS, TLSv1.1 and TLSv1.2. SSL, SSLv2 and SSLv3 may be supported in older JVMs,
     * but their usage is discouraged due to known security vulnerabilities.
     */
    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public String getSslProvider() {
        return sslProvider;
    }

    /**
     * The name of the security provider used for SSL connections. Default value is the default security provider of the JVM.
     */
    public void setSslProvider(String sslProvider) {
        this.sslProvider = sslProvider;
    }

    public String getSslTruststoreType() {
        return sslTruststoreType;
    }

    /**
     * The file format of the trust store file. Default value is JKS.
     */
    public void setSslTruststoreType(String sslTruststoreType) {
        this.sslTruststoreType = sslTruststoreType;
    }

    public String getSaslKerberosServiceName() {
        return saslKerberosServiceName;
    }

    /**
     * The Kerberos principal name that Kafka runs as. This can be defined either in Kafka's JAAS
     * config or in Kafka's config.
     */
    public void setSaslKerberosServiceName(String saslKerberosServiceName) {
        this.saslKerberosServiceName = saslKerberosServiceName;
    }

    public String getSaslMechanism() {
        return saslMechanism;
    }

    /**
     * The Simple Authentication and Security Layer (SASL) Mechanism used.
     * For the valid values see <a href="http://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml">http://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml</a>
     */
    public void setSaslMechanism(String saslMechanism) {
        this.saslMechanism = saslMechanism;
    }
    
    public String getSaslJaasConfig() {
        return saslJaasConfig;
    }

    /**
     * Expose the kafka sasl.jaas.config parameter
     * 
     * Example:
     * org.apache.kafka.common.security.plain.PlainLoginModule required username="USERNAME" password="PASSWORD";
     */
    public void setSaslJaasConfig(String saslMechanism) {
        this.saslJaasConfig = saslMechanism;
    }

    public String getSecurityProtocol() {
        return securityProtocol;
    }

    /**
     * Protocol used to communicate with brokers. Currently only PLAINTEXT and SSL are supported.
     */
    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * SSL configuration using a Camel {@link SSLContextParameters} object. If configured it's applied before the other SSL endpoint parameters.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }


    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    /**
     * The password of the private key in the key store file. This is optional for client.
     */
    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getSslKeystoreLocation() {
        return sslKeystoreLocation;
    }

    /**
     * The location of the key store file. This is optional for client and can be used for two-way
     * authentication for client.
     */
    public void setSslKeystoreLocation(String sslKeystoreLocation) {
        this.sslKeystoreLocation = sslKeystoreLocation;
    }

    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    /**
     * The store password for the key store file.This is optional for client and only needed
     * if ssl.keystore.location is configured.
     */
    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    public String getSslTruststoreLocation() {
        return sslTruststoreLocation;
    }

    /**
     * The location of the trust store file.
     */
    public void setSslTruststoreLocation(String sslTruststoreLocation) {
        this.sslTruststoreLocation = sslTruststoreLocation;
    }

    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }


    /**
     * The password for the trust store file.
     */
    public void setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
    }

    public Integer getBufferMemorySize() {
        return bufferMemorySize;
    }

    /**
     * The total bytes of memory the producer can use to buffer records waiting to be sent to the server.
     * If records are sent faster than they can be delivered to the server the producer will either block
     * or throw an exception based on the preference specified by block.on.buffer.full.This setting should
     * correspond roughly to the total memory the producer will use, but is not a hard bound since not all
     * memory the producer uses is used for buffering. Some additional memory will be used for compression
     * (if compression is enabled) as well as for maintaining in-flight requests.
     */
    public void setBufferMemorySize(Integer bufferMemorySize) {
        this.bufferMemorySize = bufferMemorySize;
    }

    public String getKey() {
        return key;
    }

    /**
     * The record key (or null if no key is specified).
     * If this option has been configured then it take precedence over header {@link KafkaConstants#KEY}
     */
    public void setKey(String key) {
        this.key = key;
    }

    public Integer getPartitionKey() {
        return partitionKey;
    }

    /**
     * The partition to which the record will be sent (or null if no partition was specified).
     * If this option has been configured then it take precedence over header {@link KafkaConstants#PARTITION_KEY}
     */
    public void setPartitionKey(Integer partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getRequestRequiredAcks() {
        return requestRequiredAcks;
    }

    /**
     * The number of acknowledgments the producer requires the leader to have received before considering a request complete.
     * This controls the durability of records that are sent. The following settings are common:
     * acks=0 If set to zero then the producer will not wait for any acknowledgment from the server at all.
     * The record will be immediately added to the socket buffer and considered sent. No guarantee can be made that the server
     * has received the record in this case, and the retries configuration will not take effect (as the client won't generally
     * know of any failures). The offset given back for each record will always be set to -1.
     * acks=1 This will mean the leader will write the record to its local log but will respond without awaiting full acknowledgement
     * from all followers. In this case should the leader fail immediately after acknowledging the record but before the followers have
     * replicated it then the record will be lost.
     * acks=all This means the leader will wait for the full set of in-sync replicas to acknowledge the record. This guarantees that the
     * record will not be lost as long as at least one in-sync replica remains alive. This is the strongest available guarantee.
     */
    public void setRequestRequiredAcks(String requestRequiredAcks) {
        this.requestRequiredAcks = requestRequiredAcks;
    }

    public Integer getRetries() {
        return retries;
    }

    /**
     * Setting a value greater than zero will cause the client to resend any record whose send fails with a potentially transient error.
     * Note that this retry is no different than if the client resent the record upon receiving the error. Allowing retries will potentially
     * change the ordering of records because if two records are sent to a single partition, and the first fails and is retried but the second
     * succeeds, then the second record may appear first.
     */
    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public Integer getProducerBatchSize() {
        return producerBatchSize;
    }

    /**
     * The producer will attempt to batch records together into fewer requests whenever multiple records are being sent to the same partition.
     * This helps performance on both the client and the server. This configuration controls the default batch size in bytes.
     * No attempt will be made to batch records larger than this size.Requests sent to brokers will contain multiple batches, one for each
     * partition with data available to be sent.A small batch size will make batching less common and may reduce throughput (a batch size of zero
     * will disable batching entirely). A very large batch size may use memory a bit more wastefully as we will always allocate a buffer of the
     * specified batch size in anticipation of additional records.
     */
    public void setProducerBatchSize(Integer producerBatchSize) {
        this.producerBatchSize = producerBatchSize;
    }

    public Integer getConnectionMaxIdleMs() {
        return connectionMaxIdleMs;
    }

    /**
     * Close idle connections after the number of milliseconds specified by this config.
     */
    public void setConnectionMaxIdleMs(Integer connectionMaxIdleMs) {
        this.connectionMaxIdleMs = connectionMaxIdleMs;
    }

    public Integer getLingerMs() {
        return lingerMs;
    }

    /**
     * The producer groups together any records that arrive in between request transmissions into a single batched request. Normally this
     * occurs only under load when records arrive faster than they can be sent out. However in some circumstances the client may want to reduce
     * the number of requests even under moderate load. This setting accomplishes this by adding a small amount of artificial delay—that is,
     * rather than immediately sending out a record the producer will wait for up to the given delay to allow other records to be sent so that
     * the sends can be batched together. This can be thought of as analogous to Nagle's algorithm in TCP. This setting gives the upper bound on
     * the delay for batching: once we get batch.size worth of records for a partition it will be sent immediately regardless of this setting,
     * however if we have fewer than this many bytes accumulated for this partition we will 'linger' for the specified time waiting for more
     * records to show up. This setting defaults to 0 (i.e. no delay). Setting linger.ms=5, for example, would have the effect of reducing the
     * number of requests sent but would add up to 5ms of latency to records sent in the absense of load.
     */
    public void setLingerMs(Integer lingerMs) {
        this.lingerMs = lingerMs;
    }

    public Integer getMaxBlockMs() {
        return maxBlockMs;
    }

    /**
     * The configuration controls how long sending to kafka will block. These methods can be
     * blocked for multiple reasons. For e.g: buffer full, metadata unavailable.This configuration imposes maximum limit on the total time spent
     * in fetching metadata, serialization of key and value, partitioning and allocation of buffer memory when doing a send(). In case of
     * partitionsFor(), this configuration imposes a maximum time threshold on waiting for metadata
     */
    public void setMaxBlockMs(Integer maxBlockMs) {
        this.maxBlockMs = maxBlockMs;
    }

    public Integer getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * The maximum size of a request. This is also effectively a cap on the maximum record size. Note that the server has its own cap on record size
     * which may be different from this. This setting will limit the number of record batches the producer will send in a single request to avoid
     * sending huge requests.
     */
    public void setMaxRequestSize(Integer maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public Integer getReceiveBufferBytes() {
        return receiveBufferBytes;
    }

    /**
     * The size of the TCP receive buffer (SO_RCVBUF) to use when reading data.
     */
    public void setReceiveBufferBytes(Integer receiveBufferBytes) {
        this.receiveBufferBytes = receiveBufferBytes;
    }

    public Integer getMaxInFlightRequest() {
        return maxInFlightRequest;
    }

    /**
     * The maximum number of unacknowledged requests the client will send on a single connection before blocking. Note that if this setting
     * is set to be greater than 1 and there are failed sends, there is a risk of message re-ordering due to retries (i.e., if retries are enabled).
     */
    public void setMaxInFlightRequest(Integer maxInFlightRequest) {
        this.maxInFlightRequest = maxInFlightRequest;
    }

    public Integer getMetadataMaxAgeMs() {
        return metadataMaxAgeMs;
    }

    /**
     * The period of time in milliseconds after which we force a refresh of metadata even if we haven't seen any partition leadership
     * changes to proactively discover any new brokers or partitions.
     */
    public void setMetadataMaxAgeMs(Integer metadataMaxAgeMs) {
        this.metadataMaxAgeMs = metadataMaxAgeMs;
    }

    public String getMetricReporters() {
        return metricReporters;
    }

    /**
     * A list of classes to use as metrics reporters. Implementing the MetricReporter interface allows plugging in classes that will be
     * notified of new metric creation. The JmxReporter is always included to register JMX statistics.
     */
    public void setMetricReporters(String metricReporters) {
        this.metricReporters = metricReporters;
    }

    public Integer getNoOfMetricsSample() {
        return noOfMetricsSample;
    }

    /**
     * The number of samples maintained to compute metrics.
     */
    public void setNoOfMetricsSample(Integer noOfMetricsSample) {
        this.noOfMetricsSample = noOfMetricsSample;
    }

    public Integer getMetricsSampleWindowMs() {
        return metricsSampleWindowMs;
    }

    /**
     * The number of samples maintained to compute metrics.
     */
    public void setMetricsSampleWindowMs(Integer metricsSampleWindowMs) {
        this.metricsSampleWindowMs = metricsSampleWindowMs;
    }

    public Integer getReconnectBackoffMs() {
        return reconnectBackoffMs;
    }

    /**
     * The amount of time to wait before attempting to reconnect to a given host. This avoids repeatedly connecting to a host
     * in a tight loop. This backoff applies to all requests sent by the consumer to the broker.
     */
    public void setReconnectBackoffMs(Integer reconnectBackoffMs) {
        this.reconnectBackoffMs = reconnectBackoffMs;
    }

    public Integer getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * The expected time between heartbeats to the consumer coordinator when using Kafka's group management facilities.
     * Heartbeats are used to ensure that the consumer's session stays active and to facilitate rebalancing when new
     * consumers join or leave the group. The value must be set lower than session.timeout.ms, but typically should be set
     * no higher than 1/3 of that value. It can be adjusted even lower to control the expected time for normal rebalances.
     */
    public void setHeartbeatIntervalMs(Integer heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public Integer getMaxPartitionFetchBytes() {
        return maxPartitionFetchBytes;
    }

    /**
     * The maximum amount of data per-partition the server will return. The maximum total memory used for
     * a request will be #partitions * max.partition.fetch.bytes. This size must be at least as large as the
     * maximum message size the server allows or else it is possible for the producer to send messages larger
     * than the consumer can fetch. If that happens, the consumer can get stuck trying to fetch a large message
     * on a certain partition.
     */
    public void setMaxPartitionFetchBytes(Integer maxPartitionFetchBytes) {
        this.maxPartitionFetchBytes = maxPartitionFetchBytes;
    }

    public Integer getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /**
     * The timeout used to detect failures when using Kafka's group management facilities.
     */
    public void setSessionTimeoutMs(Integer sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public Integer getMaxPollRecords() {
        return maxPollRecords;
    }

    /**
     * The maximum number of records returned in a single call to poll()
     */
    public void setMaxPollRecords(Integer maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public Long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    /**
     * The timeout used when polling the KafkaConsumer.
     */
    public void setPollTimeoutMs(Long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public Long getMaxPollIntervalMs() {
        return maxPollIntervalMs;
    }

    /**
     * The maximum delay between invocations of poll() when using consumer group management.
     * This places an upper bound on the amount of time that the consumer can be idle before fetching more records.
     * If poll() is not called before expiration of this timeout, then the consumer is considered failed and the group
     * will rebalance in order to reassign the partitions to another member.
     */
    public void setMaxPollIntervalMs(Long maxPollIntervalMs) {
        this.maxPollIntervalMs = maxPollIntervalMs;
    }

    public String getPartitionAssignor() {
        return partitionAssignor;
    }

    /**
     * The class name of the partition assignment strategy that the client will use to distribute
     * partition ownership amongst consumer instances when group management is used
     */
    public void setPartitionAssignor(String partitionAssignor) {
        this.partitionAssignor = partitionAssignor;
    }

    public Integer getConsumerRequestTimeoutMs() {
        return consumerRequestTimeoutMs;
    }

    /**
     * The configuration controls the maximum amount of time the client will wait for the response
     * of a request. If the response is not received before the timeout elapses the client will resend
     * the request if necessary or fail the request if retries are exhausted.
     */
    public void setConsumerRequestTimeoutMs(Integer consumerRequestTimeoutMs) {
        this.consumerRequestTimeoutMs = consumerRequestTimeoutMs;
    }

    public Boolean getCheckCrcs() {
        return checkCrcs;
    }

    /**
     * Automatically check the CRC32 of the records consumed. This ensures no on-the-wire or on-disk
     * corruption to the messages occurred. This check adds some overhead, so it may be disabled in
     * cases seeking extreme performance.
     */
    public void setCheckCrcs(Boolean checkCrcs) {
        this.checkCrcs = checkCrcs;
    }

    public String getKeyDeserializer() {
        return keyDeserializer;
    }

    /**
     * Deserializer class for key that implements the Deserializer interface.
     */
    public void setKeyDeserializer(String keyDeserializer) {
        this.keyDeserializer = keyDeserializer;
    }

    public String getValueDeserializer() {
        return valueDeserializer;
    }

    /**
     * Deserializer class for value that implements the Deserializer interface.
     */
    public void setValueDeserializer(String valueDeserializer) {
        this.valueDeserializer = valueDeserializer;
    }

    public String getSeekTo() {
        return seekTo;
    }

    /**
     * Set if KafkaConsumer will read from beginning or end on startup:
     * beginning : read from beginning
     * end : read from end
     * 
     * This is replacing the earlier property seekToBeginning
     */
    public void setSeekTo(String seekTo) {
        this.seekTo = seekTo;
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    /**
     * To use a custom worker pool for continue routing {@link Exchange} after kafka server has acknowledge
     * the message that was sent to it from {@link KafkaProducer} using asynchronous non-blocking processing.
     */
    public void setWorkerPool(ExecutorService workerPool) {
        this.workerPool = workerPool;
    }

    public Integer getWorkerPoolCoreSize() {
        return workerPoolCoreSize;
    }

    /**
     * Number of core threads for the worker pool for continue routing {@link Exchange} after kafka server has acknowledge
     * the message that was sent to it from {@link KafkaProducer} using asynchronous non-blocking processing.
     */
    public void setWorkerPoolCoreSize(Integer workerPoolCoreSize) {
        this.workerPoolCoreSize = workerPoolCoreSize;
    }

    public Integer getWorkerPoolMaxSize() {
        return workerPoolMaxSize;
    }

    /**
     * Maximum number of threads for the worker pool for continue routing {@link Exchange} after kafka server has acknowledge
     * the message that was sent to it from {@link KafkaProducer} using asynchronous non-blocking processing.
     */
    public void setWorkerPoolMaxSize(Integer workerPoolMaxSize) {
        this.workerPoolMaxSize = workerPoolMaxSize;
    }

    public boolean isRecordMetadata() {
        return recordMetadata;
    }

    /**
     * Whether the producer should store the {@link RecordMetadata} results from sending to Kafka.
     *
     * The results are stored in a {@link List} containing the {@link RecordMetadata} metadata's.
     * The list is stored on a header with the key {@link KafkaConstants#KAFKA_RECORDMETA}
     */
    public void setRecordMetadata(boolean recordMetadata) {
        this.recordMetadata = recordMetadata;
    }


    public String getInterceptorClasses() {
        return interceptorClasses;
    }
    /**
     * Sets interceptors for producer or consumers.
     * Producer interceptors have to be classes implementing {@link org.apache.kafka.clients.producer.ProducerInterceptor}
     * Consumer interceptors have to be classes implementing {@link org.apache.kafka.clients.consumer.ConsumerInterceptor}
     * Note that if you use Producer interceptor on a consumer it will throw a class cast exception in runtime
     */
    public void setInterceptorClasses(String interceptorClasses) {
        this.interceptorClasses = interceptorClasses;
    }

    public boolean isEnableIdempotence() {
        return enableIdempotence;
    }

    /**
     * If set to 'true' the producer will ensure that exactly one copy of each message is written in the stream. If 'false', producer 
     * retries may write duplicates of the retried message in the stream. If set to true this option will require max.in.flight.requests.per.connection to be set to 1 and 
     * retries cannot be zero and additionally acks must be set to 'all'. 
     */
    public void setEnableIdempotence(boolean enableIdempotence) {
        this.enableIdempotence = enableIdempotence;
    }

    public Integer getReconnectBackoffMaxMs() {
        return reconnectBackoffMaxMs;
    }

    /**
     * The maximum amount of time in milliseconds to wait when reconnecting to a
     * broker that has repeatedly failed to connect. If provided, the backoff
     * per host will increase exponentially for each consecutive connection
     * failure, up to this maximum. After calculating the backoff increase, 20%
     * random jitter is added to avoid connection storms.
     */
    public void setReconnectBackoffMaxMs(Integer reconnectBackoffMaxMs) {
        this.reconnectBackoffMaxMs = reconnectBackoffMaxMs;
    }
}
