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

import java.security.KeyStore;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWTKeystoreAliasesTest {

    @Test
    void noWarningForAnAliasNamedAfterAnAlgorithm() throws Exception {
        // a PKCS12 keystore stores the alias in lower case, as hs256
        assertNull(JWTAuthenticationConfigurer.checkJwtKeystoreAliases(keyStoreWith("HS256"), "camel.server", "keystore.p12"));
    }

    @Test
    void noWarningWhenOneOfTheAliasesIsNamedAfterAnAlgorithm() throws Exception {
        assertNull(JWTAuthenticationConfigurer.checkJwtKeystoreAliases(keyStoreWith("keycloak", "HS512"),
                "camel.server", "keystore.p12"));
    }

    @Test
    void warningForAnAliasNotNamedAfterAnAlgorithm() throws Exception {
        String warning = JWTAuthenticationConfigurer.checkJwtKeystoreAliases(keyStoreWith("keycloak"),
                "camel.management", "keystore.p12");

        assertNotNull(warning);
        assertTrue(warning.contains("camel.management.jwtKeystorePath"), warning);
        assertTrue(warning.contains("keycloak"), warning);
        assertTrue(warning.contains("RS256"), warning);
        assertTrue(warning.contains("keystore.p12"), warning);
    }

    private static KeyStore keyStoreWith(String... aliases) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        for (String alias : aliases) {
            keyStore.setEntry(alias, new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[32], "HmacSHA256")),
                    new KeyStore.PasswordProtection("secret".toCharArray()));
        }
        return keyStore;
    }
}
