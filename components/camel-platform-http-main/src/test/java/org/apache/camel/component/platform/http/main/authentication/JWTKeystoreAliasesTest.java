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
package org.apache.camel.component.platform.http.main.authentication;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTKeystoreAliasesTest {

    private static final String PASSWORD = "secret";

    @TempDir
    Path dir;

    @Test
    void validKeystoreStarts() throws Exception {
        // a PKCS12 keystore stores the alias in lower case, as hs256
        Path file = writeKeystore("PKCS12", PASSWORD, "HS256");

        assertThatCode(() -> JWTAuthenticationConfigurer.assertJwtKeystoreHasKey("PKCS12", file.toString(), PASSWORD,
                "camel.server")).doesNotThrowAnyException();
    }

    @Test
    void oneAliasNamedAfterAnAlgorithmIsEnough() throws Exception {
        Path file = writeKeystore("PKCS12", PASSWORD, "keycloak", "HS512");

        assertThatCode(() -> JWTAuthenticationConfigurer.assertJwtKeystoreHasKey("PKCS12", file.toString(), PASSWORD,
                "camel.server")).doesNotThrowAnyException();
    }

    @Test
    void noAliasNamedAfterAnAlgorithmFails() throws Exception {
        Path file = writeKeystore("PKCS12", PASSWORD, "keycloak");

        assertThatThrownBy(() -> JWTAuthenticationConfigurer.assertJwtKeystoreHasKey("PKCS12", file.toString(),
                PASSWORD, "camel.management"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("camel.management.jwtKeystorePath")
                .hasMessageContaining("keycloak")
                .hasMessageContaining("RS256");
    }

    @Test
    void keyWithAnotherPasswordThanTheKeystoreFails() throws Exception {
        // JCEKS keeps a password per key, so the key can have another password than the keystore
        Path file = writeKeystore("JCEKS", "another", "HS256");

        assertThatThrownBy(() -> JWTAuthenticationConfigurer.assertJwtKeystoreHasKey("JCEKS", file.toString(),
                PASSWORD, "camel.server"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no key that Vert.x can use");
    }

    @Test
    void typeNotSetIsStillChecked() throws Exception {
        // the default keystore type of Java is PKCS12
        Path file = writeKeystore("PKCS12", PASSWORD, "keycloak");

        assertThatThrownBy(() -> JWTAuthenticationConfigurer.assertJwtKeystoreHasKey(null, file.toString(), PASSWORD,
                "camel.server"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keycloak");
    }

    @Test
    void keystoreNotFoundFails() {
        String missing = dir.resolve("missing.p12").toString();

        assertThatThrownBy(() -> JWTAuthenticationConfigurer.assertJwtKeystoreHasKey("PKCS12", missing, PASSWORD,
                "camel.server"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not found");
    }

    private Path writeKeystore(String type, String keyPassword, String... aliases) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(null, null);
        for (String alias : aliases) {
            keyStore.setEntry(alias, new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[32], "HmacSHA256")),
                    new KeyStore.PasswordProtection(keyPassword.toCharArray()));
        }
        Path file = dir.resolve("keystore." + type.toLowerCase());
        try (OutputStream out = Files.newOutputStream(file)) {
            keyStore.store(out, PASSWORD.toCharArray());
        }
        return file;
    }
}
