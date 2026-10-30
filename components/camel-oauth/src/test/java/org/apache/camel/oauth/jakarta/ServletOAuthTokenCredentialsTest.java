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
package org.apache.camel.oauth.jakarta;

import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.camel.oauth.OAuthConfig;
import org.apache.camel.oauth.OAuthException;
import org.apache.camel.oauth.TokenCredentials;
import org.apache.camel.oauth.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Authenticates bearer tokens the way {@code OAuthBearerTokenProcessor} does on the servlet backend. The tokens verify
 * against a local JWK set and are not expired, so no identity provider is contacted.
 */
class ServletOAuthTokenCredentialsTest {

    private static final String KID = "test-key-1";
    private static final String ISSUER = "https://idp.example.com";

    private RSAKey rsaKey;
    private ServletOAuth oauth;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID(KID).generate();
        OAuthConfig oauthConfig = new OAuthConfig().setClientId("my-client");
        oauthConfig.setJWKSet(new JWKSet(rsaKey.toPublicJWK()));
        oauthConfig.getJWTOptions().setIssuer(ISSUER);
        oauth = new ServletOAuth() {
            {
                config = oauthConfig;
            }
        };
    }

    @Test
    void bearerTokenRejectedBeforeNotBefore() throws Exception {
        String token = signedToken(new Date(System.currentTimeMillis() + 3_600_000L));

        OAuthException ex = assertThrows(OAuthException.class, () -> oauth.authenticate(new TokenCredentials(token)));
        assertEquals("Token is not yet valid (nbf)", ex.getCause().getMessage());
    }

    @Test
    void bearerTokenAcceptedOnceNotBeforeHasPassed() throws Exception {
        String token = signedToken(new Date(System.currentTimeMillis() - 60_000L));

        UserProfile profile = oauth.authenticate(new TokenCredentials(token));
        assertEquals("user1", profile.subject());
    }

    @Test
    void bearerTokenRejectedWithMismatchedAudience() throws Exception {
        String token = signedTokenWithAudience("other-app", null);

        OAuthException ex = assertThrows(OAuthException.class, () -> oauth.authenticate(new TokenCredentials(token)));
        assertEquals("Invalid JWT audience. Expected to contain one of: [my-client]", ex.getCause().getMessage());
    }

    @Test
    void bearerTokenRejectedWhenAudienceMissing() throws Exception {
        String token = signedTokenWithAudience(null, null);

        OAuthException ex = assertThrows(OAuthException.class, () -> oauth.authenticate(new TokenCredentials(token)));
        assertEquals("Invalid JWT audience. Expected to contain one of: [my-client]", ex.getCause().getMessage());
    }

    @Test
    void bearerTokenAcceptedWithCustomJwtOptionsAudience() throws Exception {
        OAuthConfig oauthConfig = new OAuthConfig().setClientId("my-client");
        oauthConfig.setJWKSet(new JWKSet(rsaKey.toPublicJWK()));
        oauthConfig.getJWTOptions().setIssuer(ISSUER);
        oauthConfig.getJWTOptions().addAudience("custom-api");
        ServletOAuth customOAuth = new ServletOAuth() {
            {
                config = oauthConfig;
            }
        };

        String token = signedTokenWithAudience("custom-api", null);
        UserProfile profile = customOAuth.authenticate(new TokenCredentials(token));
        assertEquals("user1", profile.subject());
    }

    @Test
    void bearerTokenRejectedWhenJwtOptionsAudienceMismatched() throws Exception {
        OAuthConfig oauthConfig = new OAuthConfig().setClientId("my-client");
        oauthConfig.setJWKSet(new JWKSet(rsaKey.toPublicJWK()));
        oauthConfig.getJWTOptions().setIssuer(ISSUER);
        oauthConfig.getJWTOptions().addAudience("custom-api");
        ServletOAuth customOAuth = new ServletOAuth() {
            {
                config = oauthConfig;
            }
        };

        String token = signedTokenWithAudience("my-client", null);
        OAuthException ex = assertThrows(OAuthException.class, () -> customOAuth.authenticate(new TokenCredentials(token)));
        assertEquals("Invalid JWT audience. Expected to contain one of: [custom-api]", ex.getCause().getMessage());
    }

    @Test
    void bearerTokenAcceptedWithMultiValuedAudienceArray() throws Exception {
        String token = signedTokenWithAudiences(List.of("my-client", "other-api"), null);

        UserProfile profile = oauth.authenticate(new TokenCredentials(token));
        assertEquals("user1", profile.subject());
    }

    private String signedToken(Date notBefore) throws Exception {
        return signedTokenWithAudience("my-client", notBefore);
    }

    private String signedTokenWithAudience(String audience, Date notBefore) throws Exception {
        return signedTokenWithAudiences(audience != null ? List.of(audience) : null, notBefore);
    }

    private String signedTokenWithAudiences(List<String> audiences, Date notBefore) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject("user1")
                .issuer(ISSUER)
                .expirationTime(new Date(System.currentTimeMillis() + 7_200_000L))
                .issueTime(new Date());
        if (audiences != null && !audiences.isEmpty()) {
            builder.audience(audiences);
        }
        if (notBefore != null) {
            builder.notBeforeTime(notBefore);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), builder.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
