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
import java.util.List;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void accessTokenRejectedWhenAudienceMismatched() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedTokenWithAudience("other-client"));

        OAuthException ex = assertThrows(OAuthException.class, () -> UserProfile.fromJson(verifyingConfig(), json));
        assertEquals("Invalid JWT audience. Expected to contain one of: [my-client]", ex.getMessage());
    }

    @Test
    void accessTokenRejectedWhenAudienceMissing() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedTokenWithAudience((String) null));

        OAuthException ex = assertThrows(OAuthException.class, () -> UserProfile.fromJson(verifyingConfig(), json));
        assertEquals("Invalid JWT audience. Expected to contain one of: [my-client]", ex.getMessage());
    }

    @Test
    void accessTokenAcceptedWhenJwtOptionsAudienceMatches() throws Exception {
        OAuthConfig config = verifyingConfig();
        config.getJWTOptions().addAudience("resource-server");

        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedTokenWithAudience("resource-server"));

        assertNotNull(UserProfile.fromJson(config, json));
    }

    @Test
    void accessTokenRejectedWhenJwtOptionsAudienceMismatched() throws Exception {
        OAuthConfig config = verifyingConfig();
        config.getJWTOptions().addAudience("resource-server");

        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedTokenWithAudience("my-client"));

        OAuthException ex = assertThrows(OAuthException.class, () -> UserProfile.fromJson(config, json));
        assertEquals("Invalid JWT audience. Expected to contain one of: [resource-server]", ex.getMessage());
    }

    @Test
    void accessTokenRejectedWhenNoAudienceConfigured() throws Exception {
        OAuthConfig config = new OAuthConfig();
        config.setJWKSet(new JWKSet(rsaKey.toPublicJWK()));

        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedTokenWithAudience("my-client"));

        OAuthException ex = assertThrows(OAuthException.class, () -> UserProfile.fromJson(config, json));
        assertEquals("Token validation requires expected audience; none configured", ex.getMessage());
    }

    @Test
    void accessTokenAcceptedWithMultiValuedAudienceArray() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("access_token", signedTokenWithAudiences(List.of("my-client", "other-audience")));

        UserProfile profile = UserProfile.fromJson(verifyingConfig(), json);
        assertNotNull(profile);
    }

    @Test
    void codeExchangeLoginFlowAcceptedWhenAudienceMatchesClientId() throws Exception {
        JsonObject tokenResponse = new JsonObject();
        tokenResponse.addProperty("access_token", signedTokenWithAudience("my-client"));
        tokenResponse.addProperty("id_token", signedIdToken("my-client"));
        tokenResponse.addProperty("expires_in", 3600);

        UserProfile profile = UserProfile.fromJson(verifyingConfig(), tokenResponse);
        assertNotNull(profile);
        assertEquals("alice", profile.subject());
    }

    @Test
    void codeExchangeLoginFlowAcceptedWithCustomJwtOptionsAudienceForIdp() throws Exception {
        OAuthConfig config = verifyingConfig();
        config.getJWTOptions().addAudience("account");

        JsonObject tokenResponse = new JsonObject();
        tokenResponse.addProperty("access_token", signedTokenWithAudience("account"));
        tokenResponse.addProperty("id_token", signedIdToken("my-client"));
        tokenResponse.addProperty("expires_in", 3600);

        UserProfile profile = UserProfile.fromJson(config, tokenResponse);
        assertNotNull(profile);
        assertEquals("alice", profile.subject());
    }

    @Test
    void codeExchangeLoginFlowRejectedWhenAccessTokenAudienceMismatched() throws Exception {
        JsonObject tokenResponse = new JsonObject();
        tokenResponse.addProperty("access_token", signedTokenWithAudience("account"));
        tokenResponse.addProperty("id_token", signedIdToken("my-client"));
        tokenResponse.addProperty("expires_in", 3600);

        OAuthException ex = assertThrows(OAuthException.class, () -> UserProfile.fromJson(verifyingConfig(), tokenResponse));
        assertEquals("Invalid JWT audience. Expected to contain one of: [my-client]", ex.getMessage());
    }

    private OAuthConfig verifyingConfig() {
        OAuthConfig config = new OAuthConfig().setClientId("my-client");
        config.setJWKSet(new JWKSet(rsaKey.toPublicJWK()));
        return config;
    }

    private String signedToken() throws Exception {
        return signedTokenWithAudience("my-client");
    }

    private String signedTokenWithAudience(String audience) throws Exception {
        return signedTokenWithAudiences(audience != null ? List.of(audience) : null);
    }

    private String signedTokenWithAudiences(List<String> audiences) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user1")
                .issuer("https://idp.example.com")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000L))
                .issueTime(new Date());
        if (audiences != null && !audiences.isEmpty()) {
            claims.audience(audiences);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    private String signedIdToken(String clientId) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://idp.example.com")
                .claim("azp", clientId)
                .claim("name", "Alice")
                .claim("email", "alice@example.com")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000L))
                .issueTime(new Date());
        claims.audience(clientId);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
