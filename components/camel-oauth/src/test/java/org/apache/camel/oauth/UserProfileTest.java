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
package org.apache.camel.oauth;

import java.util.Date;

import com.google.gson.JsonObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserProfileTest {

    private static final String KID = "test-key-1";

    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID(KID).generate();
    }

    @Test
    void accessTokenRejectedWhenJwkSetEmpty() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedToken());

        // A present-but-empty JWK set means the signature cannot be verified; the token must not be trusted.
        OAuthConfig config = new OAuthConfig().setClientId("my-client");
        config.setJWKSet(new JWKSet());

        assertThrows(OAuthException.class, () -> UserProfile.fromJson(config, json));
    }

    @Test
    void accessTokenRejectedWhenJwkSetMissing() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedToken());

        // No JWK set configured at all: still must not trust an unverified token.
        OAuthConfig config = new OAuthConfig().setClientId("my-client");

        assertThrows(OAuthException.class, () -> UserProfile.fromJson(config, json));
    }

    @Test
    void accessTokenAcceptedWhenSignatureVerifies() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedToken());

        OAuthConfig config = new OAuthConfig().setClientId("my-client");
        config.setJWKSet(new JWKSet(rsaKey.toPublicJWK()));

        UserProfile profile = UserProfile.fromJson(config, json);
        assertNotNull(profile);
    }

    private String signedToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user1")
                .issuer("https://idp.example.com")
                .audience("my-client")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000L))
                .issueTime(new Date())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
