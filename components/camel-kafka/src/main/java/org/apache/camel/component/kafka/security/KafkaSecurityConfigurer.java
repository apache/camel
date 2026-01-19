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
package org.apache.camel.component.kafka.security;

import java.util.Objects;
import java.util.Properties;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;

/**
 * Builder for Kafka security configuration.
 * <p>
 * This class simplifies the creation of JAAS configurations and security settings for various Kafka authentication
 * mechanisms. Instead of manually constructing complex JAAS configuration strings, users can use this builder with
 * simple method calls.
 * </p>
 *
 * <p>
 * Usage examples:
 * </p>
 *
 * <pre>
 * // SCRAM-SHA-512 authentication
 * KafkaSecurityConfigurer.forAuthType(KafkaAuthType.SCRAM_SHA_512)
 *         .withCredentials("username", "password")
 *         .configure(kafkaConfiguration);
 *
 * // Using convenience methods
 * KafkaSecurityConfigurer.scramSha512("username", "password")
 *         .configure(kafkaConfiguration);
 *
 * // OAuth 2.0 authentication
 * KafkaSecurityConfigurer.forAuthType(KafkaAuthType.OAUTH)
 *         .withOAuth("clientId", "clientSecret", "https://auth.example.com/token")
 *         .configure(kafkaConfiguration);
 *
 * // AWS MSK IAM authentication
 * KafkaSecurityConfigurer.awsMskIam()
 *         .configure(kafkaConfiguration);
 *
 * // Getting properties directly (for non-Camel usage)
 * Properties props = KafkaSecurityConfigurer.scramSha512("user", "pass").toProperties();
 * </pre>
 *
 * <p>
 * This class is designed to be backward compatible with existing security configurations. If you prefer to use the
 * traditional approach with explicit {@code securityProtocol}, {@code saslMechanism}, and {@code saslJaasConfig}
 * properties, those will continue to work as before.
 * </p>
 */
public class KafkaSecurityConfigurer {

    private final KafkaAuthType authType;
    private String username;
    private String password;
    private boolean useSsl = true;

    // OAuth properties
    private String oauthClientId;
    private String oauthClientSecret;
    private String oauthTokenEndpointUri;
    private String oauthScope;

    // Kerberos properties
    private String kerberosPrincipal;
    private String kerberosKeytab;
    private boolean kerberosUseKeyTab = true;
    private boolean kerberosStoreKey = true;

    // Custom JAAS config (for advanced use cases)
    private String customJaasConfig;

    private KafkaSecurityConfigurer(KafkaAuthType authType) {
        this.authType = Objects.requireNonNull(authType, "authType cannot be null");
        // For NONE auth type, default to no SSL (PLAINTEXT)
        // For other auth types, default to SSL for security
        this.useSsl = (authType != KafkaAuthType.NONE);
    }

    /**
     * Creates a new security configurer for the specified authentication type.
     *
     * @param  authType the authentication type to use
     * @return          a new KafkaSecurityConfigurer instance
     */
    public static KafkaSecurityConfigurer forAuthType(KafkaAuthType authType) {
        return new KafkaSecurityConfigurer(authType);
    }

    /**
     * Creates a new security configurer for no authentication.
     *
     * @return a new KafkaSecurityConfigurer instance configured for no authentication
     */
    public static KafkaSecurityConfigurer noAuth() {
        return new KafkaSecurityConfigurer(KafkaAuthType.NONE);
    }

    /**
     * Creates a new security configurer for PLAIN authentication.
     *
     * @param  username the SASL username
     * @param  password the SASL password
     * @return          a new KafkaSecurityConfigurer instance configured for PLAIN authentication
     */
    public static KafkaSecurityConfigurer plain(String username, String password) {
        return new KafkaSecurityConfigurer(KafkaAuthType.PLAIN)
                .withCredentials(username, password);
    }

    /**
     * Creates a new security configurer for SCRAM-SHA-512 authentication.
     *
     * @param  username the SASL username
     * @param  password the SASL password
     * @return          a new KafkaSecurityConfigurer instance configured for SCRAM-SHA-512 authentication
     */
    public static KafkaSecurityConfigurer scramSha512(String username, String password) {
        return new KafkaSecurityConfigurer(KafkaAuthType.SCRAM_SHA_512)
                .withCredentials(username, password);
    }

    /**
     * Creates a new security configurer for SCRAM-SHA-256 authentication.
     *
     * @param  username the SASL username
     * @param  password the SASL password
     * @return          a new KafkaSecurityConfigurer instance configured for SCRAM-SHA-256 authentication
     */
    public static KafkaSecurityConfigurer scramSha256(String username, String password) {
        return new KafkaSecurityConfigurer(KafkaAuthType.SCRAM_SHA_256)
                .withCredentials(username, password);
    }

    /**
     * Creates a new security configurer for SSL/mTLS authentication.
     *
     * @return a new KafkaSecurityConfigurer instance configured for SSL authentication
     */
    public static KafkaSecurityConfigurer ssl() {
        return new KafkaSecurityConfigurer(KafkaAuthType.SSL);
    }

    /**
     * Creates a new security configurer for AWS MSK IAM authentication.
     *
     * @return a new KafkaSecurityConfigurer instance configured for AWS MSK IAM authentication
     */
    public static KafkaSecurityConfigurer awsMskIam() {
        return new KafkaSecurityConfigurer(KafkaAuthType.AWS_MSK_IAM);
    }

    /**
     * Creates a new security configurer for OAuth 2.0 authentication.
     *
     * @param  clientId         the OAuth client ID
     * @param  clientSecret     the OAuth client secret
     * @param  tokenEndpointUri the OAuth token endpoint URI
     * @return                  a new KafkaSecurityConfigurer instance configured for OAuth authentication
     */
    public static KafkaSecurityConfigurer oauth(String clientId, String clientSecret, String tokenEndpointUri) {
        return new KafkaSecurityConfigurer(KafkaAuthType.OAUTH)
                .withOAuth(clientId, clientSecret, tokenEndpointUri);
    }

    /**
     * Creates a new security configurer for Kerberos authentication.
     *
     * @param  principal the Kerberos principal
     * @param  keytab    the path to the Kerberos keytab file
     * @return           a new KafkaSecurityConfigurer instance configured for Kerberos authentication
     */
    public static KafkaSecurityConfigurer kerberos(String principal, String keytab) {
        return new KafkaSecurityConfigurer(KafkaAuthType.KERBEROS)
                .withKerberos(principal, keytab);
    }

    /**
     * Sets username and password credentials.
     *
     * @param  username the SASL username
     * @param  password the SASL password
     * @return          this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withCredentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    /**
     * Enables or disables SSL encryption.
     * <p>
     * For SASL authentication types, this controls whether to use SASL_SSL (default) or SASL_PLAINTEXT. For no
     * authentication (NONE), this controls whether to use SSL or PLAINTEXT.
     * </p>
     *
     * @param  useSsl true to use SSL encryption (default), false for plaintext
     * @return        this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withSsl(boolean useSsl) {
        this.useSsl = useSsl;
        return this;
    }

    /**
     * Configures OAuth 2.0 authentication parameters.
     *
     * @param  clientId         the OAuth client ID
     * @param  clientSecret     the OAuth client secret
     * @param  tokenEndpointUri the OAuth token endpoint URI
     * @return                  this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withOAuth(String clientId, String clientSecret, String tokenEndpointUri) {
        this.oauthClientId = clientId;
        this.oauthClientSecret = clientSecret;
        this.oauthTokenEndpointUri = tokenEndpointUri;
        return this;
    }

    /**
     * Sets the OAuth scope.
     *
     * @param  scope the OAuth scope
     * @return       this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withOAuthScope(String scope) {
        this.oauthScope = scope;
        return this;
    }

    /**
     * Configures Kerberos authentication parameters.
     *
     * @param  principal the Kerberos principal
     * @param  keytab    the path to the Kerberos keytab file
     * @return           this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withKerberos(String principal, String keytab) {
        this.kerberosPrincipal = principal;
        this.kerberosKeytab = keytab;
        return this;
    }

    /**
     * Sets whether to use keytab for Kerberos authentication.
     *
     * @param  useKeyTab true to use keytab (default), false otherwise
     * @return           this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withKerberosUseKeyTab(boolean useKeyTab) {
        this.kerberosUseKeyTab = useKeyTab;
        return this;
    }

    /**
     * Sets whether to store the Kerberos key.
     *
     * @param  storeKey true to store the key (default), false otherwise
     * @return          this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withKerberosStoreKey(boolean storeKey) {
        this.kerberosStoreKey = storeKey;
        return this;
    }

    /**
     * Sets a custom JAAS configuration string.
     * <p>
     * This overrides the auto-generated JAAS configuration. Use this for advanced use cases not covered by the standard
     * authentication types.
     * </p>
     *
     * @param  jaasConfig the custom JAAS configuration string
     * @return            this configurer instance for method chaining
     */
    public KafkaSecurityConfigurer withCustomJaasConfig(String jaasConfig) {
        this.customJaasConfig = jaasConfig;
        return this;
    }

    /**
     * Returns the authentication type.
     *
     * @return the authentication type
     */
    public KafkaAuthType getAuthType() {
        return authType;
    }

    /**
     * Builds the JAAS configuration string based on the authentication type and configured parameters.
     *
     * @return                          the JAAS configuration string, or null if not applicable
     * @throws IllegalArgumentException if required parameters are missing for the authentication type
     */
    public String buildJaasConfig() {
        if (ObjectHelper.isNotEmpty(customJaasConfig)) {
            return customJaasConfig;
        }

        if (authType == KafkaAuthType.NONE || authType == KafkaAuthType.SSL) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(authType.getLoginModuleClass()).append(" required");

        switch (authType) {
            case PLAIN:
            case SCRAM_SHA_256:
            case SCRAM_SHA_512:
                validateCredentials();
                sb.append(" username=\"").append(escapeJaasValue(username)).append("\"");
                sb.append(" password=\"").append(escapeJaasValue(password)).append("\"");
                break;

            case OAUTH:
                validateOAuth();
                sb.append(" clientId=\"").append(escapeJaasValue(oauthClientId)).append("\"");
                sb.append(" clientSecret=\"").append(escapeJaasValue(oauthClientSecret)).append("\"");
                sb.append(" oauth.token.endpoint.uri=\"").append(oauthTokenEndpointUri).append("\"");
                if (ObjectHelper.isNotEmpty(oauthScope)) {
                    sb.append(" oauth.scope=\"").append(oauthScope).append("\"");
                }
                break;

            case AWS_MSK_IAM:
                // AWS MSK IAM uses instance credentials, no additional config needed
                break;

            case KERBEROS:
                validateKerberos();
                sb.append(" useKeyTab=").append(kerberosUseKeyTab);
                sb.append(" storeKey=").append(kerberosStoreKey);
                sb.append(" keyTab=\"").append(escapeJaasValue(kerberosKeytab)).append("\"");
                sb.append(" principal=\"").append(escapeJaasValue(kerberosPrincipal)).append("\"");
                break;

            default:
                throw new IllegalStateException("Unsupported auth type: " + authType);
        }

        sb.append(";");
        return sb.toString();
    }

    /**
     * Returns the appropriate security protocol based on the authentication type and SSL setting.
     *
     * @return the security protocol (e.g., "SASL_SSL", "SSL", "PLAINTEXT", "SASL_PLAINTEXT")
     */
    public String getSecurityProtocol() {
        if (authType == KafkaAuthType.NONE) {
            return useSsl ? "SSL" : "PLAINTEXT";
        }
        if (authType == KafkaAuthType.SSL) {
            return "SSL";
        }
        // For SASL auth types
        return useSsl ? "SASL_SSL" : "SASL_PLAINTEXT";
    }

    /**
     * Returns the SASL mechanism for this authentication type.
     *
     * @return the SASL mechanism, or null if not a SASL auth type
     */
    public String getSaslMechanism() {
        return authType.getSaslMechanism();
    }

    /**
     * Applies this security configuration to a KafkaConfiguration instance.
     * <p>
     * This method sets the {@code securityProtocol}, {@code saslMechanism}, and {@code saslJaasConfig} properties on
     * the provided configuration object.
     * </p>
     *
     * @param configuration the KafkaConfiguration to configure
     */
    public void configure(KafkaConfiguration configuration) {
        configuration.setSecurityProtocol(getSecurityProtocol());

        if (authType.isSasl()) {
            configuration.setSaslMechanism(getSaslMechanism());
            String jaasConfig = buildJaasConfig();
            if (jaasConfig != null) {
                configuration.setSaslJaasConfig(jaasConfig);
            }
        }
    }

    /**
     * Creates a Properties object with security settings.
     * <p>
     * This is useful for direct Kafka client usage or other non-Camel scenarios.
     * </p>
     *
     * @return a Properties object containing the security configuration
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getSecurityProtocol());

        if (authType.isSasl()) {
            props.setProperty(SaslConfigs.SASL_MECHANISM, getSaslMechanism());
            String jaasConfig = buildJaasConfig();
            if (jaasConfig != null) {
                props.setProperty(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            }
        }

        return props;
    }

    /**
     * Validates that required properties are set based on the authentication type.
     *
     * @throws IllegalArgumentException if required properties are missing
     */
    public void validate() {
        if (authType.requiresCredentials()) {
            validateCredentials();
        }
        if (authType.requiresOAuth()) {
            validateOAuth();
        }
        if (authType.requiresKerberos()) {
            validateKerberos();
        }
    }

    private void validateCredentials() {
        if (ObjectHelper.isEmpty(username)) {
            throw new IllegalArgumentException(
                    "Username is required for " + authType + " authentication");
        }
        if (ObjectHelper.isEmpty(password)) {
            throw new IllegalArgumentException(
                    "Password is required for " + authType + " authentication");
        }
    }

    private void validateOAuth() {
        if (ObjectHelper.isEmpty(oauthClientId)) {
            throw new IllegalArgumentException("OAuth clientId is required for OAUTH authentication");
        }
        if (ObjectHelper.isEmpty(oauthClientSecret)) {
            throw new IllegalArgumentException("OAuth clientSecret is required for OAUTH authentication");
        }
        if (ObjectHelper.isEmpty(oauthTokenEndpointUri)) {
            throw new IllegalArgumentException("OAuth tokenEndpointUri is required for OAUTH authentication");
        }
    }

    private void validateKerberos() {
        if (ObjectHelper.isEmpty(kerberosPrincipal)) {
            throw new IllegalArgumentException("Kerberos principal is required for KERBEROS authentication");
        }
        if (ObjectHelper.isEmpty(kerberosKeytab)) {
            throw new IllegalArgumentException("Kerberos keytab is required for KERBEROS authentication");
        }
    }

    /**
     * Escapes special characters in JAAS config values.
     * <p>
     * Backslashes and quotes need to be escaped in JAAS configuration values.
     * </p>
     *
     * @param  value the value to escape
     * @return       the escaped value
     */
    private String escapeJaasValue(String value) {
        if (value == null) {
            return "";
        }
        // Escape backslashes first, then quotes
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return "KafkaSecurityConfigurer[authType=" + authType + ", useSsl=" + useSsl + "]";
    }
}
