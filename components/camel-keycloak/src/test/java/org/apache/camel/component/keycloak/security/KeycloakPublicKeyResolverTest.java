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
package org.apache.camel.component.keycloak.security;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KeycloakPublicKeyResolverTest {

    private static RSAPublicKey generateKey() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return (RSAPublicKey) keyGen.generateKeyPair().getPublic();
    }

    // base64url (no padding) of the unsigned big-endian value, as required for a JWK n/e component
    private static String b64url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            bytes = unsigned;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String jwksKey(String kid, RSAPublicKey key) {
        return String.format("{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"%s\",\"n\":\"%s\",\"e\":\"%s\"}",
                kid, b64url(key.getModulus()), b64url(key.getPublicExponent()));
    }

    @Test
    void selectsKeyMatchingTheKid() throws Exception {
        RSAPublicKey key1 = generateKey();
        RSAPublicKey key2 = generateKey();
        String jwks = "{\"keys\":[" + jwksKey("key-1", key1) + "," + jwksKey("key-2", key2) + "]}";

        KeycloakPublicKeyResolver resolver = new KeycloakPublicKeyResolver("http://localhost:8080", "test");
        resolver.parseJwks(jwks);

        // the key selected for a kid must be that kid's key, not merely the first one
        assertEquals(key2.getModulus(), ((RSAPublicKey) resolver.selectKey("key-2")).getModulus());
        assertEquals(key1.getModulus(), ((RSAPublicKey) resolver.selectKey("key-1")).getModulus());
    }

    @Test
    void failsClosedWhenKidNotFound() throws Exception {
        RSAPublicKey key1 = generateKey();
        String jwks = "{\"keys\":[" + jwksKey("key-1", key1) + "]}";

        KeycloakPublicKeyResolver resolver = new KeycloakPublicKeyResolver("http://localhost:8080", "test");
        resolver.parseJwks(jwks);

        // a token whose kid is not in the JWKS must not be verified against a different (first) key
        assertThrows(IOException.class, () -> resolver.selectKey("unknown-kid"));
    }

    @Test
    void fallsBackToFirstKeyWhenNoKid() throws Exception {
        RSAPublicKey key1 = generateKey();
        String jwks = "{\"keys\":[" + jwksKey("key-1", key1) + "]}";

        KeycloakPublicKeyResolver resolver = new KeycloakPublicKeyResolver("http://localhost:8080", "test");
        resolver.parseJwks(jwks);

        assertNotNull(resolver.selectKey(null));
    }
}
