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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates JWT tokens locally using JWKS with caching and key rotation support.
 */
final class JwtTokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenValidator.class);
    private static final Set<String> SYMMETRIC_ALGORITHMS = Set.of("HS256", "HS384", "HS512");

    private JwtTokenValidator() {
    }

    static OAuthTokenValidationResult validate(
            String token,
            String jwksEndpoint,
            long jwksCacheTtlSeconds,
            String expectedIssuer,
            Set<String> expectedAudiences,
            int clockSkewSeconds,
            boolean requireExpiration,
            int connectTimeoutMs,
            int readTimeoutMs,
            Set<String> allowedAlgorithms) {
        return validate(
                token, jwksEndpoint, jwksCacheTtlSeconds, expectedIssuer, expectedAudiences, clockSkewSeconds,
                requireExpiration, connectTimeoutMs, readTimeoutMs, allowedAlgorithms, null);
    }

    static OAuthTokenValidationResult validate(
            String token,
            String jwksEndpoint,
            long jwksCacheTtlSeconds,
            String expectedIssuer,
            Set<String> expectedAudiences,
            int clockSkewSeconds,
            boolean requireExpiration,
            int connectTimeoutMs,
            int readTimeoutMs,
            Set<String> allowedAlgorithms,
            String expectedTokenType) {

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (ParseException e) {
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "Token is not a valid JWT: " + e.getMessage());
        }

        String algorithm = signedJWT.getHeader().getAlgorithm().getName();
        if (SYMMETRIC_ALGORITHMS.contains(algorithm)) {
            return OAuthTokenValidationResult.invalid(
                    ErrorCode.UNSUPPORTED_ALGORITHM,
                    "Symmetric JWT algorithm " + algorithm + " is not supported for JWKS validation");
        }
        if (allowedAlgorithms != null && !allowedAlgorithms.contains(algorithm)) {
            return OAuthTokenValidationResult.invalid(
                    ErrorCode.UNSUPPORTED_ALGORITHM, "JWT algorithm " + algorithm + " is not in the allowed set");
        }

        if (expectedTokenType != null) {
            JOSEObjectType actualType = signedJWT.getHeader().getType();
            if (actualType == null || !expectedTokenType.equalsIgnoreCase(actualType.getType())) {
                return OAuthTokenValidationResult.invalid(
                        ErrorCode.INVALID_TOKEN, "Invalid JWT type: expected " + expectedTokenType
                                                 + ", got " + (actualType != null ? actualType.getType() : null));
            }
        }

        String keyID = signedJWT.getHeader().getKeyID();
        JwksCache jwksCache = JwksCache.instance();

        JWKSet jwkSet = jwksCache.getJwkSet(jwksEndpoint, jwksCacheTtlSeconds, connectTimeoutMs, readTimeoutMs);
        List<JWK> candidates = selectEligibleKeys(jwkSet, keyID, algorithm);

        if (candidates.isEmpty()) {
            try {
                jwkSet = jwksCache.refreshJwkSet(jwksEndpoint, connectTimeoutMs, readTimeoutMs);
                candidates = selectEligibleKeys(jwkSet, keyID, algorithm);
            } catch (OAuthException e) {
                LOG.debug("JWKS refresh failed, using cached keys: {}", e.getMessage());
            }
            if (candidates.isEmpty()) {
                return OAuthTokenValidationResult.invalid(ErrorCode.NO_MATCHING_KEY, "No matching key found for kid: " + keyID);
            }
        }

        boolean verified = false;
        for (JWK candidate : candidates) {
            try {
                JWSVerifier verifier = createVerifier(candidate);
                if (signedJWT.verify(verifier)) {
                    verified = true;
                    break;
                }
            } catch (Exception e) {
                LOG.debug("Signature verification failed with JWK {}: {}", candidate.getKeyID(), e.getMessage());
            }
        }
        if (!verified) {
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_SIGNATURE, "Invalid token signature");
        }

        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "Failed to parse JWT claims: " + e.getMessage());
        }

        long now = System.currentTimeMillis() / 1000L;

        if (claims.getExpirationTime() != null) {
            long exp = claims.getExpirationTime().getTime() / 1000L;
            if (now > exp + clockSkewSeconds) {
                return OAuthTokenValidationResult.invalid(ErrorCode.EXPIRED_TOKEN, "Token has expired");
            }
        } else if (requireExpiration) {
            return OAuthTokenValidationResult.invalid(ErrorCode.MISSING_EXPIRATION, "Token has no expiration claim");
        }

        if (claims.getNotBeforeTime() != null) {
            long nbf = claims.getNotBeforeTime().getTime() / 1000L;
            if (now < nbf - clockSkewSeconds) {
                return OAuthTokenValidationResult.invalid(ErrorCode.NOT_YET_VALID, "Token is not yet valid (nbf)");
            }
        }

        if (expectedIssuer != null) {
            String iss = claims.getIssuer();
            if (!expectedIssuer.equals(iss)) {
                return OAuthTokenValidationResult.invalid(
                        ErrorCode.INVALID_ISSUER, "Invalid JWT issuer: expected " + expectedIssuer + ", got " + iss);
            }
        }

        List<String> audience = claims.getAudience() != null ? claims.getAudience() : Collections.emptyList();
        if (expectedAudiences != null && !expectedAudiences.isEmpty() && !containsAny(audience, expectedAudiences)) {
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_AUDIENCE,
                    "Invalid JWT audience: expected to contain one of " + expectedAudiences);
        }

        String subject = claims.getSubject();
        String issuer = claims.getIssuer();
        long expiresAt = claims.getExpirationTime() != null ? claims.getExpirationTime().getTime() / 1000L : 0;

        List<String> scopes = Collections.emptyList();
        Object scopeClaim = claims.getClaim("scope");
        if (scopeClaim instanceof String s) {
            scopes = Arrays.asList(s.split(" "));
        } else if (scopeClaim instanceof List<?> list) {
            scopes = new ArrayList<>();
            for (Object item : list) {
                scopes.add(String.valueOf(item));
            }
        }

        Map<String, Object> allClaims = claims.getClaims();

        LOG.debug("JWT validated successfully: sub={}, iss={}, exp={}", subject, issuer, expiresAt);
        return OAuthTokenValidationResult.valid(subject, issuer, audience, scopes, allClaims, expiresAt);
    }

    private static JWSVerifier createVerifier(JWK jwk) throws Exception {
        if (jwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey.toRSAPublicKey());
        } else if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey.toECPublicKey());
        }
        throw new OAuthException("Unsupported JWK key type: " + jwk.getKeyType());
    }

    private static List<JWK> selectEligibleKeys(JWKSet jwkSet, String keyID, String algorithm) {
        List<JWK> candidates = jwkSet.getKeys().stream()
                .filter(jwk -> keyID == null || keyID.equals(jwk.getKeyID()))
                .filter(jwk -> isSignatureUse(jwk) && isVerifyOperation(jwk))
                .filter(jwk -> isAlgorithmAllowed(jwk, algorithm))
                .filter(jwk -> isKeyTypeCompatible(jwk, algorithm))
                .collect(Collectors.toList());

        if (keyID == null && candidates.size() != 1) {
            return Collections.emptyList();
        }
        return candidates;
    }

    private static boolean isSignatureUse(JWK jwk) {
        KeyUse keyUse = jwk.getKeyUse();
        return keyUse == null || KeyUse.SIGNATURE.equals(keyUse);
    }

    private static boolean isVerifyOperation(JWK jwk) {
        Set<KeyOperation> keyOperations = jwk.getKeyOperations();
        return keyOperations == null || keyOperations.isEmpty() || keyOperations.contains(KeyOperation.VERIFY);
    }

    private static boolean isAlgorithmAllowed(JWK jwk, String algorithm) {
        return jwk.getAlgorithm() == null || algorithm.equals(jwk.getAlgorithm().getName());
    }

    private static boolean isKeyTypeCompatible(JWK jwk, String algorithm) {
        if (algorithm.startsWith("RS") || algorithm.startsWith("PS")) {
            return jwk instanceof RSAKey;
        }
        if (algorithm.startsWith("ES")) {
            return jwk instanceof ECKey;
        }
        return false;
    }

    private static boolean containsAny(List<String> actual, Set<String> expected) {
        for (String value : expected) {
            if (actual.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
