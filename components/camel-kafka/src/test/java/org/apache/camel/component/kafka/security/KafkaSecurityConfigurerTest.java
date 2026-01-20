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

import java.util.Properties;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KafkaSecurityConfigurer} and {@link KafkaAuthType}.
 */
public class KafkaSecurityConfigurerTest {

    // ========================================================================
    // KafkaAuthType enum tests
    // ========================================================================

    @Test
    public void testAuthTypeNone() {
        KafkaAuthType authType = KafkaAuthType.NONE;
        assertEquals("PLAINTEXT", authType.getDefaultSecurityProtocol());
        assertNull(authType.getSaslMechanism());
        assertNull(authType.getLoginModuleClass());
        assertFalse(authType.isSasl());
        assertFalse(authType.requiresCredentials());
        assertFalse(authType.requiresOAuth());
        assertFalse(authType.requiresSsl());
        assertFalse(authType.requiresKerberos());
        assertFalse(authType.requiresAwsMskIam());
    }

    @Test
    public void testAuthTypePlain() {
        KafkaAuthType authType = KafkaAuthType.PLAIN;
        assertEquals("SASL_SSL", authType.getDefaultSecurityProtocol());
        assertEquals("PLAIN", authType.getSaslMechanism());
        assertEquals("org.apache.kafka.common.security.plain.PlainLoginModule", authType.getLoginModuleClass());
        assertTrue(authType.isSasl());
        assertTrue(authType.requiresCredentials());
        assertFalse(authType.requiresOAuth());
    }

    @Test
    public void testAuthTypeScramSha256() {
        KafkaAuthType authType = KafkaAuthType.SCRAM_SHA_256;
        assertEquals("SASL_SSL", authType.getDefaultSecurityProtocol());
        assertEquals("SCRAM-SHA-256", authType.getSaslMechanism());
        assertEquals("org.apache.kafka.common.security.scram.ScramLoginModule", authType.getLoginModuleClass());
        assertTrue(authType.isSasl());
        assertTrue(authType.requiresCredentials());
    }

    @Test
    public void testAuthTypeScramSha512() {
        KafkaAuthType authType = KafkaAuthType.SCRAM_SHA_512;
        assertEquals("SASL_SSL", authType.getDefaultSecurityProtocol());
        assertEquals("SCRAM-SHA-512", authType.getSaslMechanism());
        assertEquals("org.apache.kafka.common.security.scram.ScramLoginModule", authType.getLoginModuleClass());
        assertTrue(authType.isSasl());
        assertTrue(authType.requiresCredentials());
    }

    @Test
    public void testAuthTypeSsl() {
        KafkaAuthType authType = KafkaAuthType.SSL;
        assertEquals("SSL", authType.getDefaultSecurityProtocol());
        assertNull(authType.getSaslMechanism());
        assertFalse(authType.isSasl());
        assertTrue(authType.requiresSsl());
    }

    @Test
    public void testAuthTypeOAuth() {
        KafkaAuthType authType = KafkaAuthType.OAUTH;
        assertEquals("SASL_SSL", authType.getDefaultSecurityProtocol());
        assertEquals("OAUTHBEARER", authType.getSaslMechanism());
        assertTrue(authType.isSasl());
        assertTrue(authType.requiresOAuth());
        assertFalse(authType.requiresCredentials());
    }

    @Test
    public void testAuthTypeAwsMskIam() {
        KafkaAuthType authType = KafkaAuthType.AWS_MSK_IAM;
        assertEquals("SASL_SSL", authType.getDefaultSecurityProtocol());
        assertEquals("AWS_MSK_IAM", authType.getSaslMechanism());
        assertEquals("software.amazon.msk.auth.iam.IAMLoginModule", authType.getLoginModuleClass());
        assertTrue(authType.isSasl());
        assertTrue(authType.requiresAwsMskIam());
    }

    @Test
    public void testAuthTypeKerberos() {
        KafkaAuthType authType = KafkaAuthType.KERBEROS;
        assertEquals("SASL_SSL", authType.getDefaultSecurityProtocol());
        assertEquals("GSSAPI", authType.getSaslMechanism());
        assertTrue(authType.isSasl());
        assertTrue(authType.requiresKerberos());
    }

    // ========================================================================
    // KafkaSecurityConfigurer - Factory method tests
    // ========================================================================

    @Test
    public void testNoAuth() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.noAuth();
        assertEquals(KafkaAuthType.NONE, configurer.getAuthType());
        assertEquals("PLAINTEXT", configurer.getSecurityProtocol());
        assertNull(configurer.buildJaasConfig());
    }

    @Test
    public void testPlainFactory() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.plain("myuser", "mypass");
        assertEquals(KafkaAuthType.PLAIN, configurer.getAuthType());
        assertEquals("SASL_SSL", configurer.getSecurityProtocol());
        assertEquals("PLAIN", configurer.getSaslMechanism());
    }

    @Test
    public void testScramSha512Factory() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.scramSha512("myuser", "mypass");
        assertEquals(KafkaAuthType.SCRAM_SHA_512, configurer.getAuthType());
        assertEquals("SASL_SSL", configurer.getSecurityProtocol());
        assertEquals("SCRAM-SHA-512", configurer.getSaslMechanism());
    }

    @Test
    public void testScramSha256Factory() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.scramSha256("myuser", "mypass");
        assertEquals(KafkaAuthType.SCRAM_SHA_256, configurer.getAuthType());
        assertEquals("SCRAM-SHA-256", configurer.getSaslMechanism());
    }

    @Test
    public void testSslFactory() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.ssl();
        assertEquals(KafkaAuthType.SSL, configurer.getAuthType());
        assertEquals("SSL", configurer.getSecurityProtocol());
        assertNull(configurer.getSaslMechanism());
    }

    @Test
    public void testAwsMskIamFactory() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.awsMskIam();
        assertEquals(KafkaAuthType.AWS_MSK_IAM, configurer.getAuthType());
        assertEquals("AWS_MSK_IAM", configurer.getSaslMechanism());
    }

    @Test
    public void testOAuthFactory() {
        KafkaSecurityConfigurer configurer
                = KafkaSecurityConfigurer.oauth("clientId", "clientSecret", "https://auth.example.com/token");
        assertEquals(KafkaAuthType.OAUTH, configurer.getAuthType());
        assertEquals("OAUTHBEARER", configurer.getSaslMechanism());
    }

    @Test
    public void testKerberosFactory() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.kerberos("kafka/host@REALM", "/path/to/keytab");
        assertEquals(KafkaAuthType.KERBEROS, configurer.getAuthType());
        assertEquals("GSSAPI", configurer.getSaslMechanism());
    }

    // ========================================================================
    // JAAS config generation tests
    // ========================================================================

    @Test
    public void testBuildJaasConfigPlain() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.plain("myuser", "mypass");
        String jaasConfig = configurer.buildJaasConfig();

        assertNotNull(jaasConfig);
        assertTrue(jaasConfig.contains("org.apache.kafka.common.security.plain.PlainLoginModule required"));
        assertTrue(jaasConfig.contains("username=\"myuser\""));
        assertTrue(jaasConfig.contains("password=\"mypass\""));
        assertTrue(jaasConfig.endsWith(";"));
    }

    @Test
    public void testBuildJaasConfigScramSha512() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.scramSha512("myuser", "secret123");
        String jaasConfig = configurer.buildJaasConfig();

        assertNotNull(jaasConfig);
        assertTrue(jaasConfig.contains("org.apache.kafka.common.security.scram.ScramLoginModule required"));
        assertTrue(jaasConfig.contains("username=\"myuser\""));
        assertTrue(jaasConfig.contains("password=\"secret123\""));
    }

    @Test
    public void testBuildJaasConfigAwsMskIam() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.awsMskIam();
        String jaasConfig = configurer.buildJaasConfig();

        assertNotNull(jaasConfig);
        assertTrue(jaasConfig.contains("software.amazon.msk.auth.iam.IAMLoginModule required"));
        // AWS MSK IAM doesn't need username/password
        assertFalse(jaasConfig.contains("username="));
    }

    @Test
    public void testBuildJaasConfigOAuth() {
        KafkaSecurityConfigurer configurer
                = KafkaSecurityConfigurer.oauth("my-client", "my-secret", "https://auth.example.com/token")
                        .withOAuthScope("kafka");
        String jaasConfig = configurer.buildJaasConfig();

        assertNotNull(jaasConfig);
        assertTrue(jaasConfig.contains("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"));
        assertTrue(jaasConfig.contains("clientId=\"my-client\""));
        assertTrue(jaasConfig.contains("clientSecret=\"my-secret\""));
        assertTrue(jaasConfig.contains("oauth.token.endpoint.uri=\"https://auth.example.com/token\""));
        assertTrue(jaasConfig.contains("oauth.scope=\"kafka\""));
    }

    @Test
    public void testBuildJaasConfigKerberos() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.kerberos("kafka/host@REALM", "/path/to/keytab");
        String jaasConfig = configurer.buildJaasConfig();

        assertNotNull(jaasConfig);
        assertTrue(jaasConfig.contains("com.sun.security.auth.module.Krb5LoginModule required"));
        assertTrue(jaasConfig.contains("useKeyTab=true"));
        assertTrue(jaasConfig.contains("storeKey=true"));
        assertTrue(jaasConfig.contains("keyTab=\"/path/to/keytab\""));
        assertTrue(jaasConfig.contains("principal=\"kafka/host@REALM\""));
    }

    @Test
    public void testBuildJaasConfigNone() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.noAuth();
        assertNull(configurer.buildJaasConfig());
    }

    @Test
    public void testBuildJaasConfigSsl() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.ssl();
        assertNull(configurer.buildJaasConfig());
    }

    @Test
    public void testCustomJaasConfig() {
        String customConfig = "org.custom.LoginModule required custom=true;";
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.PLAIN)
                .withCustomJaasConfig(customConfig);

        assertEquals(customConfig, configurer.buildJaasConfig());
    }

    // ========================================================================
    // Security protocol tests
    // ========================================================================

    @Test
    public void testSecurityProtocolWithSsl() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.scramSha512("user", "pass").withSsl(true);
        assertEquals("SASL_SSL", configurer.getSecurityProtocol());
    }

    @Test
    public void testSecurityProtocolWithoutSsl() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.scramSha512("user", "pass").withSsl(false);
        assertEquals("SASL_PLAINTEXT", configurer.getSecurityProtocol());
    }

    @Test
    public void testSecurityProtocolNoneWithSsl() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.noAuth().withSsl(true);
        assertEquals("SSL", configurer.getSecurityProtocol());
    }

    @Test
    public void testSecurityProtocolNoneWithoutSsl() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.noAuth().withSsl(false);
        assertEquals("PLAINTEXT", configurer.getSecurityProtocol());
    }

    // ========================================================================
    // Escaping tests
    // ========================================================================

    @Test
    public void testEscapeSpecialCharactersInPassword() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.plain("user", "pass\"with\\special");
        String jaasConfig = configurer.buildJaasConfig();

        // Verify escaping
        assertTrue(jaasConfig.contains("password=\"pass\\\"with\\\\special\""));
    }

    @Test
    public void testEscapeQuotesInUsername() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.plain("user\"name", "pass");
        String jaasConfig = configurer.buildJaasConfig();

        assertTrue(jaasConfig.contains("username=\"user\\\"name\""));
    }

    // ========================================================================
    // Validation tests
    // ========================================================================

    @Test
    public void testValidateMissingUsername() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.PLAIN)
                .withCredentials(null, "password");

        assertThrows(IllegalArgumentException.class, configurer::validate);
    }

    @Test
    public void testValidateMissingPassword() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.PLAIN)
                .withCredentials("username", null);

        assertThrows(IllegalArgumentException.class, configurer::validate);
    }

    @Test
    public void testValidateMissingOAuthClientId() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.OAUTH)
                .withOAuth(null, "secret", "https://auth.example.com/token");

        assertThrows(IllegalArgumentException.class, configurer::validate);
    }

    @Test
    public void testValidateMissingOAuthClientSecret() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.OAUTH)
                .withOAuth("clientId", null, "https://auth.example.com/token");

        assertThrows(IllegalArgumentException.class, configurer::validate);
    }

    @Test
    public void testValidateMissingOAuthTokenEndpoint() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.OAUTH)
                .withOAuth("clientId", "secret", null);

        assertThrows(IllegalArgumentException.class, configurer::validate);
    }

    @Test
    public void testValidateMissingKerberosPrincipal() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.KERBEROS)
                .withKerberos(null, "/path/to/keytab");

        assertThrows(IllegalArgumentException.class, configurer::validate);
    }

    @Test
    public void testValidateMissingKerberosKeytab() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.forAuthType(KafkaAuthType.KERBEROS)
                .withKerberos("principal", null);

        assertThrows(IllegalArgumentException.class, configurer::validate);
    }

    // ========================================================================
    // Integration with KafkaConfiguration tests
    // ========================================================================

    @Test
    public void testConfigureKafkaConfiguration() {
        KafkaConfiguration config = new KafkaConfiguration();

        KafkaSecurityConfigurer.scramSha512("myuser", "mypass")
                .configure(config);

        assertEquals("SASL_SSL", config.getSecurityProtocol());
        assertEquals("SCRAM-SHA-512", config.getSaslMechanism());
        assertNotNull(config.getSaslJaasConfig());
        assertTrue(config.getSaslJaasConfig().contains("ScramLoginModule"));
    }

    @Test
    public void testConfigureKafkaConfigurationPlain() {
        KafkaConfiguration config = new KafkaConfiguration();

        KafkaSecurityConfigurer.plain("user", "pass")
                .configure(config);

        assertEquals("SASL_SSL", config.getSecurityProtocol());
        assertEquals("PLAIN", config.getSaslMechanism());
        assertTrue(config.getSaslJaasConfig().contains("PlainLoginModule"));
    }

    @Test
    public void testConfigureKafkaConfigurationAwsMsk() {
        KafkaConfiguration config = new KafkaConfiguration();

        KafkaSecurityConfigurer.awsMskIam()
                .configure(config);

        assertEquals("SASL_SSL", config.getSecurityProtocol());
        assertEquals("AWS_MSK_IAM", config.getSaslMechanism());
        assertTrue(config.getSaslJaasConfig().contains("IAMLoginModule"));
    }

    @Test
    public void testConfigureKafkaConfigurationNone() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSecurityProtocol("SOME_VALUE"); // Should be overwritten

        KafkaSecurityConfigurer.noAuth()
                .configure(config);

        assertEquals("PLAINTEXT", config.getSecurityProtocol());
        assertNull(config.getSaslJaasConfig());
    }

    @Test
    public void testConfigureKafkaConfigurationSsl() {
        KafkaConfiguration config = new KafkaConfiguration();

        KafkaSecurityConfigurer.ssl()
                .configure(config);

        assertEquals("SSL", config.getSecurityProtocol());
        // SSL auth type doesn't modify the existing SASL mechanism (which has a default value)
        // The important thing is that no JAAS config is set
        assertNull(config.getSaslJaasConfig());
    }

    // ========================================================================
    // toProperties() tests
    // ========================================================================

    @Test
    public void testToProperties() {
        Properties props = KafkaSecurityConfigurer.scramSha512("user", "pass").toProperties();

        assertEquals("SASL_SSL", props.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("SCRAM-SHA-512", props.getProperty(SaslConfigs.SASL_MECHANISM));
        assertNotNull(props.getProperty(SaslConfigs.SASL_JAAS_CONFIG));
    }

    @Test
    public void testToPropertiesNoAuth() {
        Properties props = KafkaSecurityConfigurer.noAuth().toProperties();

        assertEquals("PLAINTEXT", props.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertNull(props.getProperty(SaslConfigs.SASL_MECHANISM));
        assertNull(props.getProperty(SaslConfigs.SASL_JAAS_CONFIG));
    }

    // ========================================================================
    // toString() test
    // ========================================================================

    @Test
    public void testToString() {
        KafkaSecurityConfigurer configurer = KafkaSecurityConfigurer.scramSha512("user", "pass");
        String str = configurer.toString();

        assertTrue(str.contains("SCRAM_SHA_512"));
        assertTrue(str.contains("useSsl=true"));
    }
}
