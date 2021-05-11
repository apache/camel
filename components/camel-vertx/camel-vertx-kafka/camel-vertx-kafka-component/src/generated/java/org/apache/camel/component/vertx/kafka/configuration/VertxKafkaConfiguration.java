package org.apache.camel.component.vertx.kafka.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class VertxKafkaConfiguration
        extends
            BaseVertxKafkaConfiguration
        implements
            Cloneable {

    // partition.id
    @UriParam(label = "common")
    private Integer partitionId;
    // topic
    @UriPath(label = "common")
    @Metadata(required = true)
    private String topic;
    // bootstrap.servers
    @UriParam(label = "common")
    private String bootstrapServers;
    // client.dns.lookup
    @UriParam(label = "common", defaultValue = "use_all_dns_ips", enums = "default,use_all_dns_ips,resolve_canonical_bootstrap_servers_only")
    private String clientDnsLookup = "use_all_dns_ips";
    // metadata.max.age.ms
    @UriParam(label = "common", defaultValue = "5m", javaType = "java.time.Duration")
    private long metadataMaxAgeMs = 300000;
    // client.id
    @UriParam(label = "common")
    private String clientId;
    // send.buffer.bytes
    @UriParam(label = "common", defaultValue = "131072")
    private int sendBufferBytes = 131072;
    // receive.buffer.bytes
    @UriParam(label = "common", defaultValue = "32768")
    private int receiveBufferBytes = 32768;
    // reconnect.backoff.ms
    @UriParam(label = "common", defaultValue = "50ms", javaType = "java.time.Duration")
    private long reconnectBackoffMs = 50;
    // reconnect.backoff.max.ms
    @UriParam(label = "common", defaultValue = "1s", javaType = "java.time.Duration")
    private long reconnectBackoffMaxMs = 1000;
    // retry.backoff.ms
    @UriParam(label = "common", defaultValue = "100ms", javaType = "java.time.Duration")
    private long retryBackoffMs = 100;
    // metrics.sample.window.ms
    @UriParam(label = "common", defaultValue = "30s", javaType = "java.time.Duration")
    private long metricsSampleWindowMs = 30000;
    // metrics.num.samples
    @UriParam(label = "common", defaultValue = "2")
    private int metricsNumSamples = 2;
    // metrics.recording.level
    @UriParam(label = "common", defaultValue = "INFO", enums = "INFO,DEBUG,TRACE")
    private String metricsRecordingLevel = "INFO";
    // metric.reporters
    @UriParam(label = "common")
    private String metricReporters;
    // request.timeout.ms
    @UriParam(label = "common", defaultValue = "30s", javaType = "java.time.Duration")
    private int requestTimeoutMs = 30000;
    // socket.connection.setup.timeout.ms
    @UriParam(label = "common", defaultValue = "10s", javaType = "java.time.Duration")
    private long socketConnectionSetupTimeoutMs = 10000;
    // socket.connection.setup.timeout.max.ms
    @UriParam(label = "common", defaultValue = "30s", javaType = "java.time.Duration")
    private long socketConnectionSetupTimeoutMaxMs = 30000;
    // connections.max.idle.ms
    @UriParam(label = "common", defaultValue = "9m", javaType = "java.time.Duration")
    private long connectionsMaxIdleMs = 540000;
    // interceptor.classes
    @UriParam(label = "common")
    private String interceptorClasses;
    // security.providers
    @UriParam(label = "common,security")
    private String securityProviders;
    // security.protocol
    @UriParam(label = "common,security", defaultValue = "PLAINTEXT")
    private String securityProtocol = "PLAINTEXT";
    // ssl.protocol
    @UriParam(label = "common,security", defaultValue = "TLSv1.2")
    private String sslProtocol = "TLSv1.2";
    // ssl.provider
    @UriParam(label = "common,security")
    private String sslProvider;
    // ssl.cipher.suites
    @UriParam(label = "common,security")
    private String sslCipherSuites;
    // ssl.enabled.protocols
    @UriParam(label = "common,security", defaultValue = "TLSv1.2,TLSv1.3")
    private String sslEnabledProtocols = "TLSv1.2,TLSv1.3";
    // ssl.keystore.type
    @UriParam(label = "common,security", defaultValue = "JKS")
    private String sslKeystoreType = "JKS";
    // ssl.keystore.location
    @UriParam(label = "common,security")
    private String sslKeystoreLocation;
    // ssl.keystore.password
    @UriParam(label = "common,security")
    private String sslKeystorePassword;
    // ssl.key.password
    @UriParam(label = "common,security")
    private String sslKeyPassword;
    // ssl.keystore.key
    @UriParam(label = "common,security")
    private String sslKeystoreKey;
    // ssl.keystore.certificate.chain
    @UriParam(label = "common,security")
    private String sslKeystoreCertificateChain;
    // ssl.truststore.certificates
    @UriParam(label = "common,security")
    private String sslTruststoreCertificates;
    // ssl.truststore.type
    @UriParam(label = "common,security", defaultValue = "JKS")
    private String sslTruststoreType = "JKS";
    // ssl.truststore.location
    @UriParam(label = "common,security")
    private String sslTruststoreLocation;
    // ssl.truststore.password
    @UriParam(label = "common,security")
    private String sslTruststorePassword;
    // ssl.keymanager.algorithm
    @UriParam(label = "common,security", defaultValue = "SunX509")
    private String sslKeymanagerAlgorithm = "SunX509";
    // ssl.trustmanager.algorithm
    @UriParam(label = "common,security", defaultValue = "PKIX")
    private String sslTrustmanagerAlgorithm = "PKIX";
    // ssl.endpoint.identification.algorithm
    @UriParam(label = "common,security", defaultValue = "https")
    private String sslEndpointIdentificationAlgorithm = "https";
    // ssl.secure.random.implementation
    @UriParam(label = "common,security")
    private String sslSecureRandomImplementation;
    // ssl.engine.factory.class
    @UriParam(label = "common,security")
    private String sslEngineFactoryClass;
    // sasl.kerberos.service.name
    @UriParam(label = "common,security")
    private String saslKerberosServiceName;
    // sasl.kerberos.kinit.cmd
    @UriParam(label = "common,security", defaultValue = "/usr/bin/kinit")
    private String saslKerberosKinitCmd = "/usr/bin/kinit";
    // sasl.kerberos.ticket.renew.window.factor
    @UriParam(label = "common,security", defaultValue = "0.8")
    private double saslKerberosTicketRenewWindowFactor = 0.8;
    // sasl.kerberos.ticket.renew.jitter
    @UriParam(label = "common,security", defaultValue = "0.05")
    private double saslKerberosTicketRenewJitter = 0.05;
    // sasl.kerberos.min.time.before.relogin
    @UriParam(label = "common,security", defaultValue = "60000")
    private long saslKerberosMinTimeBeforeRelogin = 60000;
    // sasl.login.refresh.window.factor
    @UriParam(label = "common,security", defaultValue = "0.8")
    private double saslLoginRefreshWindowFactor = 0.8;
    // sasl.login.refresh.window.jitter
    @UriParam(label = "common,security", defaultValue = "0.05")
    private double saslLoginRefreshWindowJitter = 0.05;
    // sasl.login.refresh.min.period.seconds
    @UriParam(label = "common,security", defaultValue = "60")
    private short saslLoginRefreshMinPeriodSeconds = 60;
    // sasl.login.refresh.buffer.seconds
    @UriParam(label = "common,security", defaultValue = "300")
    private short saslLoginRefreshBufferSeconds = 300;
    // sasl.mechanism
    @UriParam(label = "common,security", defaultValue = "GSSAPI")
    private String saslMechanism = "GSSAPI";
    // sasl.jaas.config
    @UriParam(label = "common,security")
    private String saslJaasConfig;
    // sasl.client.callback.handler.class
    @UriParam(label = "common,security")
    private String saslClientCallbackHandlerClass;
    // sasl.login.callback.handler.class
    @UriParam(label = "common,security")
    private String saslLoginCallbackHandlerClass;
    // sasl.login.class
    @UriParam(label = "common,security")
    private String saslLoginClass;
    // Additional properties
    @UriParam(label = "common", prefix = "additionalProperties.", multiValue = true)
    private Map<String, Object> additionalProperties = new HashMap<>();
    // seek.to.offset
    @UriParam(label = "consumer")
    private Long seekToOffset;
    // seek.to.position
    @UriParam(label = "consumer", enums = "beginning,end")
    private String seekToPosition;
    // group.id
    @UriParam(label = "consumer")
    private String groupId;
    // group.instance.id
    @UriParam(label = "consumer")
    private String groupInstanceId;
    // session.timeout.ms
    @UriParam(label = "consumer", defaultValue = "10s", javaType = "java.time.Duration")
    private int sessionTimeoutMs = 10000;
    // heartbeat.interval.ms
    @UriParam(label = "consumer", defaultValue = "3s", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 3000;
    // partition.assignment.strategy
    @UriParam(label = "consumer", defaultValue = "org.apache.kafka.clients.consumer.RangeAssignor")
    private String partitionAssignmentStrategy = "org.apache.kafka.clients.consumer.RangeAssignor";
    // enable.auto.commit
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean enableAutoCommit = true;
    // auto.commit.interval.ms
    @UriParam(label = "consumer", defaultValue = "5s", javaType = "java.time.Duration")
    private int autoCommitIntervalMs = 5000;
    // client.rack
    @UriParam(label = "consumer")
    private String clientRack;
    // max.partition.fetch.bytes
    @UriParam(label = "consumer", defaultValue = "1048576")
    private int maxPartitionFetchBytes = 1048576;
    // fetch.min.bytes
    @UriParam(label = "consumer", defaultValue = "1")
    private int fetchMinBytes = 1;
    // fetch.max.bytes
    @UriParam(label = "consumer", defaultValue = "52428800")
    private int fetchMaxBytes = 52428800;
    // fetch.max.wait.ms
    @UriParam(label = "consumer", defaultValue = "500ms", javaType = "java.time.Duration")
    private int fetchMaxWaitMs = 500;
    // auto.offset.reset
    @UriParam(label = "consumer", defaultValue = "latest", enums = "latest,earliest,none")
    private String autoOffsetReset = "latest";
    // check.crcs
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean checkCrcs = true;
    // key.deserializer
    @UriParam(label = "consumer", defaultValue = "org.apache.kafka.common.serialization.StringDeserializer")
    private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
    // value.deserializer
    @UriParam(label = "consumer", defaultValue = "org.apache.kafka.common.serialization.StringDeserializer")
    private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
    // default.api.timeout.ms
    @UriParam(label = "consumer", defaultValue = "1m", javaType = "java.time.Duration")
    private int defaultApiTimeoutMs = 60000;
    // max.poll.records
    @UriParam(label = "consumer", defaultValue = "500")
    private int maxPollRecords = 500;
    // max.poll.interval.ms
    @UriParam(label = "consumer", defaultValue = "5m", javaType = "java.time.Duration")
    private int maxPollIntervalMs = 300000;
    // exclude.internal.topics
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean excludeInternalTopics = true;
    // isolation.level
    @UriParam(label = "consumer", defaultValue = "read_uncommitted", enums = "read_committed,read_uncommitted")
    private String isolationLevel = "read_uncommitted";
    // allow.auto.create.topics
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean allowAutoCreateTopics = true;
    // buffer.memory
    @UriParam(label = "producer", defaultValue = "33554432")
    private long bufferMemory = 33554432;
    // retries
    @UriParam(label = "producer", defaultValue = "2147483647")
    private int retries = 2147483647;
    // acks
    @UriParam(label = "producer", defaultValue = "1", enums = "all,-1,0,1")
    private String acks = "1";
    // compression.type
    @UriParam(label = "producer", defaultValue = "none")
    private String compressionType = "none";
    // batch.size
    @UriParam(label = "producer", defaultValue = "16384")
    private int batchSize = 16384;
    // linger.ms
    @UriParam(label = "producer", defaultValue = "0ms", javaType = "java.time.Duration")
    private long lingerMs = 0;
    // delivery.timeout.ms
    @UriParam(label = "producer", defaultValue = "2m", javaType = "java.time.Duration")
    private int deliveryTimeoutMs = 120000;
    // max.request.size
    @UriParam(label = "producer", defaultValue = "1048576")
    private int maxRequestSize = 1048576;
    // max.block.ms
    @UriParam(label = "producer", defaultValue = "1m", javaType = "java.time.Duration")
    private long maxBlockMs = 60000;
    // metadata.max.idle.ms
    @UriParam(label = "producer", defaultValue = "5m", javaType = "java.time.Duration")
    private long metadataMaxIdleMs = 300000;
    // max.in.flight.requests.per.connection
    @UriParam(label = "producer", defaultValue = "5")
    private int maxInFlightRequestsPerConnection = 5;
    // key.serializer
    @UriParam(label = "producer", defaultValue = "org.apache.kafka.common.serialization.StringSerializer")
    private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
    // value.serializer
    @UriParam(label = "producer", defaultValue = "org.apache.kafka.common.serialization.StringSerializer")
    private String valueSerializer = "org.apache.kafka.common.serialization.StringSerializer";
    // partitioner.class
    @UriParam(label = "producer", defaultValue = "org.apache.kafka.clients.producer.internals.DefaultPartitioner")
    private String partitionerClass = "org.apache.kafka.clients.producer.internals.DefaultPartitioner";
    // enable.idempotence
    @UriParam(label = "producer", defaultValue = "false")
    private boolean enableIdempotence = false;
    // transaction.timeout.ms
    @UriParam(label = "producer", defaultValue = "1m", javaType = "java.time.Duration")
    private int transactionTimeoutMs = 60000;
    // transactional.id
    @UriParam(label = "producer")
    private String transactionalId;

    /**
     * The partition to which the record will be sent (or null if no partition
     * was specified) or read from a particular partition if set. Header {@link
     * VertxKafkaConstants#PARTITION_ID} If configured,
     * it will take precedence over this config
     */
    public void setPartitionId(Integer partitionId) {
        this.partitionId = partitionId;
    }

    public Integer getPartitionId() {
        return partitionId;
    }

    /**
     * Name of the topic to use. On the consumer you can use comma to separate
     * multiple topics. A producer can only send
     * a message to a single topic.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * A list of host/port pairs to use for establishing the initial connection
     * to the Kafka cluster. The client will make use of all servers
     * irrespective of which servers are specified here for
     * bootstrapping&mdash;this list only impacts the initial hosts used to
     * discover the full set of servers. This list should be in the form
     * <code>host1:port1,host2:port2,...</code>. Since these servers are just
     * used for the initial connection to discover the full cluster membership
     * (which may change dynamically), this list need not contain the full set
     * of servers (you may want more than one, though, in case a server is
     * down).
     */
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Controls how the client uses DNS lookups. If set to
     * <code>use_all_dns_ips</code>, connect to each returned IP address in
     * sequence until a successful connection is established. After a
     * disconnection, the next IP is used. Once all IPs have been used once, the
     * client resolves the IP(s) from the hostname again (both the JVM and the
     * OS cache DNS name lookups, however). If set to
     * <code>resolve_canonical_bootstrap_servers_only</code>, resolve each
     * bootstrap address into a list of canonical names. After the bootstrap
     * phase, this behaves the same as <code>use_all_dns_ips</code>. If set to
     * <code>default</code> (deprecated), attempt to connect to the first IP
     * address returned by the lookup, even if the lookup returns multiple IP
     * addresses.
     */
    public void setClientDnsLookup(String clientDnsLookup) {
        this.clientDnsLookup = clientDnsLookup;
    }

    public String getClientDnsLookup() {
        return clientDnsLookup;
    }

    /**
     * The period of time in milliseconds after which we force a refresh of
     * metadata even if we haven't seen any partition leadership changes to
     * proactively discover any new brokers or partitions.
     */
    public void setMetadataMaxAgeMs(long metadataMaxAgeMs) {
        this.metadataMaxAgeMs = metadataMaxAgeMs;
    }

    public long getMetadataMaxAgeMs() {
        return metadataMaxAgeMs;
    }

    /**
     * An id string to pass to the server when making requests. The purpose of
     * this is to be able to track the source of requests beyond just ip/port by
     * allowing a logical application name to be included in server-side request
     * logging.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * The size of the TCP send buffer (SO_SNDBUF) to use when sending data. If
     * the value is -1, the OS default will be used.
     */
    public void setSendBufferBytes(int sendBufferBytes) {
        this.sendBufferBytes = sendBufferBytes;
    }

    public int getSendBufferBytes() {
        return sendBufferBytes;
    }

    /**
     * The size of the TCP receive buffer (SO_RCVBUF) to use when reading data.
     * If the value is -1, the OS default will be used.
     */
    public void setReceiveBufferBytes(int receiveBufferBytes) {
        this.receiveBufferBytes = receiveBufferBytes;
    }

    public int getReceiveBufferBytes() {
        return receiveBufferBytes;
    }

    /**
     * The base amount of time to wait before attempting to reconnect to a given
     * host. This avoids repeatedly connecting to a host in a tight loop. This
     * backoff applies to all connection attempts by the client to a broker.
     */
    public void setReconnectBackoffMs(long reconnectBackoffMs) {
        this.reconnectBackoffMs = reconnectBackoffMs;
    }

    public long getReconnectBackoffMs() {
        return reconnectBackoffMs;
    }

    /**
     * The maximum amount of time in milliseconds to wait when reconnecting to a
     * broker that has repeatedly failed to connect. If provided, the backoff
     * per host will increase exponentially for each consecutive connection
     * failure, up to this maximum. After calculating the backoff increase, 20%
     * random jitter is added to avoid connection storms.
     */
    public void setReconnectBackoffMaxMs(long reconnectBackoffMaxMs) {
        this.reconnectBackoffMaxMs = reconnectBackoffMaxMs;
    }

    public long getReconnectBackoffMaxMs() {
        return reconnectBackoffMaxMs;
    }

    /**
     * The amount of time to wait before attempting to retry a failed request to
     * a given topic partition. This avoids repeatedly sending requests in a
     * tight loop under some failure scenarios.
     */
    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    /**
     * The window of time a metrics sample is computed over.
     */
    public void setMetricsSampleWindowMs(long metricsSampleWindowMs) {
        this.metricsSampleWindowMs = metricsSampleWindowMs;
    }

    public long getMetricsSampleWindowMs() {
        return metricsSampleWindowMs;
    }

    /**
     * The number of samples maintained to compute metrics.
     */
    public void setMetricsNumSamples(int metricsNumSamples) {
        this.metricsNumSamples = metricsNumSamples;
    }

    public int getMetricsNumSamples() {
        return metricsNumSamples;
    }

    /**
     * The highest recording level for metrics.
     */
    public void setMetricsRecordingLevel(String metricsRecordingLevel) {
        this.metricsRecordingLevel = metricsRecordingLevel;
    }

    public String getMetricsRecordingLevel() {
        return metricsRecordingLevel;
    }

    /**
     * A list of classes to use as metrics reporters. Implementing the
     * <code>org.apache.kafka.common.metrics.MetricsReporter</code> interface
     * allows plugging in classes that will be notified of new metric creation.
     * The JmxReporter is always included to register JMX statistics.
     */
    public void setMetricReporters(String metricReporters) {
        this.metricReporters = metricReporters;
    }

    public String getMetricReporters() {
        return metricReporters;
    }

    /**
     * The configuration controls the maximum amount of time the client will
     * wait for the response of a request. If the response is not received
     * before the timeout elapses the client will resend the request if
     * necessary or fail the request if retries are exhausted. This should be
     * larger than <code>replica.lag.time.max.ms</code> (a broker configuration)
     * to reduce the possibility of message duplication due to unnecessary
     * producer retries.
     */
    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * The amount of time the client will wait for the socket connection to be
     * established. If the connection is not built before the timeout elapses,
     * clients will close the socket channel.
     */
    public void setSocketConnectionSetupTimeoutMs(
            long socketConnectionSetupTimeoutMs) {
        this.socketConnectionSetupTimeoutMs = socketConnectionSetupTimeoutMs;
    }

    public long getSocketConnectionSetupTimeoutMs() {
        return socketConnectionSetupTimeoutMs;
    }

    /**
     * The maximum amount of time the client will wait for the socket connection
     * to be established. The connection setup timeout will increase
     * exponentially for each consecutive connection failure up to this maximum.
     * To avoid connection storms, a randomization factor of 0.2 will be applied
     * to the timeout resulting in a random range between 20% below and 20%
     * above the computed value.
     */
    public void setSocketConnectionSetupTimeoutMaxMs(
            long socketConnectionSetupTimeoutMaxMs) {
        this.socketConnectionSetupTimeoutMaxMs = socketConnectionSetupTimeoutMaxMs;
    }

    public long getSocketConnectionSetupTimeoutMaxMs() {
        return socketConnectionSetupTimeoutMaxMs;
    }

    /**
     * Close idle connections after the number of milliseconds specified by this
     * config.
     */
    public void setConnectionsMaxIdleMs(long connectionsMaxIdleMs) {
        this.connectionsMaxIdleMs = connectionsMaxIdleMs;
    }

    public long getConnectionsMaxIdleMs() {
        return connectionsMaxIdleMs;
    }

    /**
     * A list of classes to use as interceptors. Implementing the
     * <code>org.apache.kafka.clients.producer.ProducerInterceptor</code>
     * interface allows you to intercept (and possibly mutate) the records
     * received by the producer before they are published to the Kafka cluster.
     * By default, there are no interceptors.
     */
    public void setInterceptorClasses(String interceptorClasses) {
        this.interceptorClasses = interceptorClasses;
    }

    public String getInterceptorClasses() {
        return interceptorClasses;
    }

    /**
     * A list of configurable creator classes each returning a provider
     * implementing security algorithms. These classes should implement the
     * <code>org.apache.kafka.common.security.auth.SecurityProviderCreator</code> interface.
     */
    public void setSecurityProviders(String securityProviders) {
        this.securityProviders = securityProviders;
    }

    public String getSecurityProviders() {
        return securityProviders;
    }

    /**
     * Protocol used to communicate with brokers. Valid values are: PLAINTEXT,
     * SSL, SASL_PLAINTEXT, SASL_SSL.
     */
    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
    }

    public String getSecurityProtocol() {
        return securityProtocol;
    }

    /**
     * The SSL protocol used to generate the SSLContext. The default is
     * 'TLSv1.3' when running with Java 11 or newer, 'TLSv1.2' otherwise. This
     * value should be fine for most use cases. Allowed values in recent JVMs
     * are 'TLSv1.2' and 'TLSv1.3'. 'TLS', 'TLSv1.1', 'SSL', 'SSLv2' and 'SSLv3'
     * may be supported in older JVMs, but their usage is discouraged due to
     * known security vulnerabilities. With the default value for this config
     * and 'ssl.enabled.protocols', clients will downgrade to 'TLSv1.2' if the
     * server does not support 'TLSv1.3'. If this config is set to 'TLSv1.2',
     * clients will not use 'TLSv1.3' even if it is one of the values in
     * ssl.enabled.protocols and the server only supports 'TLSv1.3'.
     */
    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    /**
     * The name of the security provider used for SSL connections. Default value
     * is the default security provider of the JVM.
     */
    public void setSslProvider(String sslProvider) {
        this.sslProvider = sslProvider;
    }

    public String getSslProvider() {
        return sslProvider;
    }

    /**
     * A list of cipher suites. This is a named combination of authentication,
     * encryption, MAC and key exchange algorithm used to negotiate the security
     * settings for a network connection using TLS or SSL network protocol. By
     * default all the available cipher suites are supported.
     */
    public void setSslCipherSuites(String sslCipherSuites) {
        this.sslCipherSuites = sslCipherSuites;
    }

    public String getSslCipherSuites() {
        return sslCipherSuites;
    }

    /**
     * The list of protocols enabled for SSL connections. The default is
     * 'TLSv1.2,TLSv1.3' when running with Java 11 or newer, 'TLSv1.2'
     * otherwise. With the default value for Java 11, clients and servers will
     * prefer TLSv1.3 if both support it and fallback to TLSv1.2 otherwise
     * (assuming both support at least TLSv1.2). This default should be fine for
     * most cases. Also see the config documentation for `ssl.protocol`.
     */
    public void setSslEnabledProtocols(String sslEnabledProtocols) {
        this.sslEnabledProtocols = sslEnabledProtocols;
    }

    public String getSslEnabledProtocols() {
        return sslEnabledProtocols;
    }

    /**
     * The file format of the key store file. This is optional for client.
     */
    public void setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    /**
     * The location of the key store file. This is optional for client and can
     * be used for two-way authentication for client.
     */
    public void setSslKeystoreLocation(String sslKeystoreLocation) {
        this.sslKeystoreLocation = sslKeystoreLocation;
    }

    public String getSslKeystoreLocation() {
        return sslKeystoreLocation;
    }

    /**
     * The store password for the key store file. This is optional for client
     * and only needed if 'ssl.keystore.location' is configured.  Key store
     * password is not supported for PEM format.
     */
    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    /**
     * The password of the private key in the key store file orthe PEM key
     * specified in `ssl.keystore.key'. This is required for clients only if
     * two-way authentication is configured.
     */
    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    /**
     * Private key in the format specified by 'ssl.keystore.type'. Default SSL
     * engine factory supports only PEM format with PKCS#8 keys. If the key is
     * encrypted, key password must be specified using 'ssl.key.password'
     */
    public void setSslKeystoreKey(String sslKeystoreKey) {
        this.sslKeystoreKey = sslKeystoreKey;
    }

    public String getSslKeystoreKey() {
        return sslKeystoreKey;
    }

    /**
     * Certificate chain in the format specified by 'ssl.keystore.type'. Default
     * SSL engine factory supports only PEM format with a list of X.509
     * certificates
     */
    public void setSslKeystoreCertificateChain(
            String sslKeystoreCertificateChain) {
        this.sslKeystoreCertificateChain = sslKeystoreCertificateChain;
    }

    public String getSslKeystoreCertificateChain() {
        return sslKeystoreCertificateChain;
    }

    /**
     * Trusted certificates in the format specified by 'ssl.truststore.type'.
     * Default SSL engine factory supports only PEM format with X.509
     * certificates.
     */
    public void setSslTruststoreCertificates(String sslTruststoreCertificates) {
        this.sslTruststoreCertificates = sslTruststoreCertificates;
    }

    public String getSslTruststoreCertificates() {
        return sslTruststoreCertificates;
    }

    /**
     * The file format of the trust store file.
     */
    public void setSslTruststoreType(String sslTruststoreType) {
        this.sslTruststoreType = sslTruststoreType;
    }

    public String getSslTruststoreType() {
        return sslTruststoreType;
    }

    /**
     * The location of the trust store file. 
     */
    public void setSslTruststoreLocation(String sslTruststoreLocation) {
        this.sslTruststoreLocation = sslTruststoreLocation;
    }

    public String getSslTruststoreLocation() {
        return sslTruststoreLocation;
    }

    /**
     * The password for the trust store file. If a password is not set, trust
     * store file configured will still be used, but integrity checking is
     * disabled. Trust store password is not supported for PEM format.
     */
    public void setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
    }

    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }

    /**
     * The algorithm used by key manager factory for SSL connections. Default
     * value is the key manager factory algorithm configured for the Java
     * Virtual Machine.
     */
    public void setSslKeymanagerAlgorithm(String sslKeymanagerAlgorithm) {
        this.sslKeymanagerAlgorithm = sslKeymanagerAlgorithm;
    }

    public String getSslKeymanagerAlgorithm() {
        return sslKeymanagerAlgorithm;
    }

    /**
     * The algorithm used by trust manager factory for SSL connections. Default
     * value is the trust manager factory algorithm configured for the Java
     * Virtual Machine.
     */
    public void setSslTrustmanagerAlgorithm(String sslTrustmanagerAlgorithm) {
        this.sslTrustmanagerAlgorithm = sslTrustmanagerAlgorithm;
    }

    public String getSslTrustmanagerAlgorithm() {
        return sslTrustmanagerAlgorithm;
    }

    /**
     * The endpoint identification algorithm to validate server hostname using
     * server certificate. 
     */
    public void setSslEndpointIdentificationAlgorithm(
            String sslEndpointIdentificationAlgorithm) {
        this.sslEndpointIdentificationAlgorithm = sslEndpointIdentificationAlgorithm;
    }

    public String getSslEndpointIdentificationAlgorithm() {
        return sslEndpointIdentificationAlgorithm;
    }

    /**
     * The SecureRandom PRNG implementation to use for SSL cryptography
     * operations. 
     */
    public void setSslSecureRandomImplementation(
            String sslSecureRandomImplementation) {
        this.sslSecureRandomImplementation = sslSecureRandomImplementation;
    }

    public String getSslSecureRandomImplementation() {
        return sslSecureRandomImplementation;
    }

    /**
     * The class of type org.apache.kafka.common.security.auth.SslEngineFactory
     * to provide SSLEngine objects. Default value is
     * org.apache.kafka.common.security.ssl.DefaultSslEngineFactory
     */
    public void setSslEngineFactoryClass(String sslEngineFactoryClass) {
        this.sslEngineFactoryClass = sslEngineFactoryClass;
    }

    public String getSslEngineFactoryClass() {
        return sslEngineFactoryClass;
    }

    /**
     * The Kerberos principal name that Kafka runs as. This can be defined
     * either in Kafka's JAAS config or in Kafka's config.
     */
    public void setSaslKerberosServiceName(String saslKerberosServiceName) {
        this.saslKerberosServiceName = saslKerberosServiceName;
    }

    public String getSaslKerberosServiceName() {
        return saslKerberosServiceName;
    }

    /**
     * Kerberos kinit command path.
     */
    public void setSaslKerberosKinitCmd(String saslKerberosKinitCmd) {
        this.saslKerberosKinitCmd = saslKerberosKinitCmd;
    }

    public String getSaslKerberosKinitCmd() {
        return saslKerberosKinitCmd;
    }

    /**
     * Login thread will sleep until the specified window factor of time from
     * last refresh to ticket's expiry has been reached, at which time it will
     * try to renew the ticket.
     */
    public void setSaslKerberosTicketRenewWindowFactor(
            double saslKerberosTicketRenewWindowFactor) {
        this.saslKerberosTicketRenewWindowFactor = saslKerberosTicketRenewWindowFactor;
    }

    public double getSaslKerberosTicketRenewWindowFactor() {
        return saslKerberosTicketRenewWindowFactor;
    }

    /**
     * Percentage of random jitter added to the renewal time.
     */
    public void setSaslKerberosTicketRenewJitter(
            double saslKerberosTicketRenewJitter) {
        this.saslKerberosTicketRenewJitter = saslKerberosTicketRenewJitter;
    }

    public double getSaslKerberosTicketRenewJitter() {
        return saslKerberosTicketRenewJitter;
    }

    /**
     * Login thread sleep time between refresh attempts.
     */
    public void setSaslKerberosMinTimeBeforeRelogin(
            long saslKerberosMinTimeBeforeRelogin) {
        this.saslKerberosMinTimeBeforeRelogin = saslKerberosMinTimeBeforeRelogin;
    }

    public long getSaslKerberosMinTimeBeforeRelogin() {
        return saslKerberosMinTimeBeforeRelogin;
    }

    /**
     * Login refresh thread will sleep until the specified window factor
     * relative to the credential's lifetime has been reached, at which time it
     * will try to refresh the credential. Legal values are between 0.5 (50%)
     * and 1.0 (100%) inclusive; a default value of 0.8 (80%) is used if no
     * value is specified. Currently applies only to OAUTHBEARER.
     */
    public void setSaslLoginRefreshWindowFactor(
            double saslLoginRefreshWindowFactor) {
        this.saslLoginRefreshWindowFactor = saslLoginRefreshWindowFactor;
    }

    public double getSaslLoginRefreshWindowFactor() {
        return saslLoginRefreshWindowFactor;
    }

    /**
     * The maximum amount of random jitter relative to the credential's lifetime
     * that is added to the login refresh thread's sleep time. Legal values are
     * between 0 and 0.25 (25%) inclusive; a default value of 0.05 (5%) is used
     * if no value is specified. Currently applies only to OAUTHBEARER.
     */
    public void setSaslLoginRefreshWindowJitter(
            double saslLoginRefreshWindowJitter) {
        this.saslLoginRefreshWindowJitter = saslLoginRefreshWindowJitter;
    }

    public double getSaslLoginRefreshWindowJitter() {
        return saslLoginRefreshWindowJitter;
    }

    /**
     * The desired minimum time for the login refresh thread to wait before
     * refreshing a credential, in seconds. Legal values are between 0 and 900
     * (15 minutes); a default value of 60 (1 minute) is used if no value is
     * specified.  This value and  sasl.login.refresh.buffer.seconds are both
     * ignored if their sum exceeds the remaining lifetime of a credential.
     * Currently applies only to OAUTHBEARER.
     */
    public void setSaslLoginRefreshMinPeriodSeconds(
            short saslLoginRefreshMinPeriodSeconds) {
        this.saslLoginRefreshMinPeriodSeconds = saslLoginRefreshMinPeriodSeconds;
    }

    public short getSaslLoginRefreshMinPeriodSeconds() {
        return saslLoginRefreshMinPeriodSeconds;
    }

    /**
     * The amount of buffer time before credential expiration to maintain when
     * refreshing a credential, in seconds. If a refresh would otherwise occur
     * closer to expiration than the number of buffer seconds then the refresh
     * will be moved up to maintain as much of the buffer time as possible.
     * Legal values are between 0 and 3600 (1 hour); a default value of  300 (5
     * minutes) is used if no value is specified. This value and
     * sasl.login.refresh.min.period.seconds are both ignored if their sum
     * exceeds the remaining lifetime of a credential. Currently applies only to
     * OAUTHBEARER.
     */
    public void setSaslLoginRefreshBufferSeconds(
            short saslLoginRefreshBufferSeconds) {
        this.saslLoginRefreshBufferSeconds = saslLoginRefreshBufferSeconds;
    }

    public short getSaslLoginRefreshBufferSeconds() {
        return saslLoginRefreshBufferSeconds;
    }

    /**
     * SASL mechanism used for client connections. This may be any mechanism for
     * which a security provider is available. GSSAPI is the default mechanism.
     */
    public void setSaslMechanism(String saslMechanism) {
        this.saslMechanism = saslMechanism;
    }

    public String getSaslMechanism() {
        return saslMechanism;
    }

    /**
     * JAAS login context parameters for SASL connections in the format used by
     * JAAS configuration files. JAAS configuration file format is described <a
     * href="http://docs.oracle.com/javase/8/docs/technotes/guides/security/jgss/tutorials/LoginConfigFile.html">here</a>. The format for the value is: <code>loginModuleClass controlFlag (optionName=optionValue)*;</code>. For brokers, the config must be prefixed with listener prefix and SASL mechanism name in lower-case. For example, listener.name.sasl_ssl.scram-sha-256.sasl.jaas.config=com.example.ScramLoginModule required;
     */
    public void setSaslJaasConfig(String saslJaasConfig) {
        this.saslJaasConfig = saslJaasConfig;
    }

    public String getSaslJaasConfig() {
        return saslJaasConfig;
    }

    /**
     * The fully qualified name of a SASL client callback handler class that
     * implements the AuthenticateCallbackHandler interface.
     */
    public void setSaslClientCallbackHandlerClass(
            String saslClientCallbackHandlerClass) {
        this.saslClientCallbackHandlerClass = saslClientCallbackHandlerClass;
    }

    public String getSaslClientCallbackHandlerClass() {
        return saslClientCallbackHandlerClass;
    }

    /**
     * The fully qualified name of a SASL login callback handler class that
     * implements the AuthenticateCallbackHandler interface. For brokers, login
     * callback handler config must be prefixed with listener prefix and SASL
     * mechanism name in lower-case. For example,
     * listener.name.sasl_ssl.scram-sha-256.sasl.login.callback.handler.class=com.example.CustomScramLoginCallbackHandler
     */
    public void setSaslLoginCallbackHandlerClass(
            String saslLoginCallbackHandlerClass) {
        this.saslLoginCallbackHandlerClass = saslLoginCallbackHandlerClass;
    }

    public String getSaslLoginCallbackHandlerClass() {
        return saslLoginCallbackHandlerClass;
    }

    /**
     * The fully qualified name of a class that implements the Login interface.
     * For brokers, login config must be prefixed with listener prefix and SASL
     * mechanism name in lower-case. For example,
     * listener.name.sasl_ssl.scram-sha-256.sasl.login.class=com.example.CustomScramLogin
     */
    public void setSaslLoginClass(String saslLoginClass) {
        this.saslLoginClass = saslLoginClass;
    }

    public String getSaslLoginClass() {
        return saslLoginClass;
    }

    /**
     * Sets additional properties for either kafka consumer or kafka producer in
     * case they can't be set directly on the camel configurations (e.g: new
     * Kafka properties that are not reflected yet in Camel configurations), the
     * properties have to be prefixed with `additionalProperties.`. E.g:
     * `additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://localhost:8811/avro`
     */
    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    /**
     * Set if KafkaConsumer will read from a particular offset on startup. This
     * config will take precedence over seekTo config
     */
    public void setSeekToOffset(Long seekToOffset) {
        this.seekToOffset = seekToOffset;
    }

    public Long getSeekToOffset() {
        return seekToOffset;
    }

    /**
     * Set if KafkaConsumer will read from beginning or end on startup:
     * beginning : read from beginning end : read from end.
     */
    public void setSeekToPosition(String seekToPosition) {
        this.seekToPosition = seekToPosition;
    }

    public String getSeekToPosition() {
        return seekToPosition;
    }

    /**
     * A unique string that identifies the consumer group this consumer belongs
     * to. This property is required if the consumer uses either the group
     * management functionality by using <code>subscribe(topic)</code> or the
     * Kafka-based offset management strategy.
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * A unique identifier of the consumer instance provided by the end user.
     * Only non-empty strings are permitted. If set, the consumer is treated as
     * a static member, which means that only one instance with this ID is
     * allowed in the consumer group at any time. This can be used in
     * combination with a larger session timeout to avoid group rebalances
     * caused by transient unavailability (e.g. process restarts). If not set,
     * the consumer will join the group as a dynamic member, which is the
     * traditional behavior.
     */
    public void setGroupInstanceId(String groupInstanceId) {
        this.groupInstanceId = groupInstanceId;
    }

    public String getGroupInstanceId() {
        return groupInstanceId;
    }

    /**
     * The timeout used to detect client failures when using Kafka's group
     * management facility. The client sends periodic heartbeats to indicate its
     * liveness to the broker. If no heartbeats are received by the broker
     * before the expiration of this session timeout, then the broker will
     * remove this client from the group and initiate a rebalance. Note that the
     * value must be in the allowable range as configured in the broker
     * configuration by <code>group.min.session.timeout.ms</code> and
     * <code>group.max.session.timeout.ms</code>.
     */
    public void setSessionTimeoutMs(int sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /**
     * The expected time between heartbeats to the consumer coordinator when
     * using Kafka's group management facilities. Heartbeats are used to ensure
     * that the consumer's session stays active and to facilitate rebalancing
     * when new consumers join or leave the group. The value must be set lower
     * than <code>session.timeout.ms</code>, but typically should be set no
     * higher than 1/3 of that value. It can be adjusted even lower to control
     * the expected time for normal rebalances.
     */
    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * A list of class names or class types, ordered by preference, of supported
     * partition assignment strategies that the client will use to distribute
     * partition ownership amongst consumer instances when group management is
     * used. Available options
     * are:<ul><li><code>org.apache.kafka.clients.consumer.RangeAssignor</code>:
     * The default assignor, which works on a per-topic
     * basis.</li><li><code>org.apache.kafka.clients.consumer.RoundRobinAssignor</code>: Assigns partitions to consumers in a round-robin fashion.</li><li><code>org.apache.kafka.clients.consumer.StickyAssignor</code>: Guarantees an assignment that is maximally balanced while preserving as many existing partition assignments as possible.</li><li><code>org.apache.kafka.clients.consumer.CooperativeStickyAssignor</code>: Follows the same StickyAssignor logic, but allows for cooperative rebalancing.</li></ul><p>Implementing the <code>org.apache.kafka.clients.consumer.ConsumerPartitionAssignor</code> interface allows you to plug in a custom assignment strategy.
     */
    public void setPartitionAssignmentStrategy(
            String partitionAssignmentStrategy) {
        this.partitionAssignmentStrategy = partitionAssignmentStrategy;
    }

    public String getPartitionAssignmentStrategy() {
        return partitionAssignmentStrategy;
    }

    /**
     * If true the consumer's offset will be periodically committed in the
     * background.
     */
    public void setEnableAutoCommit(boolean enableAutoCommit) {
        this.enableAutoCommit = enableAutoCommit;
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    /**
     * The frequency in milliseconds that the consumer offsets are
     * auto-committed to Kafka if <code>enable.auto.commit</code> is set to
     * <code>true</code>.
     */
    public void setAutoCommitIntervalMs(int autoCommitIntervalMs) {
        this.autoCommitIntervalMs = autoCommitIntervalMs;
    }

    public int getAutoCommitIntervalMs() {
        return autoCommitIntervalMs;
    }

    /**
     * A rack identifier for this client. This can be any string value which
     * indicates where this client is physically located. It corresponds with
     * the broker config 'broker.rack'
     */
    public void setClientRack(String clientRack) {
        this.clientRack = clientRack;
    }

    public String getClientRack() {
        return clientRack;
    }

    /**
     * The maximum amount of data per-partition the server will return. Records
     * are fetched in batches by the consumer. If the first record batch in the
     * first non-empty partition of the fetch is larger than this limit, the
     * batch will still be returned to ensure that the consumer can make
     * progress. The maximum record batch size accepted by the broker is defined
     * via <code>message.max.bytes</code> (broker config) or
     * <code>max.message.bytes</code> (topic config). See fetch.max.bytes for
     * limiting the consumer request size.
     */
    public void setMaxPartitionFetchBytes(int maxPartitionFetchBytes) {
        this.maxPartitionFetchBytes = maxPartitionFetchBytes;
    }

    public int getMaxPartitionFetchBytes() {
        return maxPartitionFetchBytes;
    }

    /**
     * The minimum amount of data the server should return for a fetch request.
     * If insufficient data is available the request will wait for that much
     * data to accumulate before answering the request. The default setting of 1
     * byte means that fetch requests are answered as soon as a single byte of
     * data is available or the fetch request times out waiting for data to
     * arrive. Setting this to something greater than 1 will cause the server to
     * wait for larger amounts of data to accumulate which can improve server
     * throughput a bit at the cost of some additional latency.
     */
    public void setFetchMinBytes(int fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    public int getFetchMinBytes() {
        return fetchMinBytes;
    }

    /**
     * The maximum amount of data the server should return for a fetch request.
     * Records are fetched in batches by the consumer, and if the first record
     * batch in the first non-empty partition of the fetch is larger than this
     * value, the record batch will still be returned to ensure that the
     * consumer can make progress. As such, this is not a absolute maximum. The
     * maximum record batch size accepted by the broker is defined via
     * <code>message.max.bytes</code> (broker config) or
     * <code>max.message.bytes</code> (topic config). Note that the consumer
     * performs multiple fetches in parallel.
     */
    public void setFetchMaxBytes(int fetchMaxBytes) {
        this.fetchMaxBytes = fetchMaxBytes;
    }

    public int getFetchMaxBytes() {
        return fetchMaxBytes;
    }

    /**
     * The maximum amount of time the server will block before answering the
     * fetch request if there isn't sufficient data to immediately satisfy the
     * requirement given by fetch.min.bytes.
     */
    public void setFetchMaxWaitMs(int fetchMaxWaitMs) {
        this.fetchMaxWaitMs = fetchMaxWaitMs;
    }

    public int getFetchMaxWaitMs() {
        return fetchMaxWaitMs;
    }

    /**
     * What to do when there is no initial offset in Kafka or if the current
     * offset does not exist any more on the server (e.g. because that data has
     * been deleted): <ul><li>earliest: automatically reset the offset to the
     * earliest offset<li>latest: automatically reset the offset to the latest
     * offset</li><li>none: throw exception to the consumer if no previous
     * offset is found for the consumer's group</li><li>anything else: throw
     * exception to the consumer.</li></ul>
     */
    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    /**
     * Automatically check the CRC32 of the records consumed. This ensures no
     * on-the-wire or on-disk corruption to the messages occurred. This check
     * adds some overhead, so it may be disabled in cases seeking extreme
     * performance.
     */
    public void setCheckCrcs(boolean checkCrcs) {
        this.checkCrcs = checkCrcs;
    }

    public boolean isCheckCrcs() {
        return checkCrcs;
    }

    /**
     * Deserializer class for key that implements the
     * <code>org.apache.kafka.common.serialization.Deserializer</code>
     * interface.
     */
    public void setKeyDeserializer(String keyDeserializer) {
        this.keyDeserializer = keyDeserializer;
    }

    public String getKeyDeserializer() {
        return keyDeserializer;
    }

    /**
     * Deserializer class for value that implements the
     * <code>org.apache.kafka.common.serialization.Deserializer</code>
     * interface.
     */
    public void setValueDeserializer(String valueDeserializer) {
        this.valueDeserializer = valueDeserializer;
    }

    public String getValueDeserializer() {
        return valueDeserializer;
    }

    /**
     * Specifies the timeout (in milliseconds) for client APIs. This
     * configuration is used as the default timeout for all client operations
     * that do not specify a <code>timeout</code> parameter.
     */
    public void setDefaultApiTimeoutMs(int defaultApiTimeoutMs) {
        this.defaultApiTimeoutMs = defaultApiTimeoutMs;
    }

    public int getDefaultApiTimeoutMs() {
        return defaultApiTimeoutMs;
    }

    /**
     * The maximum number of records returned in a single call to poll(). Note,
     * that <code>max.poll.records</code> does not impact the underlying
     * fetching behavior. The consumer will cache the records from each fetch
     * request and returns them incrementally from each poll.
     */
    public void setMaxPollRecords(int maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    /**
     * The maximum delay between invocations of poll() when using consumer group
     * management. This places an upper bound on the amount of time that the
     * consumer can be idle before fetching more records. If poll() is not
     * called before expiration of this timeout, then the consumer is considered
     * failed and the group will rebalance in order to reassign the partitions
     * to another member. For consumers using a non-null
     * <code>group.instance.id</code> which reach this timeout, partitions will
     * not be immediately reassigned. Instead, the consumer will stop sending
     * heartbeats and partitions will be reassigned after expiration of
     * <code>session.timeout.ms</code>. This mirrors the behavior of a static
     * consumer which has shutdown.
     */
    public void setMaxPollIntervalMs(int maxPollIntervalMs) {
        this.maxPollIntervalMs = maxPollIntervalMs;
    }

    public int getMaxPollIntervalMs() {
        return maxPollIntervalMs;
    }

    /**
     * Whether internal topics matching a subscribed pattern should be excluded
     * from the subscription. It is always possible to explicitly subscribe to
     * an internal topic.
     */
    public void setExcludeInternalTopics(boolean excludeInternalTopics) {
        this.excludeInternalTopics = excludeInternalTopics;
    }

    public boolean isExcludeInternalTopics() {
        return excludeInternalTopics;
    }

    /**
     * Controls how to read messages written transactionally. If set to
     * <code>read_committed</code>, consumer.poll() will only return
     * transactional messages which have been committed. If set to
     * <code>read_uncommitted</code> (the default), consumer.poll() will return
     * all messages, even transactional messages which have been aborted.
     * Non-transactional messages will be returned unconditionally in either
     * mode. <p>Messages will always be returned in offset order. Hence, in
     * <code>read_committed</code> mode, consumer.poll() will only return
     * messages up to the last stable offset (LSO), which is the one less than
     * the offset of the first open transaction. In particular any messages
     * appearing after messages belonging to ongoing transactions will be
     * withheld until the relevant transaction has been completed. As a result,
     * <code>read_committed</code> consumers will not be able to read up to the
     * high watermark when there are in flight transactions.</p><p> Further,
     * when in <code>read_committed</code> the seekToEnd method will return the
     * LSO
     */
    public void setIsolationLevel(String isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public String getIsolationLevel() {
        return isolationLevel;
    }

    /**
     * Allow automatic topic creation on the broker when subscribing to or
     * assigning a topic. A topic being subscribed to will be automatically
     * created only if the broker allows for it using
     * `auto.create.topics.enable` broker configuration. This configuration must
     * be set to `false` when using brokers older than 0.11.0
     */
    public void setAllowAutoCreateTopics(boolean allowAutoCreateTopics) {
        this.allowAutoCreateTopics = allowAutoCreateTopics;
    }

    public boolean isAllowAutoCreateTopics() {
        return allowAutoCreateTopics;
    }

    /**
     * The total bytes of memory the producer can use to buffer records waiting
     * to be sent to the server. If records are sent faster than they can be
     * delivered to the server the producer will block for
     * <code>max.block.ms</code> after which it will throw an exception.<p>This
     * setting should correspond roughly to the total memory the producer will
     * use, but is not a hard bound since not all memory the producer uses is
     * used for buffering. Some additional memory will be used for compression
     * (if compression is enabled) as well as for maintaining in-flight
     * requests.
     */
    public void setBufferMemory(long bufferMemory) {
        this.bufferMemory = bufferMemory;
    }

    public long getBufferMemory() {
        return bufferMemory;
    }

    /**
     * Setting a value greater than zero will cause the client to resend any
     * record whose send fails with a potentially transient error. Note that
     * this retry is no different than if the client resent the record upon
     * receiving the error. Allowing retries without setting
     * <code>max.in.flight.requests.per.connection</code> to 1 will potentially
     * change the ordering of records because if two batches are sent to a
     * single partition, and the first fails and is retried but the second
     * succeeds, then the records in the second batch may appear first. Note
     * additionally that produce requests will be failed before the number of
     * retries has been exhausted if the timeout configured by
     * <code>delivery.timeout.ms</code> expires first before successful
     * acknowledgement. Users should generally prefer to leave this config unset
     * and instead use <code>delivery.timeout.ms</code> to control retry
     * behavior.
     */
    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getRetries() {
        return retries;
    }

    /**
     * The number of acknowledgments the producer requires the leader to have
     * received before considering a request complete. This controls the
     * durability of records that are sent. The following settings are allowed:
     * <ul> <li><code>acks=0</code> If set to zero then the producer will not
     * wait for any acknowledgment from the server at all. The record will be
     * immediately added to the socket buffer and considered sent. No guarantee
     * can be made that the server has received the record in this case, and the
     * <code>retries</code> configuration will not take effect (as the client
     * won't generally know of any failures). The offset given back for each
     * record will always be set to <code>-1</code>. <li><code>acks=1</code>
     * This will mean the leader will write the record to its local log but will
     * respond without awaiting full acknowledgement from all followers. In this
     * case should the leader fail immediately after acknowledging the record
     * but before the followers have replicated it then the record will be lost.
     * <li><code>acks=all</code> This means the leader will wait for the full
     * set of in-sync replicas to acknowledge the record. This guarantees that
     * the record will not be lost as long as at least one in-sync replica
     * remains alive. This is the strongest available guarantee. This is
     * equivalent to the acks=-1 setting.</ul>
     */
    public void setAcks(String acks) {
        this.acks = acks;
    }

    public String getAcks() {
        return acks;
    }

    /**
     * The compression type for all data generated by the producer. The default
     * is none (i.e. no compression). Valid  values are <code>none</code>,
     * <code>gzip</code>, <code>snappy</code>, <code>lz4</code>, or
     * <code>zstd</code>. Compression is of full batches of data, so the
     * efficacy of batching will also impact the compression ratio (more
     * batching means better compression).
     */
    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }

    public String getCompressionType() {
        return compressionType;
    }

    /**
     * The producer will attempt to batch records together into fewer requests
     * whenever multiple records are being sent to the same partition. This
     * helps performance on both the client and the server. This configuration
     * controls the default batch size in bytes. <p>No attempt will be made to
     * batch records larger than this size. <p>Requests sent to brokers will
     * contain multiple batches, one for each partition with data available to
     * be sent. <p>A small batch size will make batching less common and may
     * reduce throughput (a batch size of zero will disable batching entirely).
     * A very large batch size may use memory a bit more wastefully as we will
     * always allocate a buffer of the specified batch size in anticipation of
     * additional records.
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * The producer groups together any records that arrive in between request
     * transmissions into a single batched request. Normally this occurs only
     * under load when records arrive faster than they can be sent out. However
     * in some circumstances the client may want to reduce the number of
     * requests even under moderate load. This setting accomplishes this by
     * adding a small amount of artificial delay&mdash;that is, rather than
     * immediately sending out a record the producer will wait for up to the
     * given delay to allow other records to be sent so that the sends can be
     * batched together. This can be thought of as analogous to Nagle's
     * algorithm in TCP. This setting gives the upper bound on the delay for
     * batching: once we get <code>batch.size</code> worth of records for a
     * partition it will be sent immediately regardless of this setting, however
     * if we have fewer than this many bytes accumulated for this partition we
     * will 'linger' for the specified time waiting for more records to show up.
     * This setting defaults to 0 (i.e. no delay). Setting
     * <code>linger.ms=5</code>, for example, would have the effect of reducing
     * the number of requests sent but would add up to 5ms of latency to records
     * sent in the absence of load.
     */
    public void setLingerMs(long lingerMs) {
        this.lingerMs = lingerMs;
    }

    public long getLingerMs() {
        return lingerMs;
    }

    /**
     * An upper bound on the time to report success or failure after a call to
     * <code>send()</code> returns. This limits the total time that a record
     * will be delayed prior to sending, the time to await acknowledgement from
     * the broker (if expected), and the time allowed for retriable send
     * failures. The producer may report failure to send a record earlier than
     * this config if either an unrecoverable error is encountered, the retries
     * have been exhausted, or the record is added to a batch which reached an
     * earlier delivery expiration deadline. The value of this config should be
     * greater than or equal to the sum of <code>request.timeout.ms</code> and
     * <code>linger.ms</code>.
     */
    public void setDeliveryTimeoutMs(int deliveryTimeoutMs) {
        this.deliveryTimeoutMs = deliveryTimeoutMs;
    }

    public int getDeliveryTimeoutMs() {
        return deliveryTimeoutMs;
    }

    /**
     * The maximum size of a request in bytes. This setting will limit the
     * number of record batches the producer will send in a single request to
     * avoid sending huge requests. This is also effectively a cap on the
     * maximum uncompressed record batch size. Note that the server has its own
     * cap on the record batch size (after compression if compression is
     * enabled) which may be different from this.
     */
    public void setMaxRequestSize(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public int getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * The configuration controls how long the <code>KafkaProducer</code>'s
     * <code>send()</code>, <code>partitionsFor()</code>,
     * <code>initTransactions()</code>, <code>sendOffsetsToTransaction()</code>,
     * <code>commitTransaction()</code> and <code>abortTransaction()</code>
     * methods will block. For <code>send()</code> this timeout bounds the total
     * time waiting for both metadata fetch and buffer allocation (blocking in
     * the user-supplied serializers or partitioner is not counted against this
     * timeout). For <code>partitionsFor()</code> this timeout bounds the time
     * spent waiting for metadata if it is unavailable. The transaction-related
     * methods always block, but may timeout if the transaction coordinator
     * could not be discovered or did not respond within the timeout.
     */
    public void setMaxBlockMs(long maxBlockMs) {
        this.maxBlockMs = maxBlockMs;
    }

    public long getMaxBlockMs() {
        return maxBlockMs;
    }

    /**
     * Controls how long the producer will cache metadata for a topic that's
     * idle. If the elapsed time since a topic was last produced to exceeds the
     * metadata idle duration, then the topic's metadata is forgotten and the
     * next access to it will force a metadata fetch request.
     */
    public void setMetadataMaxIdleMs(long metadataMaxIdleMs) {
        this.metadataMaxIdleMs = metadataMaxIdleMs;
    }

    public long getMetadataMaxIdleMs() {
        return metadataMaxIdleMs;
    }

    /**
     * The maximum number of unacknowledged requests the client will send on a
     * single connection before blocking. Note that if this setting is set to be
     * greater than 1 and there are failed sends, there is a risk of message
     * re-ordering due to retries (i.e., if retries are enabled).
     */
    public void setMaxInFlightRequestsPerConnection(
            int maxInFlightRequestsPerConnection) {
        this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
    }

    public int getMaxInFlightRequestsPerConnection() {
        return maxInFlightRequestsPerConnection;
    }

    /**
     * Serializer class for key that implements the
     * <code>org.apache.kafka.common.serialization.Serializer</code> interface.
     */
    public void setKeySerializer(String keySerializer) {
        this.keySerializer = keySerializer;
    }

    public String getKeySerializer() {
        return keySerializer;
    }

    /**
     * Serializer class for value that implements the
     * <code>org.apache.kafka.common.serialization.Serializer</code> interface.
     */
    public void setValueSerializer(String valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    public String getValueSerializer() {
        return valueSerializer;
    }

    /**
     * Partitioner class that implements the
     * <code>org.apache.kafka.clients.producer.Partitioner</code> interface.
     */
    public void setPartitionerClass(String partitionerClass) {
        this.partitionerClass = partitionerClass;
    }

    public String getPartitionerClass() {
        return partitionerClass;
    }

    /**
     * When set to 'true', the producer will ensure that exactly one copy of
     * each message is written in the stream. If 'false', producer retries due
     * to broker failures, etc., may write duplicates of the retried message in
     * the stream. Note that enabling idempotence requires
     * <code>max.in.flight.requests.per.connection</code> to be less than or
     * equal to 5, <code>retries</code> to be greater than 0 and
     * <code>acks</code> must be 'all'. If these values are not explicitly set
     * by the user, suitable values will be chosen. If incompatible values are
     * set, a <code>ConfigException</code> will be thrown.
     */
    public void setEnableIdempotence(boolean enableIdempotence) {
        this.enableIdempotence = enableIdempotence;
    }

    public boolean isEnableIdempotence() {
        return enableIdempotence;
    }

    /**
     * The maximum amount of time in ms that the transaction coordinator will
     * wait for a transaction status update from the producer before proactively
     * aborting the ongoing transaction.If this value is larger than the
     * transaction.max.timeout.ms setting in the broker, the request will fail
     * with a <code>InvalidTxnTimeoutException</code> error.
     */
    public void setTransactionTimeoutMs(int transactionTimeoutMs) {
        this.transactionTimeoutMs = transactionTimeoutMs;
    }

    public int getTransactionTimeoutMs() {
        return transactionTimeoutMs;
    }

    /**
     * The TransactionalId to use for transactional delivery. This enables
     * reliability semantics which span multiple producer sessions since it
     * allows the client to guarantee that transactions using the same
     * TransactionalId have been completed prior to starting any new
     * transactions. If no TransactionalId is provided, then the producer is
     * limited to idempotent delivery. If a TransactionalId is configured,
     * <code>enable.idempotence</code> is implied. By default the TransactionId
     * is not configured, which means transactions cannot be used. Note that, by
     * default, transactions require a cluster of at least three brokers which
     * is the recommended setting for production; for development you can change
     * this, by adjusting broker setting
     * <code>transaction.state.log.replication.factor</code>.
     */
    public void setTransactionalId(String transactionalId) {
        this.transactionalId = transactionalId;
    }

    public String getTransactionalId() {
        return transactionalId;
    }

    public Properties createConsumerConfiguration() {
        final Properties props = new Properties();
        addPropertyIfNotNull(props, "partition.id", partitionId);
        addPropertyIfNotNull(props, "topic", topic);
        addPropertyIfNotNull(props, "bootstrap.servers", bootstrapServers);
        addPropertyIfNotNull(props, "client.dns.lookup", clientDnsLookup);
        addPropertyIfNotNull(props, "metadata.max.age.ms", metadataMaxAgeMs);
        addPropertyIfNotNull(props, "client.id", clientId);
        addPropertyIfNotNull(props, "send.buffer.bytes", sendBufferBytes);
        addPropertyIfNotNull(props, "receive.buffer.bytes", receiveBufferBytes);
        addPropertyIfNotNull(props, "reconnect.backoff.ms", reconnectBackoffMs);
        addPropertyIfNotNull(props, "reconnect.backoff.max.ms", reconnectBackoffMaxMs);
        addPropertyIfNotNull(props, "retry.backoff.ms", retryBackoffMs);
        addPropertyIfNotNull(props, "metrics.sample.window.ms", metricsSampleWindowMs);
        addPropertyIfNotNull(props, "metrics.num.samples", metricsNumSamples);
        addPropertyIfNotNull(props, "metrics.recording.level", metricsRecordingLevel);
        addPropertyIfNotNull(props, "metric.reporters", metricReporters);
        addPropertyIfNotNull(props, "request.timeout.ms", requestTimeoutMs);
        addPropertyIfNotNull(props, "socket.connection.setup.timeout.ms", socketConnectionSetupTimeoutMs);
        addPropertyIfNotNull(props, "socket.connection.setup.timeout.max.ms", socketConnectionSetupTimeoutMaxMs);
        addPropertyIfNotNull(props, "connections.max.idle.ms", connectionsMaxIdleMs);
        addPropertyIfNotNull(props, "interceptor.classes", interceptorClasses);
        addPropertyIfNotNull(props, "security.providers", securityProviders);
        addPropertyIfNotNull(props, "security.protocol", securityProtocol);
        addPropertyIfNotNull(props, "ssl.protocol", sslProtocol);
        addPropertyIfNotNull(props, "ssl.provider", sslProvider);
        addPropertyIfNotNull(props, "ssl.cipher.suites", sslCipherSuites);
        addPropertyIfNotNull(props, "ssl.enabled.protocols", sslEnabledProtocols);
        addPropertyIfNotNull(props, "ssl.keystore.type", sslKeystoreType);
        addPropertyIfNotNull(props, "ssl.keystore.location", sslKeystoreLocation);
        addPropertyIfNotNull(props, "ssl.keystore.password", sslKeystorePassword);
        addPropertyIfNotNull(props, "ssl.key.password", sslKeyPassword);
        addPropertyIfNotNull(props, "ssl.keystore.key", sslKeystoreKey);
        addPropertyIfNotNull(props, "ssl.keystore.certificate.chain", sslKeystoreCertificateChain);
        addPropertyIfNotNull(props, "ssl.truststore.certificates", sslTruststoreCertificates);
        addPropertyIfNotNull(props, "ssl.truststore.type", sslTruststoreType);
        addPropertyIfNotNull(props, "ssl.truststore.location", sslTruststoreLocation);
        addPropertyIfNotNull(props, "ssl.truststore.password", sslTruststorePassword);
        addPropertyIfNotNull(props, "ssl.keymanager.algorithm", sslKeymanagerAlgorithm);
        addPropertyIfNotNull(props, "ssl.trustmanager.algorithm", sslTrustmanagerAlgorithm);
        addPropertyIfNotNull(props, "ssl.endpoint.identification.algorithm", sslEndpointIdentificationAlgorithm);
        addPropertyIfNotNull(props, "ssl.secure.random.implementation", sslSecureRandomImplementation);
        addPropertyIfNotNull(props, "ssl.engine.factory.class", sslEngineFactoryClass);
        addPropertyIfNotNull(props, "sasl.kerberos.service.name", saslKerberosServiceName);
        addPropertyIfNotNull(props, "sasl.kerberos.kinit.cmd", saslKerberosKinitCmd);
        addPropertyIfNotNull(props, "sasl.kerberos.ticket.renew.window.factor", saslKerberosTicketRenewWindowFactor);
        addPropertyIfNotNull(props, "sasl.kerberos.ticket.renew.jitter", saslKerberosTicketRenewJitter);
        addPropertyIfNotNull(props, "sasl.kerberos.min.time.before.relogin", saslKerberosMinTimeBeforeRelogin);
        addPropertyIfNotNull(props, "sasl.login.refresh.window.factor", saslLoginRefreshWindowFactor);
        addPropertyIfNotNull(props, "sasl.login.refresh.window.jitter", saslLoginRefreshWindowJitter);
        addPropertyIfNotNull(props, "sasl.login.refresh.min.period.seconds", saslLoginRefreshMinPeriodSeconds);
        addPropertyIfNotNull(props, "sasl.login.refresh.buffer.seconds", saslLoginRefreshBufferSeconds);
        addPropertyIfNotNull(props, "sasl.mechanism", saslMechanism);
        addPropertyIfNotNull(props, "sasl.jaas.config", saslJaasConfig);
        addPropertyIfNotNull(props, "sasl.client.callback.handler.class", saslClientCallbackHandlerClass);
        addPropertyIfNotNull(props, "sasl.login.callback.handler.class", saslLoginCallbackHandlerClass);
        addPropertyIfNotNull(props, "sasl.login.class", saslLoginClass);
        addPropertyIfNotNull(props, "seek.to.offset", seekToOffset);
        addPropertyIfNotNull(props, "seek.to.position", seekToPosition);
        addPropertyIfNotNull(props, "group.id", groupId);
        addPropertyIfNotNull(props, "group.instance.id", groupInstanceId);
        addPropertyIfNotNull(props, "session.timeout.ms", sessionTimeoutMs);
        addPropertyIfNotNull(props, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(props, "partition.assignment.strategy", partitionAssignmentStrategy);
        addPropertyIfNotNull(props, "enable.auto.commit", enableAutoCommit);
        addPropertyIfNotNull(props, "auto.commit.interval.ms", autoCommitIntervalMs);
        addPropertyIfNotNull(props, "client.rack", clientRack);
        addPropertyIfNotNull(props, "max.partition.fetch.bytes", maxPartitionFetchBytes);
        addPropertyIfNotNull(props, "fetch.min.bytes", fetchMinBytes);
        addPropertyIfNotNull(props, "fetch.max.bytes", fetchMaxBytes);
        addPropertyIfNotNull(props, "fetch.max.wait.ms", fetchMaxWaitMs);
        addPropertyIfNotNull(props, "auto.offset.reset", autoOffsetReset);
        addPropertyIfNotNull(props, "check.crcs", checkCrcs);
        addPropertyIfNotNull(props, "key.deserializer", keyDeserializer);
        addPropertyIfNotNull(props, "value.deserializer", valueDeserializer);
        addPropertyIfNotNull(props, "default.api.timeout.ms", defaultApiTimeoutMs);
        addPropertyIfNotNull(props, "max.poll.records", maxPollRecords);
        addPropertyIfNotNull(props, "max.poll.interval.ms", maxPollIntervalMs);
        addPropertyIfNotNull(props, "exclude.internal.topics", excludeInternalTopics);
        addPropertyIfNotNull(props, "isolation.level", isolationLevel);
        addPropertyIfNotNull(props, "allow.auto.create.topics", allowAutoCreateTopics);
        applyAdditionalProperties(props, getAdditionalProperties());
        return props;
    }

    public Properties createProducerConfiguration() {
        final Properties props = new Properties();
        addPropertyIfNotNull(props, "partition.id", partitionId);
        addPropertyIfNotNull(props, "topic", topic);
        addPropertyIfNotNull(props, "bootstrap.servers", bootstrapServers);
        addPropertyIfNotNull(props, "client.dns.lookup", clientDnsLookup);
        addPropertyIfNotNull(props, "metadata.max.age.ms", metadataMaxAgeMs);
        addPropertyIfNotNull(props, "client.id", clientId);
        addPropertyIfNotNull(props, "send.buffer.bytes", sendBufferBytes);
        addPropertyIfNotNull(props, "receive.buffer.bytes", receiveBufferBytes);
        addPropertyIfNotNull(props, "reconnect.backoff.ms", reconnectBackoffMs);
        addPropertyIfNotNull(props, "reconnect.backoff.max.ms", reconnectBackoffMaxMs);
        addPropertyIfNotNull(props, "retry.backoff.ms", retryBackoffMs);
        addPropertyIfNotNull(props, "metrics.sample.window.ms", metricsSampleWindowMs);
        addPropertyIfNotNull(props, "metrics.num.samples", metricsNumSamples);
        addPropertyIfNotNull(props, "metrics.recording.level", metricsRecordingLevel);
        addPropertyIfNotNull(props, "metric.reporters", metricReporters);
        addPropertyIfNotNull(props, "request.timeout.ms", requestTimeoutMs);
        addPropertyIfNotNull(props, "socket.connection.setup.timeout.ms", socketConnectionSetupTimeoutMs);
        addPropertyIfNotNull(props, "socket.connection.setup.timeout.max.ms", socketConnectionSetupTimeoutMaxMs);
        addPropertyIfNotNull(props, "connections.max.idle.ms", connectionsMaxIdleMs);
        addPropertyIfNotNull(props, "interceptor.classes", interceptorClasses);
        addPropertyIfNotNull(props, "security.providers", securityProviders);
        addPropertyIfNotNull(props, "security.protocol", securityProtocol);
        addPropertyIfNotNull(props, "ssl.protocol", sslProtocol);
        addPropertyIfNotNull(props, "ssl.provider", sslProvider);
        addPropertyIfNotNull(props, "ssl.cipher.suites", sslCipherSuites);
        addPropertyIfNotNull(props, "ssl.enabled.protocols", sslEnabledProtocols);
        addPropertyIfNotNull(props, "ssl.keystore.type", sslKeystoreType);
        addPropertyIfNotNull(props, "ssl.keystore.location", sslKeystoreLocation);
        addPropertyIfNotNull(props, "ssl.keystore.password", sslKeystorePassword);
        addPropertyIfNotNull(props, "ssl.key.password", sslKeyPassword);
        addPropertyIfNotNull(props, "ssl.keystore.key", sslKeystoreKey);
        addPropertyIfNotNull(props, "ssl.keystore.certificate.chain", sslKeystoreCertificateChain);
        addPropertyIfNotNull(props, "ssl.truststore.certificates", sslTruststoreCertificates);
        addPropertyIfNotNull(props, "ssl.truststore.type", sslTruststoreType);
        addPropertyIfNotNull(props, "ssl.truststore.location", sslTruststoreLocation);
        addPropertyIfNotNull(props, "ssl.truststore.password", sslTruststorePassword);
        addPropertyIfNotNull(props, "ssl.keymanager.algorithm", sslKeymanagerAlgorithm);
        addPropertyIfNotNull(props, "ssl.trustmanager.algorithm", sslTrustmanagerAlgorithm);
        addPropertyIfNotNull(props, "ssl.endpoint.identification.algorithm", sslEndpointIdentificationAlgorithm);
        addPropertyIfNotNull(props, "ssl.secure.random.implementation", sslSecureRandomImplementation);
        addPropertyIfNotNull(props, "ssl.engine.factory.class", sslEngineFactoryClass);
        addPropertyIfNotNull(props, "sasl.kerberos.service.name", saslKerberosServiceName);
        addPropertyIfNotNull(props, "sasl.kerberos.kinit.cmd", saslKerberosKinitCmd);
        addPropertyIfNotNull(props, "sasl.kerberos.ticket.renew.window.factor", saslKerberosTicketRenewWindowFactor);
        addPropertyIfNotNull(props, "sasl.kerberos.ticket.renew.jitter", saslKerberosTicketRenewJitter);
        addPropertyIfNotNull(props, "sasl.kerberos.min.time.before.relogin", saslKerberosMinTimeBeforeRelogin);
        addPropertyIfNotNull(props, "sasl.login.refresh.window.factor", saslLoginRefreshWindowFactor);
        addPropertyIfNotNull(props, "sasl.login.refresh.window.jitter", saslLoginRefreshWindowJitter);
        addPropertyIfNotNull(props, "sasl.login.refresh.min.period.seconds", saslLoginRefreshMinPeriodSeconds);
        addPropertyIfNotNull(props, "sasl.login.refresh.buffer.seconds", saslLoginRefreshBufferSeconds);
        addPropertyIfNotNull(props, "sasl.mechanism", saslMechanism);
        addPropertyIfNotNull(props, "sasl.jaas.config", saslJaasConfig);
        addPropertyIfNotNull(props, "sasl.client.callback.handler.class", saslClientCallbackHandlerClass);
        addPropertyIfNotNull(props, "sasl.login.callback.handler.class", saslLoginCallbackHandlerClass);
        addPropertyIfNotNull(props, "sasl.login.class", saslLoginClass);
        addPropertyIfNotNull(props, "buffer.memory", bufferMemory);
        addPropertyIfNotNull(props, "retries", retries);
        addPropertyIfNotNull(props, "acks", acks);
        addPropertyIfNotNull(props, "compression.type", compressionType);
        addPropertyIfNotNull(props, "batch.size", batchSize);
        addPropertyIfNotNull(props, "linger.ms", lingerMs);
        addPropertyIfNotNull(props, "delivery.timeout.ms", deliveryTimeoutMs);
        addPropertyIfNotNull(props, "max.request.size", maxRequestSize);
        addPropertyIfNotNull(props, "max.block.ms", maxBlockMs);
        addPropertyIfNotNull(props, "metadata.max.idle.ms", metadataMaxIdleMs);
        addPropertyIfNotNull(props, "max.in.flight.requests.per.connection", maxInFlightRequestsPerConnection);
        addPropertyIfNotNull(props, "key.serializer", keySerializer);
        addPropertyIfNotNull(props, "value.serializer", valueSerializer);
        addPropertyIfNotNull(props, "partitioner.class", partitionerClass);
        addPropertyIfNotNull(props, "enable.idempotence", enableIdempotence);
        addPropertyIfNotNull(props, "transaction.timeout.ms", transactionTimeoutMs);
        addPropertyIfNotNull(props, "transactional.id", transactionalId);
        applyAdditionalProperties(props, getAdditionalProperties());
        return props;
    }

    public VertxKafkaConfiguration copy() {
        try {
        	return (VertxKafkaConfiguration) clone();
        } catch (CloneNotSupportedException e) {
        	throw new RuntimeCamelException(e);
        }
    }

    private void applyAdditionalProperties(
            Properties props,
            Map<String, Object> additionalProperties) {
        if (!ObjectHelper.isEmpty(getAdditionalProperties())) {
        	additionalProperties.forEach((property, value) -> addPropertyIfNotNull(props, property, value));
        }
    }

    private static <T> void addPropertyIfNotNull(
            Properties props,
            String key,
            T value) {
        if (value != null) {
        	props.put(key, value.toString());
        }
    }
}