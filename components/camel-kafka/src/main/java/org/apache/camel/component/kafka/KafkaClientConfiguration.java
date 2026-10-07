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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.kafka.security.KafkaAuthType;
import org.apache.camel.component.kafka.security.KafkaSecurityConfigurer;
import org.apache.camel.component.kafka.serde.DefaultKafkaHeaderDeserializer;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.jsse.CipherSuitesParameters;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SecureSocketProtocolsParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;

/**
 * The options shared by every Kafka client that Camel creates: the brokers, the client id, the connection, metrics and
 * backoff settings, the security (SSL, SASL, Kerberos, OAuth) settings, the deserializers and header handling, and the
 * additional properties.
 * <p/>
 * A configuration for a specific client extends this class with the options of that client, and builds the client
 * properties with {@link #applyCommonClientProperties(Properties)}, {@link #applySecurityProperties(Properties)} and
 * {@link #applyAdditionalProperties(Properties)}.
 */
@UriParams
public abstract class KafkaClientConfiguration implements Cloneable, HeaderFilterStrategyAware {

    @UriParam(label = "common")
    private String brokers;
    @UriParam(label = "common")
    private String clientId;
    @UriParam(label = "common",
              description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy = new KafkaHeaderFilterStrategy();
    @UriParam(label = "common", defaultValue = "100")
    private Integer retryBackoffMs = 100;
    @UriParam(label = "common", defaultValue = "1000")
    private Integer retryBackoffMaxMs = 1000;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean preValidateHostAndPort = true;
    @UriParam(label = "consumer", description = "To use a custom KafkaHeaderDeserializer to deserialize kafka headers values")
    private KafkaHeaderDeserializer headerDeserializer = new DefaultKafkaHeaderDeserializer();
    // key.deserializer
    @UriParam(label = "consumer", defaultValue = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER)
    private String keyDeserializer = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER;
    // value.deserializer
    @UriParam(label = "consumer", defaultValue = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER)
    private String valueDeserializer = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER;
    // connections.max.idle.ms
    @UriParam(label = "common", defaultValue = "540000")
    private Integer connectionMaxIdleMs = 540000;
    // receive.buffer.bytes
    @UriParam(label = "common", defaultValue = "65536")
    private Integer receiveBufferBytes = 65536;
    // send.buffer.bytes
    @UriParam(label = "common", defaultValue = "131072")
    private Integer sendBufferBytes = 131072;
    // metadata.max.age.ms
    @UriParam(label = "common", defaultValue = "300000")
    private Integer metadataMaxAgeMs = 300000;
    // metric.reporters
    @UriParam(label = "common")
    private String metricReporters;
    // metrics.num.samples
    @UriParam(label = "common", defaultValue = "2")
    private Integer noOfMetricsSample = 2;
    // metrics.sample.window.ms
    @UriParam(label = "common", defaultValue = "30000")
    private Integer metricsSampleWindowMs = 30000;
    // reconnect.backoff.ms
    @UriParam(label = "common", defaultValue = "50")
    private Integer reconnectBackoffMs = 50;
    // reconnect.backoff.max.ms
    @UriParam(label = "common", defaultValue = "1000")
    private Integer reconnectBackoffMaxMs = 1000;
    // SSL
    @UriParam(label = "common,security")
    private SSLContextParameters sslContextParameters;
    // SSL
    // ssl.key.password
    @UriParam(label = "common,security", security = "secret")
    private String sslKeyPassword;
    // ssl.keystore.location
    @UriParam(label = "common,security")
    private String sslKeystoreLocation;
    // ssl.keystore.password
    @UriParam(label = "common,security", security = "secret")
    private String sslKeystorePassword;
    // ssl.truststore.location
    @UriParam(label = "common,security")
    private String sslTruststoreLocation;
    // ssl.truststore.password
    @UriParam(label = "common,security", security = "secret")
    private String sslTruststorePassword;
    // SSL
    // ssl.enabled.protocols
    @UriParam(label = "common,security")
    private String sslEnabledProtocols = SslConfigs.DEFAULT_SSL_ENABLED_PROTOCOLS;
    // ssl.keystore.type
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE)
    private String sslKeystoreType = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE;
    // ssl.protocol
    @UriParam(label = "common,security")
    private String sslProtocol = SslConfigs.DEFAULT_SSL_PROTOCOL;
    // ssl.provider
    @UriParam(label = "common,security")
    private String sslProvider;
    // ssl.truststore.type
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE)
    private String sslTruststoreType = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE;
    // SSL
    // ssl.cipher.suites
    @UriParam(label = "common,security")
    private String sslCipherSuites;
    // ssl.endpoint.identification.algorithm
    @UriParam(label = "common,security", defaultValue = "https", security = "insecure:ssl", insecureValue = "none")
    private String sslEndpointAlgorithm = SslConfigs.DEFAULT_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM;
    // ssl.keymanager.algorithm
    @UriParam(label = "common,security", defaultValue = "SunX509")
    private String sslKeymanagerAlgorithm = "SunX509";
    // ssl.trustmanager.algorithm
    @UriParam(label = "common,security", defaultValue = "PKIX")
    private String sslTrustmanagerAlgorithm = "PKIX";
    // SASL & sucurity Protocol
    // sasl.kerberos.service.name
    @UriParam(label = "common,security")
    private String saslKerberosServiceName;
    // security.protocol
    @UriParam(label = "common,security", defaultValue = CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL)
    private String securityProtocol = CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL;
    // SASL
    // sasl.mechanism
    @UriParam(label = "common,security", defaultValue = SaslConfigs.DEFAULT_SASL_MECHANISM)
    private String saslMechanism = SaslConfigs.DEFAULT_SASL_MECHANISM;
    // sasl.kerberos.kinit.cmd
    @UriParam(label = "common,security", defaultValue = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD)
    private String kerberosInitCmd = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD;
    // sasl.kerberos.min.time.before.relogin
    @UriParam(label = "common,security", defaultValue = "60000")
    private Integer kerberosBeforeReloginMinTime = 60000;
    // sasl.kerberos.ticket.renew.jitter
    @UriParam(label = "common,security", defaultValue = "0.05")
    private Double kerberosRenewJitter = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_JITTER;
    // sasl.kerberos.ticket.renew.window.factor
    @UriParam(label = "common,security", defaultValue = "0.8")
    private Double kerberosRenewWindowFactor = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_WINDOW_FACTOR;
    @UriParam(label = "common,security", defaultValue = "DEFAULT")
    // sasl.kerberos.principal.to.local.rules
    private String kerberosPrincipalToLocalRules;
    @UriParam(label = "common,security", security = "secret")
    // sasl.jaas.config
    private String saslJaasConfig;
    // Simplified authentication configuration
    @UriParam(label = "common,security",
              enums = "NONE,PLAIN,SCRAM_SHA_256,SCRAM_SHA_512,SSL,OAUTH,AWS_MSK_IAM,KERBEROS",
              description = "Simplified authentication type to use. This provides an easier way to configure Kafka "
                            + "authentication without manually setting securityProtocol, saslMechanism, and saslJaasConfig. "
                            + "When set, the appropriate security settings are automatically derived. "
                            + "Note: This is optional. You can still use the traditional approach with explicit "
                            + "securityProtocol, saslMechanism, and saslJaasConfig properties.")
    private KafkaAuthType saslAuthType;
    @UriParam(label = "common,security",
              description = "Username for SASL authentication. Used when saslAuthType is set to PLAIN, SCRAM_SHA_256, or SCRAM_SHA_512.")
    private String saslUsername;
    @UriParam(label = "common,security", security = "secret",
              description = "Password for SASL authentication. Used when saslAuthType is set to PLAIN, SCRAM_SHA_256, or SCRAM_SHA_512.")
    private String saslPassword;
    @UriParam(label = "common,security",
              description = "OAuth client ID. Used when saslAuthType is set to OAUTH.")
    private String oauthClientId;
    @UriParam(label = "common,security", security = "secret",
              description = "OAuth client secret. Used when saslAuthType is set to OAUTH.")
    private String oauthClientSecret;
    @UriParam(label = "common,security",
              description = "OAuth token endpoint URI. Used when saslAuthType is set to OAUTH.")
    private String oauthTokenEndpointUri;
    @UriParam(label = "common,security",
              description = "OAuth scope. Used when saslAuthType is set to OAUTH.")
    private String oauthScope;
    // Schema registry only options
    @UriParam(label = "schema")
    private String schemaRegistryURL;
    @UriParam(label = "schema,consumer")
    private boolean specificAvroReader;
    // Additional properties
    @UriParam(label = "common", prefix = "additionalProperties.", multiValue = true)
    private Map<String, Object> additionalProperties = new HashMap<>();
    @UriParam(label = "common", defaultValue = "30000")
    private int shutdownTimeout = 30000;
    @UriParam(label = "common,security")
    private String kerberosConfigLocation;

    /**
     * Returns a copy of this configuration
     */
    protected KafkaClientConfiguration copy() {
        try {
            KafkaClientConfiguration copy = (KafkaClientConfiguration) clone();
            copy.additionalProperties = new HashMap<>(this.additionalProperties);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Applies the simplified saslAuthType configuration if set and traditional JAAS config is not explicitly provided.
     * <p>
     * This method ensures backward compatibility: if the user has explicitly set saslJaasConfig, it takes precedence
     * over the saslAuthType-based configuration.
     * </p>
     */
    protected void applyAuthTypeConfiguration() {
        // Only apply if saslAuthType is set and saslJaasConfig is NOT explicitly set
        if (saslAuthType != null && ObjectHelper.isEmpty(saslJaasConfig)) {
            KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(saslAuthType);

            // Configure based on auth type
            if (saslAuthType.requiresCredentials()) {
                configurer.withCredentials(saslUsername, saslPassword);
            } else if (saslAuthType.requiresOAuth()) {
                configurer.withOAuth(oauthClientId, oauthClientSecret, oauthTokenEndpointUri);
                if (ObjectHelper.isNotEmpty(oauthScope)) {
                    configurer.withOAuthScope(oauthScope);
                }
            } else if (saslAuthType.requiresKerberos()) {
                // For Kerberos, we still use the existing kerberos properties
                // The saslAuthType just helps set the mechanism and protocol
            }

            // For SASL types, the configurer defaults to useSsl=true (SASL_SSL).
            // Only downgrade to SASL_PLAINTEXT if the user explicitly requested it.
            if (saslAuthType.isSasl()) {
                if (securityProtocol.equals("SASL_PLAINTEXT")) {
                    configurer.withSsl(false);
                }
            } else if (saslAuthType == KafkaAuthType.NONE) {
                boolean hasSslConfig = ObjectHelper.isNotEmpty(sslTruststoreLocation)
                        || ObjectHelper.isNotEmpty(sslKeystoreLocation)
                        || sslContextParameters != null;
                configurer.withSsl(hasSslConfig || securityProtocol.equals("SSL"));
            }

            // Apply the configuration
            configurer.configure(this);
        }
    }

    /**
     * Adds the client options that every Kafka client (producer and consumers) supports: client id, connection,
     * metadata, metrics, backoff and schema registry settings.
     */
    protected void applyCommonClientProperties(Properties props) {
        addPropertyIfNotEmpty(props, CommonClientConfigs.CLIENT_ID_CONFIG, getClientId());
        addPropertyIfNotEmpty(props, CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, getConnectionMaxIdleMs());
        addPropertyIfNotEmpty(props, CommonClientConfigs.RECEIVE_BUFFER_CONFIG, getReceiveBufferBytes());
        addPropertyIfNotEmpty(props, CommonClientConfigs.SEND_BUFFER_CONFIG, getSendBufferBytes());
        addPropertyIfNotEmpty(props, CommonClientConfigs.METADATA_MAX_AGE_CONFIG, getMetadataMaxAgeMs());
        addPropertyIfNotEmpty(props, CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, getMetricReporters());
        addPropertyIfNotEmpty(props, CommonClientConfigs.METRICS_NUM_SAMPLES_CONFIG, getNoOfMetricsSample());
        addPropertyIfNotEmpty(props, CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_CONFIG, getMetricsSampleWindowMs());
        addPropertyIfNotEmpty(props, CommonClientConfigs.RECONNECT_BACKOFF_MS_CONFIG, getReconnectBackoffMs());
        addPropertyIfNotEmpty(props, CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, getReconnectBackoffMaxMs());
        addPropertyIfNotEmpty(props, CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG, getRetryBackoffMs());
        addPropertyIfNotEmpty(props, CommonClientConfigs.RETRY_BACKOFF_MAX_MS_CONFIG, getRetryBackoffMaxMs());
        addPropertyIfNotEmpty(props, "schema.registry.url", getSchemaRegistryURL());
    }

    /**
     * Adds the SSL, security protocol and SASL options. The sslContextParameters are applied before the SSL endpoint
     * options.
     */
    protected void applySecurityProperties(Properties props) {
        String protocol = resolveSecurityProtocol();
        if (sslContextParameters != null) {
            applySslConfigurationFromContext(props, sslContextParameters);
        }
        applySslConfigurationFromOptions(props, protocol);

        addPropertyIfNotEmpty(props, CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol);

        if (isSasl(protocol)) {
            applySaslConfiguration(props);
        }
    }

    /**
     * Adds the additional properties, which override any option set before.
     */
    protected void applyAdditionalProperties(Properties props) {
        if (!ObjectHelper.isEmpty(getAdditionalProperties())) {
            getAdditionalProperties().forEach((property, value) -> {
                if (value != null) {
                    // value should be as-is
                    props.put(property, value);
                }
            });
        }
    }

    private void applySaslConfiguration(Properties props) {
        addPropertyIfNotEmpty(props, SaslConfigs.SASL_KERBEROS_SERVICE_NAME, getSaslKerberosServiceName());
        addPropertyIfNotEmpty(props, SaslConfigs.SASL_KERBEROS_KINIT_CMD, getKerberosInitCmd());
        addPropertyIfNotEmpty(props, SaslConfigs.SASL_KERBEROS_MIN_TIME_BEFORE_RELOGIN, getKerberosBeforeReloginMinTime());
        addPropertyIfNotEmpty(props, SaslConfigs.SASL_KERBEROS_TICKET_RENEW_JITTER, getKerberosRenewJitter());
        addPropertyIfNotEmpty(props, SaslConfigs.SASL_KERBEROS_TICKET_RENEW_WINDOW_FACTOR, getKerberosRenewWindowFactor());
        addPropertyIfNotEmpty(props, BrokerSecurityConfigs.SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES_CONFIG,
                getKerberosPrincipalToLocalRules());
        addPropertyIfNotEmpty(props, SaslConfigs.SASL_MECHANISM, getSaslMechanism());
        addPropertyIfNotEmpty(props, SaslConfigs.SASL_JAAS_CONFIG, getSaslJaasConfig());
    }

    /**
     * The security protocol to use, which is SSL when sslContextParameters are configured and the security protocol is
     * the default (PLAINTEXT).
     */
    private String resolveSecurityProtocol() {
        if (sslContextParameters != null && SecurityProtocol.PLAINTEXT.name().equals(securityProtocol)) {
            return SecurityProtocol.SSL.name();
        }
        return securityProtocol;
    }

    /**
     * Adds the SSL option, unless the option is its default value and the value has already been configured from the
     * sslContextParameters (which are applied before the SSL endpoint options).
     */
    private void addSslOption(Properties props, String key, String value, String defaultValue, boolean upperCase) {
        if (sslContextParameters != null && Objects.equals(value, defaultValue) && props.containsKey(key)) {
            return;
        }
        if (upperCase) {
            addUpperCasePropertyIfNotEmpty(props, key, value);
        } else {
            addPropertyIfNotEmpty(props, key, value);
        }
    }

    private static boolean isSasl(String securityProtocol) {
        return securityProtocol.equals(SecurityProtocol.SASL_PLAINTEXT.name())
                || securityProtocol.equals(SecurityProtocol.SASL_SSL.name());
    }

    private void applySslConfigurationFromOptions(Properties props, String protocol) {
        if (protocol.equals(SecurityProtocol.SSL.name()) || protocol.equals(SecurityProtocol.SASL_SSL.name())) {
            addPropertyIfNotNull(props, SslConfigs.SSL_KEY_PASSWORD_CONFIG, getSslKeyPassword());
            addPropertyIfNotEmpty(props, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getSslKeystoreLocation());
            addPropertyIfNotEmpty(props, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getSslKeystorePassword());
            addPropertyIfNotEmpty(props, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getSslTruststoreLocation());
            addPropertyIfNotEmpty(props, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getSslTruststorePassword());
            addPropertyIfNotEmpty(props, SslConfigs.SSL_CIPHER_SUITES_CONFIG, getSslCipherSuites());
            String algo = getSslEndpointAlgorithm();
            if (algo != null && !algo.equals("none") && !algo.equals("false")) {
                addPropertyIfNotNull(props, SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, algo);
            } else {
                props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
            }
            addSslOption(props, SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, getSslKeymanagerAlgorithm(), "SunX509", false);
            addSslOption(props, SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, getSslTrustmanagerAlgorithm(), "PKIX", false);
            addSslOption(props, SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, getSslEnabledProtocols(),
                    SslConfigs.DEFAULT_SSL_ENABLED_PROTOCOLS, false);
            addSslOption(props, SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, getSslKeystoreType(), SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE,
                    true);
            addSslOption(props, SslConfigs.SSL_PROTOCOL_CONFIG, getSslProtocol(), SslConfigs.DEFAULT_SSL_PROTOCOL, false);
            addPropertyIfNotEmpty(props, SslConfigs.SSL_PROVIDER_CONFIG, getSslProvider());
            addSslOption(props, SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, getSslTruststoreType(),
                    SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE, true);
        }
    }

    /**
     * Uses the standard camel {@link SSLContextParameters} object to fill the Kafka SSL properties
     *
     * @param props                Kafka properties
     * @param sslContextParameters SSL configuration
     */
    private static void applySslConfigurationFromContext(Properties props, SSLContextParameters sslContextParameters) {
        addPropertyIfNotNull(props, SslConfigs.SSL_PROTOCOL_CONFIG, sslContextParameters.getSecureSocketProtocol());
        addPropertyIfNotNull(props, SslConfigs.SSL_PROVIDER_CONFIG, sslContextParameters.getProvider());

        CipherSuitesParameters cipherSuites = sslContextParameters.getCipherSuites();
        if (cipherSuites != null) {
            addCommaSeparatedList(props, SslConfigs.SSL_CIPHER_SUITES_CONFIG, cipherSuites.getCipherSuite());
        }

        SecureSocketProtocolsParameters secureSocketProtocols = sslContextParameters.getSecureSocketProtocols();
        if (secureSocketProtocols != null) {
            addCommaSeparatedList(props, SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG,
                    secureSocketProtocols.getSecureSocketProtocol());
        }

        KeyManagersParameters keyManagers = sslContextParameters.getKeyManagers();
        if (keyManagers != null) {
            addPropertyIfNotNull(props, SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, keyManagers.getAlgorithm());
            addPropertyIfNotNull(props, SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyManagers.getKeyPassword());
            KeyStoreParameters keyStore = keyManagers.getKeyStore();
            if (keyStore != null) {
                // kakfa loads the resource itself and you cannot have a prefix
                String location = keyStore.getResource();
                if (ResourceHelper.hasScheme(location)) {
                    location = StringHelper.after(location, ":");
                }
                addUpperCasePropertyIfNotEmpty(props, SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, keyStore.getType());
                addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, location);
                addPropertyIfNotNull(props, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStore.getPassword());
            }
        }

        TrustManagersParameters trustManagers = sslContextParameters.getTrustManagers();
        if (trustManagers != null) {
            addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, trustManagers.getAlgorithm());
            KeyStoreParameters keyStore = trustManagers.getKeyStore();
            if (keyStore != null) {
                // kakfa loads the resource itself and you cannot have a prefix
                String location = keyStore.getResource();
                if (ResourceHelper.hasScheme(location)) {
                    location = StringHelper.after(location, ":");
                }
                addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, keyStore.getType());
                addPropertyIfNotNull(props, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, location);
                addPropertyIfNotEmpty(props, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, keyStore.getPassword());
            }
        }
    }

    protected static void addPropertyIfNotFalse(Properties props, String key, boolean value) {
        if (value) {
            // value should be as-is
            props.put(key, value);
        }
    }

    protected static <T> void addPropertyIfNotEmpty(Properties props, String key, T value) {
        if (ObjectHelper.isNotEmpty(value)) {
            // value should be as-is
            props.put(key, value);
        }
    }

    protected static <T> void addUpperCasePropertyIfNotEmpty(Properties props, String key, T value) {
        if (ObjectHelper.isNotEmpty(value)) {
            props.put(key, String.valueOf(value).toUpperCase(Locale.ROOT));
        }
    }

    protected static <T> void addPropertyIfNotNull(Properties props, String key, T value) {
        if (value != null) {
            // value should be as-is
            props.put(key, value);
        }
    }

    protected static void addCommaSeparatedList(Properties props, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            props.put(key, values.stream().collect(Collectors.joining(",")));
        }
    }

    public boolean isPreValidateHostAndPort() {
        return preValidateHostAndPort;
    }

    /**
     * Whether to eager validate that broker host:port is valid and can be DNS resolved to known host during starting
     * this consumer. If the validation fails, then an exception is thrown, which makes Camel fail fast.
     *
     * Disabling this will postpone the validation after the consumer is started, and Camel will keep re-connecting in
     * case of validation or DNS resolution error.
     */
    public void setPreValidateHostAndPort(boolean preValidateHostAndPort) {
        this.preValidateHostAndPort = preValidateHostAndPort;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * The client id is a user-specified string sent in each request to help trace calls. It should logically identify
     * the application making the request.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Timeout in milliseconds to wait gracefully for the consumer or producer to shut down and terminate its worker
     * threads.
     */
    public void setShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public String getBrokers() {
        return brokers;
    }

    /**
     * URL of the Kafka brokers to use. The format is host1:port1,host2:port2, and the list can be a subset of brokers
     * or a VIP pointing to a subset of brokers.
     * <p/>
     * This option is known as <tt>bootstrap.servers</tt> in the Kafka documentation.
     */
    public void setBrokers(String brokers) {
        this.brokers = brokers;
    }

    public String getSchemaRegistryURL() {
        return schemaRegistryURL;
    }

    /**
     * URL of the schema registry servers to use. The format is host1:port1,host2:port2. This is known as
     * schema.registry.url in multiple Schema registries documentation. This option is only available externally (not
     * standard Apache Kafka)
     */
    public void setSchemaRegistryURL(String schemaRegistryURL) {
        this.schemaRegistryURL = schemaRegistryURL;
    }

    public boolean isSpecificAvroReader() {
        return specificAvroReader;
    }

    /**
     * This enables the use of a specific Avro reader for use with the in multiple Schema registries documentation with
     * Avro Deserializers implementation. This option is only available externally (not standard Apache Kafka)
     */
    public void setSpecificAvroReader(boolean specificAvroReader) {
        this.specificAvroReader = specificAvroReader;
    }

    public Integer getRetryBackoffMs() {
        return retryBackoffMs;
    }

    /**
     * The amount of time to wait before attempting to retry a failed request to a given topic partition. This avoids
     * repeatedly sending requests in a tight loop under some failure scenarios. This value is the initial backoff value
     * and will increase exponentially for each failed request, up to the retry.backoff.max.ms value.
     */
    public void setRetryBackoffMs(Integer retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public Integer getRetryBackoffMaxMs() {
        return retryBackoffMaxMs;
    }

    /**
     * The maximum amount of time in milliseconds to wait when retrying a request to the broker that has repeatedly
     * failed. If provided, the backoff per client will increase exponentially for each failed request, up to this
     * maximum. To prevent all clients from being synchronized upon retry, a randomized jitter with a factor of 0.2 will
     * be applied to the backoff, resulting in the backoff falling within a range between 20% below and 20% above the
     * computed value. If retry.backoff.ms is set to be higher than retry.backoff.max.ms, then retry.backoff.max.ms will
     * be used as a constant backoff from the beginning without any exponential increase
     */
    public void setRetryBackoffMaxMs(Integer retryBackoffMaxMs) {
        this.retryBackoffMaxMs = retryBackoffMaxMs;
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
     * Login thread will sleep until the specified window factor of time from last refresh to ticket's expiry has been
     * reached, at which time it will try to renew the ticket.
     */
    public void setKerberosRenewWindowFactor(Double kerberosRenewWindowFactor) {
        this.kerberosRenewWindowFactor = kerberosRenewWindowFactor;
    }

    public String getKerberosPrincipalToLocalRules() {
        return kerberosPrincipalToLocalRules;
    }

    /**
     * A list of rules for mapping from principal names to short names (typically operating system usernames). The rules
     * are evaluated in order, and the first rule that matches a principal name is used to map it to a short name. Any
     * later rules in the list are ignored. By default, principal names of the form {username}/{hostname}@{REALM} are
     * mapped to {username}. For more details on the format, please see the Security Authorization and ACLs
     * documentation (at the Apache Kafka project website).
     *
     * Multiple values can be separated by comma
     */
    public void setKerberosPrincipalToLocalRules(String kerberosPrincipalToLocalRules) {
        this.kerberosPrincipalToLocalRules = kerberosPrincipalToLocalRules;
    }

    public String getSslCipherSuites() {
        return sslCipherSuites;
    }

    /**
     * A list of cipher suites. This is a named combination of authentication, encryption, MAC and key exchange
     * algorithm used to negotiate the security settings for a network connection using TLS or SSL network protocol. By
     * default, all the available cipher suites are supported.
     */
    public void setSslCipherSuites(String sslCipherSuites) {
        this.sslCipherSuites = sslCipherSuites;
    }

    public String getSslEndpointAlgorithm() {
        return sslEndpointAlgorithm;
    }

    /**
     * The endpoint identification algorithm to validate server hostname using server certificate. Use none or false to
     * disable server hostname verification.
     */
    public void setSslEndpointAlgorithm(String sslEndpointAlgorithm) {
        this.sslEndpointAlgorithm = sslEndpointAlgorithm;
    }

    public String getSslKeymanagerAlgorithm() {
        return sslKeymanagerAlgorithm;
    }

    /**
     * The algorithm used by key manager factory for SSL connections. Default value is the key manager factory algorithm
     * configured for the Java Virtual Machine.
     */
    public void setSslKeymanagerAlgorithm(String sslKeymanagerAlgorithm) {
        this.sslKeymanagerAlgorithm = sslKeymanagerAlgorithm;
    }

    public String getSslTrustmanagerAlgorithm() {
        return sslTrustmanagerAlgorithm;
    }

    /**
     * The algorithm used by trust manager factory for SSL connections. Default value is the trust manager factory
     * algorithm configured for the Java Virtual Machine.
     */
    public void setSslTrustmanagerAlgorithm(String sslTrustmanagerAlgorithm) {
        this.sslTrustmanagerAlgorithm = sslTrustmanagerAlgorithm;
    }

    public String getSslEnabledProtocols() {
        return sslEnabledProtocols;
    }

    /**
     * The list of protocols enabled for SSL connections. The default is TLSv1.2,TLSv1.3 when running with Java 11 or
     * newer, TLSv1.2 otherwise. With the default value for Java 11, clients and servers will prefer TLSv1.3 if both
     * support it and fallback to TLSv1.2 otherwise (assuming both support at least TLSv1.2). This default should be
     * fine for most cases. Also see the config documentation for SslProtocol.
     */
    public void setSslEnabledProtocols(String sslEnabledProtocols) {
        this.sslEnabledProtocols = sslEnabledProtocols;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    /**
     * The file format of the key store file. This is optional for the client. The default value is JKS
     */
    public void setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    /**
     * The SSL protocol used to generate the SSLContext. The default is TLSv1.3 when running with Java 11 or newer,
     * TLSv1.2 otherwise. This value should be fine for most use cases. Allowed values in recent JVMs are TLSv1.2 and
     * TLSv1.3. TLS, TLSv1.1, SSL, SSLv2 and SSLv3 may be supported in older JVMs, but their usage is discouraged due to
     * known security vulnerabilities. With the default value for this config and sslEnabledProtocols, clients will
     * downgrade to TLSv1.2 if the server does not support TLSv1.3. If this config is set to TLSv1.2, clients will not
     * use TLSv1.3 even if it is one of the values in sslEnabledProtocols and the server only supports TLSv1.3.
     */
    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public String getSslProvider() {
        return sslProvider;
    }

    /**
     * The name of the security provider used for SSL connections. Default value is the default security provider of the
     * JVM.
     */
    public void setSslProvider(String sslProvider) {
        this.sslProvider = sslProvider;
    }

    public String getSslTruststoreType() {
        return sslTruststoreType;
    }

    /**
     * The file format of the trust store file. The default value is JKS.
     */
    public void setSslTruststoreType(String sslTruststoreType) {
        this.sslTruststoreType = sslTruststoreType;
    }

    public String getSaslKerberosServiceName() {
        return saslKerberosServiceName;
    }

    /**
     * The Kerberos principal name that Kafka runs as. This can be defined either in Kafka's JAAS config or in Kafka's
     * config.
     */
    public void setSaslKerberosServiceName(String saslKerberosServiceName) {
        this.saslKerberosServiceName = saslKerberosServiceName;
    }

    public String getSaslMechanism() {
        return saslMechanism;
    }

    /**
     * The Simple Authentication and Security Layer (SASL) Mechanism used. For the valid values see <a href=
     * "http://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml">http://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml</a>
     */
    public void setSaslMechanism(String saslMechanism) {
        this.saslMechanism = saslMechanism;
    }

    public String getSaslJaasConfig() {
        return saslJaasConfig;
    }

    /**
     * Expose the kafka sasl.jaas.config parameter Example: org.apache.kafka.common.security.plain.PlainLoginModule
     * required username="USERNAME" password="PASSWORD";
     */
    public void setSaslJaasConfig(String saslJaasConfig) {
        this.saslJaasConfig = saslJaasConfig;
    }

    public KafkaAuthType getSaslAuthType() {
        return saslAuthType;
    }

    /**
     * Simplified authentication type to use. This provides an easier way to configure Kafka authentication without
     * manually setting securityProtocol, saslMechanism, and saslJaasConfig.
     * <p>
     * When set, the appropriate security settings are automatically derived based on the authentication type. This is
     * optional - you can still use the traditional approach with explicit securityProtocol, saslMechanism, and
     * saslJaasConfig properties.
     * </p>
     * <p>
     * Supported values: NONE, PLAIN, SCRAM_SHA_256, SCRAM_SHA_512, SSL, OAUTH, AWS_MSK_IAM, KERBEROS
     * </p>
     *
     * @param saslAuthType the authentication type to use
     */
    public void setSaslAuthType(KafkaAuthType saslAuthType) {
        this.saslAuthType = saslAuthType;
    }

    public String getSaslUsername() {
        return saslUsername;
    }

    /**
     * Username for SASL authentication. Used when saslAuthType is set to PLAIN, SCRAM_SHA_256, or SCRAM_SHA_512.
     *
     * @param saslUsername the SASL username
     */
    public void setSaslUsername(String saslUsername) {
        this.saslUsername = saslUsername;
    }

    public String getSaslPassword() {
        return saslPassword;
    }

    /**
     * Password for SASL authentication. Used when saslAuthType is set to PLAIN, SCRAM_SHA_256, or SCRAM_SHA_512.
     *
     * @param saslPassword the SASL password
     */
    public void setSaslPassword(String saslPassword) {
        this.saslPassword = saslPassword;
    }

    public String getOauthClientId() {
        return oauthClientId;
    }

    /**
     * OAuth client ID. Used when saslAuthType is set to OAUTH.
     *
     * @param oauthClientId the OAuth client ID
     */
    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = oauthClientId;
    }

    public String getOauthClientSecret() {
        return oauthClientSecret;
    }

    /**
     * OAuth client secret. Used when saslAuthType is set to OAUTH.
     *
     * @param oauthClientSecret the OAuth client secret
     */
    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = oauthClientSecret;
    }

    public String getOauthTokenEndpointUri() {
        return oauthTokenEndpointUri;
    }

    /**
     * OAuth token endpoint URI. Used when saslAuthType is set to OAUTH.
     *
     * @param oauthTokenEndpointUri the OAuth token endpoint URI
     */
    public void setOauthTokenEndpointUri(String oauthTokenEndpointUri) {
        this.oauthTokenEndpointUri = oauthTokenEndpointUri;
    }

    public String getOauthScope() {
        return oauthScope;
    }

    /**
     * OAuth scope. Used when saslAuthType is set to OAUTH.
     *
     * @param oauthScope the OAuth scope
     */
    public void setOauthScope(String oauthScope) {
        this.oauthScope = oauthScope;
    }

    public String getSecurityProtocol() {
        return securityProtocol;
    }

    /**
     * Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are supported
     */
    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * SSL configuration using a Camel {@link SSLContextParameters} object. If configured, it's applied before the other
     * SSL endpoint parameters.
     *
     * NOTE: Kafka only supports loading keystore from file locations, so prefix the location with file: in the
     * KeyStoreParameters.resource option.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    /**
     * The password of the private key in the key store file or the PEM key specified in sslKeystoreKey. This is
     * required for clients only if two-way authentication is configured.
     */
    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getSslKeystoreLocation() {
        return sslKeystoreLocation;
    }

    /**
     * The location of the key store file. This is optional for the client and can be used for two-way authentication
     * for the client.
     */
    public void setSslKeystoreLocation(String sslKeystoreLocation) {
        this.sslKeystoreLocation = sslKeystoreLocation;
    }

    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    /**
     * The store password for the key store file. This is optional for the client and only needed if sslKeystoreLocation
     * is configured. Key store password is not supported for PEM format.
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
     * The password for the trust store file. If a password is not set, trust store file configured will still be used,
     * but integrity checking is disabled. Trust store password is not supported for PEM format.
     */
    public void setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
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

    public Integer getReceiveBufferBytes() {
        return receiveBufferBytes;
    }

    /**
     * The size of the TCP receive buffer (SO_RCVBUF) to use when reading data.
     */
    public void setReceiveBufferBytes(Integer receiveBufferBytes) {
        this.receiveBufferBytes = receiveBufferBytes;
    }

    public Integer getMetadataMaxAgeMs() {
        return metadataMaxAgeMs;
    }

    /**
     * The period of time in milliseconds after which we force a refresh of metadata even if we haven't seen any
     * partition leadership changes to proactively discover any new brokers or partitions.
     */
    public void setMetadataMaxAgeMs(Integer metadataMaxAgeMs) {
        this.metadataMaxAgeMs = metadataMaxAgeMs;
    }

    public String getMetricReporters() {
        return metricReporters;
    }

    /**
     * A list of classes to use as metrics reporters. Implementing the MetricReporter interface allows plugging in
     * classes that will be notified of new metric creation. The JmxReporter is always included to register JMX
     * statistics.
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
     * The window of time a metrics sample is computed over.
     */
    public void setMetricsSampleWindowMs(Integer metricsSampleWindowMs) {
        this.metricsSampleWindowMs = metricsSampleWindowMs;
    }

    public Integer getReconnectBackoffMs() {
        return reconnectBackoffMs;
    }

    /**
     * The amount of time to wait before attempting to reconnect to a given host. This avoids repeatedly connecting to a
     * host in a tight loop. This backoff applies to all requests sent by the consumer to the broker.
     */
    public void setReconnectBackoffMs(Integer reconnectBackoffMs) {
        this.reconnectBackoffMs = reconnectBackoffMs;
    }

    public String getKeyDeserializer() {
        return keyDeserializer;
    }

    /**
     * Deserializer class for the key that implements the Deserializer interface.
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

    public Integer getReconnectBackoffMaxMs() {
        return reconnectBackoffMaxMs;
    }

    /**
     * The maximum amount of time in milliseconds to wait when reconnecting to a broker that has repeatedly failed to
     * connect. If provided, the backoff per host will increase exponentially for each consecutive connection failure,
     * up to this maximum. After calculating the backoff increase, 20% random jitter is added to avoid connection
     * storms.
     */
    public void setReconnectBackoffMaxMs(Integer reconnectBackoffMaxMs) {
        this.reconnectBackoffMaxMs = reconnectBackoffMaxMs;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from the Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public KafkaHeaderDeserializer getHeaderDeserializer() {
        return headerDeserializer;
    }

    /**
     * Sets custom KafkaHeaderDeserializer for deserialization kafka headers values to camel headers values.
     *
     * @param headerDeserializer custom kafka header deserializer to be used
     */
    public void setHeaderDeserializer(final KafkaHeaderDeserializer headerDeserializer) {
        this.headerDeserializer = headerDeserializer;
    }

    /**
     * Sets additional properties for either kafka consumer or kafka producer in case they can't be set directly on the
     * camel configurations (e.g.: new Kafka properties that are not reflected yet in Camel configurations), the
     * properties have to be prefixed with `additionalProperties.`., e.g.:
     * `additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://localhost:8811/avro`.
     * If the properties are set in the `application.properties` file, they must be prefixed with
     * `camel.component.kafka.additional-properties` and the property enclosed in square brackets, like this example:
     * `camel.component.kafka.additional-properties[delivery.timeout.ms]=15000`.
     */
    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public String getKerberosConfigLocation() {
        return kerberosConfigLocation;
    }

    /**
     * Location of the kerberos config file.
     */
    public void setKerberosConfigLocation(String kerberosConfigLocation) {
        this.kerberosConfigLocation = kerberosConfigLocation;
    }
}
