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
import org.apache.camel.support.jsse.JsseParametersHelper;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SecureSocketProtocolsParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base configuration of the Camel Kafka endpoints: the brokers, the client id, the connection, metrics and backoff
 * settings, the security (SSL, SASL, Kerberos, OAuth) settings and the additional properties, which every Kafka client
 * uses, together with the options for reading records (key and value deserializers, header deserializer, header filter
 * strategy, schema registry and the validation of the brokers at startup), which the Kafka consumers use.
 * <p/>
 * A configuration for a specific endpoint extends this class with the options of its clients, and builds the client
 * properties with {@link #applyCommonClientProperties(Properties)}, {@link #applySecurityProperties(Properties)} and
 * {@link #applyAdditionalProperties(Properties)}. A configuration for a client that does not read records, such as an
 * admin client, should not extend this class as it is, as it would inherit the options for reading records.
 */
@UriParams
public abstract class KafkaClientConfiguration implements Cloneable, HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaClientConfiguration.class);

    /**
     * Whether the options of sslContextParameters that the Kafka client cannot carry have been logged, so it is done
     * once per endpoint, not each time the properties of a Kafka client are created.
     */
    private volatile boolean droppedSslOptionsLogged;

    @UriParam(label = "common",
              description = "URL of the Kafka brokers to use. The format is host1:port1,host2:port2, and the list can be a "
                            + "subset of brokers or a VIP pointing to a subset of brokers. This option is known as "
                            + "bootstrap.servers in the Kafka documentation.")
    private String brokers;
    @UriParam(label = "common",
              description = "The client id is a user-specified string sent in each request to help trace calls. It should "
                            + "logically identify the application making the request.")
    private String clientId;
    @UriParam(label = "common",
              description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy = new KafkaHeaderFilterStrategy();
    @UriParam(label = "common", defaultValue = "100",
              description = "The amount of time to wait before attempting to retry a failed request to a given topic "
                            + "partition. This avoids repeatedly sending requests in a tight loop under some failure "
                            + "scenarios. This value is the initial backoff value and will increase exponentially for each "
                            + "failed request, up to the retry.backoff.max.ms value.")
    private Integer retryBackoffMs = 100;
    @UriParam(label = "common", defaultValue = "1000",
              description = "The maximum amount of time in milliseconds to wait when retrying a request to the broker that "
                            + "has repeatedly failed. If provided, the backoff per client will increase exponentially for each"
                            + " failed request, up to this maximum. To prevent all clients from being synchronized upon retry,"
                            + " a randomized jitter with a factor of 0.2 will be applied to the backoff, resulting in the "
                            + "backoff falling within a range between 20% below and 20% above the computed value. If "
                            + "retry.backoff.ms is set to be higher than retry.backoff.max.ms, then retry.backoff.max.ms will "
                            + "be used as a constant backoff from the beginning without any exponential increase")
    private Integer retryBackoffMaxMs = 1000;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether to eager validate that broker host:port is valid and can be DNS resolved to known host "
                            + "during starting this consumer. If the validation fails, then an exception is thrown, which "
                            + "makes Camel fail fast. Disabling this will postpone the validation after the consumer is "
                            + "started, and Camel will keep re-connecting in case of validation or DNS resolution error.")
    private boolean preValidateHostAndPort = true;
    @UriParam(label = "consumer", description = "To use a custom KafkaHeaderDeserializer to deserialize kafka headers values")
    private KafkaHeaderDeserializer headerDeserializer = new DefaultKafkaHeaderDeserializer();
    // key.deserializer
    @UriParam(label = "consumer", defaultValue = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER,
              description = "Deserializer class for the key that implements the Deserializer interface.")
    private String keyDeserializer = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER;
    // value.deserializer
    @UriParam(label = "consumer", defaultValue = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER,
              description = "Deserializer class for value that implements the Deserializer interface.")
    private String valueDeserializer = KafkaConstants.KAFKA_DEFAULT_DESERIALIZER;
    // connections.max.idle.ms
    @UriParam(label = "common", defaultValue = "540000",
              description = "Close idle connections after the number of milliseconds specified by this config.")
    private Integer connectionMaxIdleMs = 540000;
    // receive.buffer.bytes
    @UriParam(label = "common", defaultValue = "65536",
              description = "The size of the TCP receive buffer (SO_RCVBUF) to use when reading data. If the value is -1, "
                            + "the OS default will be used.")
    private Integer receiveBufferBytes = 65536;
    // send.buffer.bytes
    @UriParam(label = "common", defaultValue = "131072",
              description = "The size of the TCP send buffer (SO_SNDBUF) to use when sending data. If the value is -1, the "
                            + "OS default will be used.")
    private Integer sendBufferBytes = 131072;
    // metadata.max.age.ms
    @UriParam(label = "common", defaultValue = "300000",
              description = "The period of time in milliseconds after which we force a refresh of metadata even if we "
                            + "haven't seen any partition leadership changes to proactively discover any new brokers or "
                            + "partitions.")
    private Integer metadataMaxAgeMs = 300000;
    // metric.reporters
    @UriParam(label = "common",
              description = "A list of classes to use as metrics reporters. Implementing the "
                            + "org.apache.kafka.common.metrics.MetricsReporter interface allows plugging in classes that will "
                            + "be notified of new metric creation. When custom reporters are set and "
                            + "org.apache.kafka.common.metrics.JmxReporter is needed, it has to be explicitly added to the "
                            + "list.")
    private String metricReporters;
    // metrics.num.samples
    @UriParam(label = "common", defaultValue = "2", description = "The number of samples maintained to compute metrics.")
    private Integer noOfMetricsSample = 2;
    // metrics.sample.window.ms
    @UriParam(label = "common", defaultValue = "30000", description = "The window of time a metrics sample is computed over.")
    private Integer metricsSampleWindowMs = 30000;
    // reconnect.backoff.ms
    @UriParam(label = "common", defaultValue = "50",
              description = "The base amount of time to wait before attempting to reconnect to a given host. This avoids "
                            + "repeatedly connecting to a host in a tight loop. This backoff applies to all connection "
                            + "attempts by the client to a broker. This value is the initial backoff value and will increase "
                            + "exponentially for each consecutive connection failure, up to the reconnectBackoffMaxMs value.")
    private Integer reconnectBackoffMs = 50;
    // reconnect.backoff.max.ms
    @UriParam(label = "common", defaultValue = "1000",
              description = "The maximum amount of time in milliseconds to wait when reconnecting to a broker that has "
                            + "repeatedly failed to connect. If provided, the backoff per host will increase exponentially for"
                            + " each consecutive connection failure, up to this maximum. After calculating the backoff "
                            + "increase, 20% random jitter is added to avoid connection storms.")
    private Integer reconnectBackoffMaxMs = 1000;
    // SSL
    @UriParam(label = "common,security",
              description = "SSL configuration using a Camel SSLContextParameters object. If configured, it's applied before"
                            + " the other SSL endpoint parameters. NOTE: Kafka only supports loading keystore from file "
                            + "locations, so prefix the location with file: in the KeyStoreParameters.resource option.")
    private SSLContextParameters sslContextParameters;
    // SSL
    // ssl.key.password
    @UriParam(label = "common,security", security = "secret",
              description = "The password of the private key in the key store file or the PEM key specified in "
                            + "sslKeystoreKey. This is required for clients only if two-way authentication is configured.")
    private String sslKeyPassword;
    // ssl.keystore.location
    @UriParam(label = "common,security",
              description = "The location of the key store file. This is optional for the client and can be used for two-way"
                            + " authentication for the client.")
    private String sslKeystoreLocation;
    // ssl.keystore.password
    @UriParam(label = "common,security", security = "secret",
              description = "The store password for the key store file. This is optional for the client and only needed if "
                            + "sslKeystoreLocation is configured. Key store password is not supported for PEM format.")
    private String sslKeystorePassword;
    // ssl.truststore.location
    @UriParam(label = "common,security", description = "The location of the trust store file.")
    private String sslTruststoreLocation;
    // ssl.truststore.password
    @UriParam(label = "common,security", security = "secret",
              description = "The password for the trust store file. If a password is not set, trust store file configured "
                            + "will still be used, but integrity checking is disabled. Trust store password is not supported "
                            + "for PEM format.")
    private String sslTruststorePassword;
    // SSL
    // ssl.enabled.protocols
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_ENABLED_PROTOCOLS,
              description = "The list of protocols enabled for SSL connections. The default is TLSv1.2,TLSv1.3. This means "
                            + "that clients and servers will prefer TLSv1.3 if both support it and fallback to TLSv1.2 "
                            + "otherwise (assuming both support at least TLSv1.2). This default should be fine for most use "
                            + "cases. Also see the documentation of sslProtocol to understand how it can impact the TLS "
                            + "version negotiation behavior.")
    private String sslEnabledProtocols = SslConfigs.DEFAULT_SSL_ENABLED_PROTOCOLS;
    // ssl.keystore.type
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE,
              description = "The file format of the key store file. This is optional for the client. The supported values "
                            + "are JKS, PKCS12 and PEM.")
    private String sslKeystoreType = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE;
    // ssl.protocol
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_PROTOCOL,
              description = "The SSL protocol used to generate the SSLContext. The default is TLSv1.3, which should be fine "
                            + "for most use cases. A typical alternative to the default is TLSv1.2. Allowed values for this "
                            + "config are dependent on the JVM. Clients using the defaults for this config and "
                            + "sslEnabledProtocols will downgrade to TLSv1.2 if the server does not support TLSv1.3. If this "
                            + "config is set to TLSv1.2, however, clients will not use TLSv1.3 even if it is one of the values "
                            + "in sslEnabledProtocols and the server only supports TLSv1.3.")
    private String sslProtocol = SslConfigs.DEFAULT_SSL_PROTOCOL;
    // ssl.provider
    @UriParam(label = "common,security",
              description = "The name of the security provider used for SSL connections. Default value is the default "
                            + "security provider of the JVM.")
    private String sslProvider;
    // ssl.truststore.type
    @UriParam(label = "common,security", defaultValue = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE,
              description = "The file format of the trust store file. The supported values are JKS, PKCS12 and PEM.")
    private String sslTruststoreType = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE;
    // SSL
    // ssl.cipher.suites
    @UriParam(label = "common,security",
              description = "A list of cipher suites. This is a named combination of authentication, encryption, MAC and key"
                            + " exchange algorithm used to negotiate the security settings for a network connection using TLS "
                            + "or SSL network protocol. By default, all the available cipher suites are supported.")
    private String sslCipherSuites;
    // ssl.endpoint.identification.algorithm
    @UriParam(label = "common,security", defaultValue = "https", security = "insecure:ssl", insecureValue = "none",
              description = "The endpoint identification algorithm to validate server hostname using server certificate. Use"
                            + " none or false to disable server hostname verification.")
    private String sslEndpointAlgorithm = SslConfigs.DEFAULT_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM;
    // ssl.keymanager.algorithm
    @UriParam(label = "common,security", defaultValue = "SunX509",
              description = "The algorithm used by key manager factory for SSL connections. Default value is the key manager"
                            + " factory algorithm configured for the Java Virtual Machine.")
    private String sslKeymanagerAlgorithm = "SunX509";
    // ssl.trustmanager.algorithm
    @UriParam(label = "common,security", defaultValue = "PKIX",
              description = "The algorithm used by trust manager factory for SSL connections. Default value is the trust "
                            + "manager factory algorithm configured for the Java Virtual Machine.")
    private String sslTrustmanagerAlgorithm = "PKIX";
    // SASL & sucurity Protocol
    // sasl.kerberos.service.name
    @UriParam(label = "common,security",
              description = "The Kerberos principal name that Kafka runs as. This can be defined either in Kafka's JAAS "
                            + "config or in Kafka's config.")
    private String saslKerberosServiceName;
    // security.protocol
    @UriParam(label = "common,security", defaultValue = CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL,
              description = "Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are "
                            + "supported")
    private String securityProtocol = CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL;
    // SASL
    // sasl.mechanism
    @UriParam(label = "common,security", defaultValue = SaslConfigs.DEFAULT_SASL_MECHANISM,
              description = "The Simple Authentication and Security Layer (SASL) Mechanism used. For the valid values see "
                            + "http://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml")
    private String saslMechanism = SaslConfigs.DEFAULT_SASL_MECHANISM;
    // sasl.kerberos.kinit.cmd
    @UriParam(label = "common,security", defaultValue = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD,
              description = "Kerberos kinit command path. Default is /usr/bin/kinit")
    private String kerberosInitCmd = SaslConfigs.DEFAULT_KERBEROS_KINIT_CMD;
    // sasl.kerberos.min.time.before.relogin
    @UriParam(label = "common,security", defaultValue = "60000",
              description = "Login thread sleep time between refresh attempts.")
    private Integer kerberosBeforeReloginMinTime = 60000;
    // sasl.kerberos.ticket.renew.jitter
    @UriParam(label = "common,security", defaultValue = "0.05",
              description = "Percentage of random jitter added to the renewal time.")
    private Double kerberosRenewJitter = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_JITTER;
    // sasl.kerberos.ticket.renew.window.factor
    @UriParam(label = "common,security", defaultValue = "0.8",
              description = "Login thread will sleep until the specified window factor of time from last refresh to ticket's"
                            + " expiry has been reached, at which time it will try to renew the ticket.")
    private Double kerberosRenewWindowFactor = SaslConfigs.DEFAULT_KERBEROS_TICKET_RENEW_WINDOW_FACTOR;
    @UriParam(label = "common,security", defaultValue = "DEFAULT",
              description = "A list of rules for mapping from principal names to short names (typically operating system "
                            + "usernames). The rules are evaluated in order, and the first rule that matches a principal name "
                            + "is used to map it to a short name. Any later rules in the list are ignored. By default, "
                            + "principal names made of a username, a hostname and a Kerberos realm are mapped to the "
                            + "username. For more details on the format, please see the Security Authorization and ACLs documentation (at the "
                            + "Apache Kafka project website). Multiple values can be separated by comma")
    // sasl.kerberos.principal.to.local.rules
    private String kerberosPrincipalToLocalRules;
    @UriParam(label = "common,security", security = "secret",
              description = "Expose the kafka sasl.jaas.config parameter Example: "
                            + "org.apache.kafka.common.security.plain.PlainLoginModule required username=USERNAME "
                            + "password=PASSWORD;")
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
    @UriParam(label = "schema",
              description = "URL of the schema registry servers to use. The format is host1:port1,host2:port2. This is known"
                            + " as schema.registry.url in multiple Schema registries documentation. This option is only "
                            + "available externally (not standard Apache Kafka). When schemaRegistryVendor is set, the URL "
                            + "is set under the property of the vendor: schema.registry.url for confluent, "
                            + "apicurio.registry.url for apicurio (the URL of the registry API, such as "
                            + "http://localhost:8080/apis/registry/v3), schema.registry.url for azure (the fully qualified "
                            + "namespace, such as my-namespace.servicebus.windows.net), and endpoint for aws-glue (optional, "
                            + "as Glue is addressed by the AWS region).")
    private String schemaRegistryURL;
    @UriParam(label = "schema,consumer",
              description = "This enables the use of a specific Avro reader for use with the in multiple Schema registries "
                            + "documentation with Avro Deserializers implementation. This option is only available externally "
                            + "(not standard Apache Kafka). When schemaRegistryVendor is set, it is set under the property "
                            + "of the vendor (for apicurio apicurio.registry.use-specific-avro-reader, for aws-glue "
                            + "avroRecordType=SPECIFIC_RECORD).")
    private boolean specificAvroReader;
    @UriParam(label = "schema", enums = KafkaSchemaRegistry.VENDORS,
              description = "The schema registry vendor, which sets up the value serializer (producer), the value "
                            + "deserializer (consumer) and the registry properties of the vendor for the schemaFormat, "
                            + "so that you do not need to know the class names or the property names of the vendor. The "
                            + "schemaRegistryURL is set as the registry URL of the vendor. A valueSerializer or "
                            + "valueDeserializer other than the default, and additionalProperties, take precedence. The "
                            + "serde jars of the vendor must be on the classpath, as Camel does not include them. Leave it "
                            + "unset to configure the serializer, deserializer and registry properties yourself.")
    private String schemaRegistryVendor;
    @UriParam(label = "schema", defaultValue = KafkaSchemaRegistry.AVRO, enums = KafkaSchemaRegistry.FORMATS,
              description = "The format of the record values in the schema registry, which selects the serializer and "
                            + "deserializer of the schemaRegistryVendor. Not every vendor supports every format: azure "
                            + "supports only avro. Only used when schemaRegistryVendor is set.")
    private String schemaFormat = KafkaSchemaRegistry.AVRO;
    @UriParam(label = "schema,security",
              description = "The username to log in to the schema registry with basic auth, with schemaRegistryPassword. Used "
                            + "with schemaRegistryVendor confluent (basic.auth.user.info, which is the API key for Confluent Cloud) "
                            + "and apicurio (apicurio.registry.auth.username). The azure and aws-glue vendors log in with the "
                            + "credentials of the environment instead.")
    private String schemaRegistryUsername;
    @UriParam(label = "schema,security", security = "secret",
              description = "The password to log in to the schema registry with basic auth, with schemaRegistryUsername. For "
                            + "Confluent Cloud, the API secret.")
    private String schemaRegistryPassword;
    @UriParam(label = "schema,security",
              description = "The token endpoint of the OAuth2 server to log in to the schema registry with client credentials, "
                            + "with schemaRegistryOauthClientId and schemaRegistryOauthClientSecret. Used with schemaRegistryVendor "
                            + "confluent (bearer.auth.issuer.endpoint.url) and apicurio "
                            + "(apicurio.registry.auth.service.token.endpoint, such as the token endpoint of a Keycloak realm).")
    private String schemaRegistryOauthTokenEndpoint;
    @UriParam(label = "schema,security",
              description = "The OAuth2 client id to log in to the schema registry with client credentials. Used with "
                            + "schemaRegistryVendor confluent and apicurio.")
    private String schemaRegistryOauthClientId;
    @UriParam(label = "schema,security", security = "secret",
              description = "The OAuth2 client secret to log in to the schema registry with client credentials.")
    private String schemaRegistryOauthClientSecret;
    @UriParam(label = "schema,security",
              description = "The OAuth2 scope to ask for when logging in to the schema registry with client credentials "
                            + "(optional).")
    private String schemaRegistryOauthScope;
    // Additional properties
    @UriParam(label = "common", prefix = "additionalProperties.", multiValue = true,
              description = "Sets additional properties for either kafka consumer or kafka producer in case they can't be "
                            + "set directly on the camel configurations (e.g.: new Kafka properties that are not reflected yet"
                            + " in Camel configurations), the properties have to be prefixed with additionalProperties., e.g.:"
                            + " additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://lo"
                            + "calhost:8811/avro. If the properties are set in the application.properties file, they must be "
                            + "prefixed with camel.component.kafka.additional-properties "
                            + "(camel.component.kafka-share.additional-properties for the Kafka Share component) followed by "
                            + "the property name enclosed in square brackets, for example the delivery.timeout.ms property in "
                            + "square brackets.")
    private Map<String, Object> additionalProperties = new HashMap<>();
    @UriParam(label = "common", defaultValue = "30000",
              description = "Timeout in milliseconds to wait gracefully for the consumer or producer to shut down and "
                            + "terminate its worker threads.")
    private int shutdownTimeout = 30000;
    @UriParam(label = "common,security", description = "Location of the kerberos config file.")
    private String kerberosConfigLocation;

    /**
     * Returns a copy of this configuration
     */
    protected KafkaClientConfiguration copy() {
        try {
            KafkaClientConfiguration copy = (KafkaClientConfiguration) clone();
            copy.additionalProperties = new HashMap<>(this.additionalProperties);
            copy.droppedSslOptionsLogged = false;
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
        KafkaSchemaRegistry.applyUrl(props, resolveSchemaRegistryVendor(), getSchemaRegistryURL());
        KafkaSchemaRegistry.applyLogin(props, resolveSchemaRegistryVendor(), getSchemaRegistryUsername(),
                getSchemaRegistryPassword(), getSchemaRegistryOauthTokenEndpoint(), getSchemaRegistryOauthClientId(),
                getSchemaRegistryOauthClientSecret(), getSchemaRegistryOauthScope());
    }

    /**
     * Adds the value serializer and the registry properties of the schemaRegistryVendor to the producer properties. The
     * value serializer is only set when the configured one is the default, so that an explicit valueSerializer wins.
     * Does nothing if no schemaRegistryVendor is set.
     *
     * @param  props                    the producer properties
     * @param  valueSerializer          the configured value serializer
     * @throws IllegalArgumentException if the vendor or format is unknown, or the vendor does not support the format
     */
    protected void applySchemaRegistryProducerProperties(Properties props, String valueSerializer) {
        String vendor = resolveSchemaRegistryVendor();
        if (vendor != null) {
            boolean explicit = !KafkaConstants.KAFKA_DEFAULT_SERIALIZER.equals(valueSerializer);
            KafkaSchemaRegistry.applyProducer(props, vendor, getSchemaFormat(), explicit);
        }
    }

    /**
     * Adds the value deserializer and the registry properties of the schemaRegistryVendor to the consumer properties,
     * and the specificAvroReader option under the key of the vendor. The value deserializer is only set when the
     * configured one is the default, so that an explicit valueDeserializer wins. Without a schemaRegistryVendor, the
     * specificAvroReader option is set as specific.avro.reader.
     *
     * @throws IllegalArgumentException if the vendor or format is unknown, or the vendor does not support the format
     */
    protected void applySchemaRegistryConsumerProperties(Properties props) {
        String vendor = resolveSchemaRegistryVendor();
        if (vendor != null) {
            boolean explicit = !KafkaConstants.KAFKA_DEFAULT_DESERIALIZER.equals(getValueDeserializer());
            KafkaSchemaRegistry.applyConsumer(props, vendor, getSchemaFormat(), isSpecificAvroReader(), explicit);
        } else {
            addPropertyIfNotFalse(props, KafkaSchemaRegistry.CONFLUENT_SPECIFIC_AVRO_READER, isSpecificAvroReader());
        }
    }

    /**
     * Checks the value serializer or deserializer that the schemaRegistryVendor sets. The endpoint replaces the class
     * names of the serializers and deserializers by the classes that the class resolver of the CamelContext loads
     * (which downloads the jar with Camel JBang), so the class name of the vendor that is still a String is a class
     * that none of the class loaders of Camel and Kafka has, and Kafka would fail to load it too.
     *
     * @param  props the properties of the client, after the endpoint replaced the class names by the classes
     * @param  key   value.serializer or value.deserializer
     * @return       a message that names the Maven artifact of the class to add, or null if the class was loaded, no
     *               schemaRegistryVendor is set, or the class is not the one of the vendor (such as an explicit
     *               valueSerializer)
     */
    public String missingSchemaRegistrySerde(Properties props, String key) {
        if (ObjectHelper.isEmpty(schemaRegistryVendor) || !(props.get(key) instanceof String name)) {
            return null;
        }
        String vendor = resolveSchemaRegistryVendor();
        boolean serializer = ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG.equals(key);
        String expected = serializer
                ? KafkaSchemaRegistry.serializer(vendor, getSchemaFormat())
                : KafkaSchemaRegistry.deserializer(vendor, getSchemaFormat());
        if (!expected.equals(name.trim())) {
            return null;
        }
        return KafkaSchemaRegistry.missingSerdeMessage(vendor, getSchemaFormat(),
                serializer ? "serializer" : "deserializer", expected);
    }

    private String resolveSchemaRegistryVendor() {
        return KafkaSchemaRegistry.vendor(getSchemaRegistryVendor(), getSchemaFormat());
    }

    /**
     * Adds the SSL, security protocol and SASL options. The sslContextParameters are applied before the SSL endpoint
     * options.
     */
    protected void applySecurityProperties(Properties props) {
        String protocol = resolveSecurityProtocol();
        if (sslContextParameters != null) {
            applySslConfigurationFromContext(props, sslContextParameters);
            logDroppedSslOptions(sslContextParameters);
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
            // the Azure serde needs its flags as Boolean, also when given as a String in additionalProperties
            KafkaSchemaRegistry.applyBooleanFlags(props, resolveSchemaRegistryVendor());
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
     * The Kafka client builds its TLS connections from the ssl.* properties, which cannot carry the options that only
     * the SSLContext of {@code SSLContextParameters.createSSLContext} applies: say so once instead of dropping them in
     * silence. The post-quantum preference of the named groups, which that SSLContext applies when no named groups are
     * configured, is not an option of the user: losing it is only logged at DEBUG.
     */
    private void logDroppedSslOptions(SSLContextParameters sslContextParameters) {
        if (droppedSslOptionsLogged) {
            return;
        }
        droppedSslOptionsLogged = true;

        List<String> dropped = JsseParametersHelper.engineOnlyParameters(sslContextParameters);
        if (!dropped.isEmpty()) {
            LOG.warn("The Kafka client cannot apply the {} of sslContextParameters: it creates its TLS connections from"
                     + " the ssl.* properties, which have no such settings, so the JVM defaults are used instead",
                    String.join(", ", dropped));
        } else if (LOG.isDebugEnabled()) {
            List<String> postQuantum = JsseParametersHelper.postQuantumNamedGroups(sslContextParameters);
            if (postQuantum != null) {
                LOG.debug("The Kafka client cannot apply the post-quantum named group order {} that sslContextParameters"
                          + " prefers on this JVM: it uses the named group order of the JVM instead",
                        postQuantum);
            }
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
            if (keyManagers.getKeyManager() != null) {
                // the Kafka client loads the key store itself from the ssl.keystore.* properties, so an existing
                // key manager cannot be passed on, and ignoring it would connect without the client certificate
                throw new IllegalArgumentException(
                        "A KeyManager set on the key managers of sslContextParameters is not supported by the Kafka"
                                                   + " client: configure a keyStore instead");
            }
            addPropertyIfNotNull(props, SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, keyManagers.getAlgorithm());
            addPropertyIfNotNull(props, SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyManagers.getKeyPassword());
            KeyStoreParameters keyStore = keyManagers.getKeyStore();
            if (keyStore != null) {
                // kafka loads the resource itself and you cannot have a prefix
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
                // kafka loads the resource itself and you cannot have a prefix
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
     * standard Apache Kafka). When schemaRegistryVendor is set, the URL is set under the property of the vendor:
     * schema.registry.url for confluent, apicurio.registry.url for apicurio (the URL of the registry API, such as
     * http://localhost:8080/apis/registry/v3), schema.registry.url for azure (the fully qualified namespace, such as
     * my-namespace.servicebus.windows.net), and endpoint for aws-glue (optional, as Glue is addressed by the AWS
     * region).
     */
    public void setSchemaRegistryURL(String schemaRegistryURL) {
        this.schemaRegistryURL = schemaRegistryURL;
    }

    public boolean isSpecificAvroReader() {
        return specificAvroReader;
    }

    /**
     * This enables the use of a specific Avro reader for use with the in multiple Schema registries documentation with
     * Avro Deserializers implementation. This option is only available externally (not standard Apache Kafka). When
     * schemaRegistryVendor is set, it is set under the property of the vendor (for apicurio
     * apicurio.registry.use-specific-avro-reader, for aws-glue avroRecordType=SPECIFIC_RECORD).
     */
    public void setSpecificAvroReader(boolean specificAvroReader) {
        this.specificAvroReader = specificAvroReader;
    }

    public String getSchemaRegistryVendor() {
        return schemaRegistryVendor;
    }

    /**
     * The schema registry vendor, which sets up the value serializer (producer), the value deserializer (consumer) and
     * the registry properties of the vendor for the schemaFormat, so that you do not need to know the class names or
     * the property names of the vendor. The schemaRegistryURL is set as the registry URL of the vendor. A
     * valueSerializer or valueDeserializer other than the default, and additionalProperties, take precedence. The serde
     * jars of the vendor must be on the classpath, as Camel does not include them. Leave it unset to configure the
     * serializer, deserializer and registry properties yourself.
     */
    public void setSchemaRegistryVendor(String schemaRegistryVendor) {
        this.schemaRegistryVendor = schemaRegistryVendor;
    }

    public String getSchemaFormat() {
        return schemaFormat;
    }

    /**
     * The format of the record values in the schema registry, which selects the serializer and deserializer of the
     * schemaRegistryVendor. Not every vendor supports every format: azure supports only avro. Only used when
     * schemaRegistryVendor is set.
     */
    public void setSchemaFormat(String schemaFormat) {
        this.schemaFormat = schemaFormat;
    }

    public String getSchemaRegistryUsername() {
        return schemaRegistryUsername;
    }

    /**
     * The username to log in to the schema registry with basic auth, with schemaRegistryPassword. Used with
     * schemaRegistryVendor confluent (basic.auth.user.info, which is the API key for Confluent Cloud) and apicurio
     * (apicurio.registry.auth.username). The azure and aws-glue vendors log in with the credentials of the environment
     * instead.
     */
    public void setSchemaRegistryUsername(String schemaRegistryUsername) {
        this.schemaRegistryUsername = schemaRegistryUsername;
    }

    public String getSchemaRegistryPassword() {
        return schemaRegistryPassword;
    }

    /**
     * The password to log in to the schema registry with basic auth, with schemaRegistryUsername. For Confluent Cloud,
     * the API secret.
     */
    public void setSchemaRegistryPassword(String schemaRegistryPassword) {
        this.schemaRegistryPassword = schemaRegistryPassword;
    }

    public String getSchemaRegistryOauthTokenEndpoint() {
        return schemaRegistryOauthTokenEndpoint;
    }

    /**
     * The token endpoint of the OAuth2 server to log in to the schema registry with client credentials, with
     * schemaRegistryOauthClientId and schemaRegistryOauthClientSecret. Used with schemaRegistryVendor confluent
     * (bearer.auth.issuer.endpoint.url) and apicurio (apicurio.registry.auth.service.token.endpoint, such as the token
     * endpoint of a Keycloak realm).
     */
    public void setSchemaRegistryOauthTokenEndpoint(String schemaRegistryOauthTokenEndpoint) {
        this.schemaRegistryOauthTokenEndpoint = schemaRegistryOauthTokenEndpoint;
    }

    public String getSchemaRegistryOauthClientId() {
        return schemaRegistryOauthClientId;
    }

    /**
     * The OAuth2 client id to log in to the schema registry with client credentials. Used with schemaRegistryVendor
     * confluent and apicurio.
     */
    public void setSchemaRegistryOauthClientId(String schemaRegistryOauthClientId) {
        this.schemaRegistryOauthClientId = schemaRegistryOauthClientId;
    }

    public String getSchemaRegistryOauthClientSecret() {
        return schemaRegistryOauthClientSecret;
    }

    /**
     * The OAuth2 client secret to log in to the schema registry with client credentials.
     */
    public void setSchemaRegistryOauthClientSecret(String schemaRegistryOauthClientSecret) {
        this.schemaRegistryOauthClientSecret = schemaRegistryOauthClientSecret;
    }

    public String getSchemaRegistryOauthScope() {
        return schemaRegistryOauthScope;
    }

    /**
     * The OAuth2 scope to ask for when logging in to the schema registry with client credentials (optional).
     */
    public void setSchemaRegistryOauthScope(String schemaRegistryOauthScope) {
        this.schemaRegistryOauthScope = schemaRegistryOauthScope;
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
     * The size of the TCP send buffer (SO_SNDBUF) to use when sending data. If the value is -1, the OS default will be
     * used.
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
     * later rules in the list are ignored. By default, principal names made of a username, a hostname and a Kerberos
     * realm are mapped to the username. For more details on the format, please see the Security Authorization and ACLs
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
     * The list of protocols enabled for SSL connections. The default is TLSv1.2,TLSv1.3. This means that clients and
     * servers will prefer TLSv1.3 if both support it and fallback to TLSv1.2 otherwise (assuming both support at least
     * TLSv1.2). This default should be fine for most use cases. Also see the documentation of sslProtocol to understand
     * how it can impact the TLS version negotiation behavior.
     */
    public void setSslEnabledProtocols(String sslEnabledProtocols) {
        this.sslEnabledProtocols = sslEnabledProtocols;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    /**
     * The file format of the key store file. This is optional for the client. The supported values are JKS, PKCS12 and
     * PEM.
     */
    public void setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    /**
     * The SSL protocol used to generate the SSLContext. The default is TLSv1.3, which should be fine for most use
     * cases. A typical alternative to the default is TLSv1.2. Allowed values for this config are dependent on the JVM.
     * Clients using the defaults for this config and sslEnabledProtocols will downgrade to TLSv1.2 if the server does
     * not support TLSv1.3. If this config is set to TLSv1.2, however, clients will not use TLSv1.3 even if it is one of
     * the values in sslEnabledProtocols and the server only supports TLSv1.3.
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
     * The file format of the trust store file. The supported values are JKS, PKCS12 and PEM.
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
     * The size of the TCP receive buffer (SO_RCVBUF) to use when reading data. If the value is -1, the OS default will
     * be used.
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
     * A list of classes to use as metrics reporters. Implementing the org.apache.kafka.common.metrics.MetricsReporter
     * interface allows plugging in classes that will be notified of new metric creation. When custom reporters are set
     * and org.apache.kafka.common.metrics.JmxReporter is needed, it has to be explicitly added to the list.
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
     * The base amount of time to wait before attempting to reconnect to a given host. This avoids repeatedly connecting
     * to a host in a tight loop. This backoff applies to all connection attempts by the client to a broker. This value
     * is the initial backoff value and will increase exponentially for each consecutive connection failure, up to the
     * reconnectBackoffMaxMs value.
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
     * `camel.component.kafka.additional-properties` (`camel.component.kafka-share.additional-properties` for the Kafka
     * Share component) and the property enclosed in square brackets, like this example:
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
