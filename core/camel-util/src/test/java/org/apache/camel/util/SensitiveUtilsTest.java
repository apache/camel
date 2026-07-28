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

package org.apache.camel.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveUtilsTest {

    @Test
    void testContainsSensitive() {
        assertThat(SensitiveUtils.containsSensitive("accessKey")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("accesstoken")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("authorizationtoken")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("clientsecret")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("passphrase")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("password")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("sasljaasconfig")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("sasl-jaas-config")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("saslJaasConfig")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("secret")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("secretkey")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("secret-key")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("secretKey")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("secret-Key")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("access-key")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("accessKey")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("access-Key")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("client-secret")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("authorization-token")).isTrue();
        assertThat(SensitiveUtils.containsSensitive("foo.bar.accessKey")).isTrue();

        assertThat(SensitiveUtils.containsSensitive("foo.bar.accessKey.")).isFalse();
        assertThat(SensitiveUtils.containsSensitive("foo")).isFalse();
        assertThat(SensitiveUtils.containsSensitive("bar")).isFalse();
    }

    @Test
    void maskUserInfoCredentialsMasksPasswordInCommonSchemes() {
        assertThat(SensitiveUtils.maskUserInfoCredentials("mongodb://user:pass@host:27017/db", "xxxxx"))
                .isEqualTo("mongodb://user:xxxxx@host:27017/db");
        assertThat(SensitiveUtils.maskUserInfoCredentials("mongodb+srv://user:p%40ss@cluster/db", "xxxxx"))
                .isEqualTo("mongodb+srv://user:xxxxx@cluster/db");
        assertThat(SensitiveUtils.maskUserInfoCredentials("amqp://admin:secret@broker:5672/vhost", "xxxxx"))
                .isEqualTo("amqp://admin:xxxxx@broker:5672/vhost");
        assertThat(SensitiveUtils.maskUserInfoCredentials("redis://default:s3cret@redis:6379/0", "xxxxx"))
                .isEqualTo("redis://default:xxxxx@redis:6379/0");
        assertThat(SensitiveUtils.maskUserInfoCredentials("jdbc:mysql://dbuser:dbpass@localhost:3306/app", "xxxxx"))
                .isEqualTo("jdbc:mysql://dbuser:xxxxx@localhost:3306/app");
        assertThat(SensitiveUtils.maskUserInfoCredentials("sftp://USERNAME:PASSWORD@sftp.server.test", "xxxxx"))
                .isEqualTo("sftp://USERNAME:xxxxx@sftp.server.test");
        // Redis / some brokers allow empty username with password-only userinfo
        assertThat(SensitiveUtils.maskUserInfoCredentials("redis://:s3cret@redis:6379/0", "xxxxx"))
                .isEqualTo("redis://:xxxxx@redis:6379/0");
        assertThat(SensitiveUtils.maskUserInfoCredentials("http://user:pass:word@host/path", "xxxxx"))
                .isEqualTo("http://user:xxxxx@host/path");
    }

    @Test
    void maskValueShapeHelpersSkipRegexWhenMarkersAbsent() {
        assertThat(SensitiveUtils.maskUserInfoCredentials("Hello World", "xxxxx")).isEqualTo("Hello World");
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks("plain log line", "xxxxx")).isEqualTo("plain log line");
        assertThat(SensitiveUtils.maskSensitiveValueShapes("Hello World", "xxxxx")).isEqualTo("Hello World");
    }

    @Test
    void maskUserInfoCredentialsPreservesNonCredentialUris() {
        assertThat(SensitiveUtils.maskUserInfoCredentials("https://example.com/path", "xxxxx"))
                .isEqualTo("https://example.com/path");
        assertThat(SensitiveUtils.maskUserInfoCredentials("mongodb://user@host:27017/db", "xxxxx"))
                .isEqualTo("mongodb://user@host:27017/db");
        assertThat(SensitiveUtils.maskUserInfoCredentials("mailto:user@example.com", "xxxxx"))
                .isEqualTo("mailto:user@example.com");
        assertThat(SensitiveUtils.maskUserInfoCredentials("see http://example.com/path:foo for details", "xxxxx"))
                .isEqualTo("see http://example.com/path:foo for details");
    }

    @Test
    void maskUserInfoCredentialsMasksMultipleAndEmbedded() {
        String source = "primary=mongodb://u1:p1@h1/db secondary=amqp://u2:p2@h2/v";
        assertThat(SensitiveUtils.maskUserInfoCredentials(source, "xxxxx"))
                .isEqualTo("primary=mongodb://u1:xxxxx@h1/db secondary=amqp://u2:xxxxx@h2/v");

        assertThat(SensitiveUtils.maskUserInfoCredentials(
                "{\"url\":\"mongodb://user:secret@host/db\"}", "xxxxx"))
                .isEqualTo("{\"url\":\"mongodb://user:xxxxx@host/db\"}");
    }

    @Test
    void maskUserInfoCredentialsHandlesNullEmptyAndSpecialMask() {
        assertThat(SensitiveUtils.maskUserInfoCredentials(null, "xxxxx")).isNull();
        assertThat(SensitiveUtils.maskUserInfoCredentials("", "xxxxx")).isEmpty();
        assertThat(SensitiveUtils.maskUserInfoCredentials("mongodb://u:p@h/db", null))
                .isEqualTo("mongodb://u:p@h/db");
        assertThat(SensitiveUtils.maskUserInfoCredentials("mongodb://u:p@h/db", "hide$me"))
                .isEqualTo("mongodb://u:hide$me@h/db");
    }

    @Test
    void maskPemPrivateKeyBlocksMasksBodiesAndKeepsMarkers() {
        String rsa = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF6PZFEw4N8AQ
                -----END RSA PRIVATE KEY-----
                """;
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks(rsa, "xxxxx"))
                .isEqualTo("""
                        -----BEGIN RSA PRIVATE KEY-----
                        xxxxx
                        -----END RSA PRIVATE KEY-----
                        """);

        String pkcs8 = """
                -----BEGIN PRIVATE KEY-----
                MGACAQAwBQYDK2VwBCIEIJ+keyMaterialHere
                -----END PRIVATE KEY-----
                """;
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks(pkcs8, "xxxxx"))
                .contains("-----BEGIN PRIVATE KEY-----")
                .contains("xxxxx")
                .contains("-----END PRIVATE KEY-----")
                .doesNotContain("keyMaterialHere");

        String ec = """
                -----BEGIN EC PRIVATE KEY-----
                MHQCAQEEISecretEcMaterial
                -----END EC PRIVATE KEY-----
                """;
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks(ec, "xxxxx")).doesNotContain("SecretEcMaterial");

        String encrypted = """
                -----BEGIN ENCRYPTED PRIVATE KEY-----
                encrypted-key-material
                -----END ENCRYPTED PRIVATE KEY-----
                """;
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks(encrypted, "xxxxx"))
                .doesNotContain("encrypted-key-material")
                .contains("-----BEGIN ENCRYPTED PRIVATE KEY-----")
                .contains("xxxxx");
    }

    @Test
    void maskPemPrivateKeyBlocksLeavesPublicMaterialAlone() {
        String certificate = """
                -----BEGIN CERTIFICATE-----
                MIIDXTCCAkWgAwIBAgIJAKHBj
                -----END CERTIFICATE-----
                """;
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks(certificate, "xxxxx")).isEqualTo(certificate);

        String publicKey = """
                -----BEGIN PUBLIC KEY-----
                MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBA
                -----END PUBLIC KEY-----
                """;
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks(publicKey, "xxxxx")).isEqualTo(publicKey);

        String lowerCaseHeader = """
                -----begin rsa private key-----
                secretEcMaterial
                -----end rsa private key-----
                """;
        assertThat(SensitiveUtils.maskPemPrivateKeyBlocks(lowerCaseHeader, "xxxxx")).doesNotContain("secretEcMaterial");
    }

    @Test
    void maskSensitiveValueShapesCombinesUserInfoAndPem() {
        String source = """
                uri=mongodb://user:pass@host/db
                -----BEGIN PRIVATE KEY-----
                secret-key-bytes
                -----END PRIVATE KEY-----
                """;
        String masked = SensitiveUtils.maskSensitiveValueShapes(source, "xxxxx");
        assertThat(masked)
                .contains("mongodb://user:xxxxx@host/db")
                .contains("xxxxx")
                .doesNotContain("pass@")
                .doesNotContain("secret-key-bytes");
    }
}
