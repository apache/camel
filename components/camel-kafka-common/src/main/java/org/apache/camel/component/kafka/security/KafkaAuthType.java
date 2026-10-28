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

/**
 * Supported Kafka authentication mechanisms.
 * <p>
 * This enum provides a simplified way to configure Kafka authentication without having to manually construct JAAS
 * configuration strings. When used with {@link KafkaSecurityConfigurer}, it automatically derives the correct
 * {@code securityProtocol}, {@code saslMechanism}, and {@code saslJaasConfig} values.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * // Using the enum directly
 * KafkaAuthType authType = KafkaAuthType.SCRAM_SHA_512;
 *
 * // With KafkaSecurityConfigurer
 * KafkaSecurityConfigurer.forAuthType(KafkaAuthType.SCRAM_SHA_512)
 *         .withCredentials("username", "password")
 *         .configure(kafkaConfiguration);
 * </pre>
 */
public enum KafkaAuthType {

    /**
     * No authentication (insecure, typically for development only).
     * <p>
     * Uses PLAINTEXT security protocol with no SASL mechanism.
     * </p>
     */
    NONE("PLAINTEXT", null, null),

    /**
     * SASL/PLAIN authentication with username and password.
     * <p>
     * Uses PlainLoginModule for authentication. Credentials are sent in clear text, so this should only be used with
     * SSL/TLS encryption (SASL_SSL).
     * </p>
     */
    PLAIN("SASL_SSL", "PLAIN", "org.apache.kafka.common.security.plain.PlainLoginModule"),

    /**
     * SASL/SCRAM-SHA-256 authentication.
     * <p>
     * Uses ScramLoginModule with SHA-256 hashing. More secure than PLAIN as passwords are not sent in clear text.
     * </p>
     */
    SCRAM_SHA_256("SASL_SSL", "SCRAM-SHA-256", "org.apache.kafka.common.security.scram.ScramLoginModule"),

    /**
     * SASL/SCRAM-SHA-512 authentication.
     * <p>
     * Uses ScramLoginModule with SHA-512 hashing. More secure than SCRAM-SHA-256 due to stronger hash algorithm.
     * </p>
     */
    SCRAM_SHA_512("SASL_SSL", "SCRAM-SHA-512", "org.apache.kafka.common.security.scram.ScramLoginModule"),

    /**
     * SSL/TLS authentication with client certificates (mTLS).
     * <p>
     * Uses SSL security protocol. Requires keystore and truststore configuration for mutual TLS authentication.
     * </p>
     */
    SSL("SSL", null, null),

    /**
     * OAuth 2.0 / OIDC authentication.
     * <p>
     * Uses OAuthBearerLoginModule for token-based authentication. Requires OAuth client credentials and token endpoint
     * configuration.
     * </p>
     */
    OAUTH("SASL_SSL", "OAUTHBEARER", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule"),

    /**
     * AWS MSK IAM authentication.
     * <p>
     * Uses AWS IAM for authentication with Amazon MSK. Requires the aws-msk-iam-auth library on the classpath. Uses
     * instance credentials or configured AWS credentials.
     * </p>
     */
    AWS_MSK_IAM("SASL_SSL", "AWS_MSK_IAM", "software.amazon.msk.auth.iam.IAMLoginModule"),

    /**
     * Kerberos/GSSAPI authentication.
     * <p>
     * Uses Kerberos for authentication. Requires Kerberos configuration including principal and keytab.
     * </p>
     */
    KERBEROS("SASL_SSL", "GSSAPI", "com.sun.security.auth.module.Krb5LoginModule");

    private final String defaultSecurityProtocol;
    private final String saslMechanism;
    private final String loginModuleClass;

    KafkaAuthType(String defaultSecurityProtocol, String saslMechanism, String loginModuleClass) {
        this.defaultSecurityProtocol = defaultSecurityProtocol;
        this.saslMechanism = saslMechanism;
        this.loginModuleClass = loginModuleClass;
    }

    /**
     * Returns the default security protocol for this authentication type.
     *
     * @return the default security protocol (e.g., "SASL_SSL", "SSL", "PLAINTEXT")
     */
    public String getDefaultSecurityProtocol() {
        return defaultSecurityProtocol;
    }

    /**
     * Returns the SASL mechanism for this authentication type.
     *
     * @return the SASL mechanism (e.g., "PLAIN", "SCRAM-SHA-512"), or null if not a SASL auth type
     */
    public String getSaslMechanism() {
        return saslMechanism;
    }

    /**
     * Returns the fully qualified class name of the JAAS login module.
     *
     * @return the login module class name, or null if not applicable
     */
    public String getLoginModuleClass() {
        return loginModuleClass;
    }

    /**
     * Returns whether this authentication type requires username and password credentials.
     *
     * @return true if credentials are required (PLAIN, SCRAM-SHA-256, SCRAM-SHA-512)
     */
    public boolean requiresCredentials() {
        return this == PLAIN || this == SCRAM_SHA_256 || this == SCRAM_SHA_512;
    }

    /**
     * Returns whether this authentication type uses OAuth 2.0.
     *
     * @return true if OAuth authentication is used
     */
    public boolean requiresOAuth() {
        return this == OAUTH;
    }

    /**
     * Returns whether this authentication type uses SSL/mTLS without SASL.
     *
     * @return true if SSL-only authentication is used
     */
    public boolean requiresSsl() {
        return this == SSL;
    }

    /**
     * Returns whether this authentication type uses a SASL mechanism.
     *
     * @return true if a SASL mechanism is used
     */
    public boolean isSasl() {
        return saslMechanism != null;
    }

    /**
     * Returns whether this authentication type uses Kerberos.
     *
     * @return true if Kerberos authentication is used
     */
    public boolean requiresKerberos() {
        return this == KERBEROS;
    }

    /**
     * Returns whether this authentication type uses AWS MSK IAM.
     *
     * @return true if AWS MSK IAM authentication is used
     */
    public boolean requiresAwsMskIam() {
        return this == AWS_MSK_IAM;
    }
}
