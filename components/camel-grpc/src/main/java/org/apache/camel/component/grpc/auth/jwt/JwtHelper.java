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
package org.apache.camel.component.grpc.auth.jwt;

import java.time.Instant;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;

/**
 * JSON Web Token credentials generator helper
 */
public final class JwtHelper {
    private JwtHelper() {
    }

    /**
     * Creates a token without the iat (issued at) and exp (expires at) claims, which is valid until the secret changes.
     */
    public static String createJwtToken(JwtAlgorithm algorithmName, String secret, String issuer, String subject) {
        return createJwtToken(algorithmName, secret, issuer, subject, null, null);
    }

    /**
     * Creates a token with the given iat (issued at) and exp (expires at) claims. A null value leaves the claim out.
     */
    public static String createJwtToken(
            JwtAlgorithm algorithmName, String secret, String issuer, String subject, Instant issuedAt, Instant expiresAt) {
        try {
            Algorithm algorithm = selectAlgorithm(algorithmName, secret);
            JWTCreator.Builder builder = JWT.create().withIssuer(issuer).withSubject(subject);
            if (issuedAt != null) {
                builder.withIssuedAt(issuedAt);
            }
            if (expiresAt != null) {
                builder.withExpiresAt(expiresAt);
            }
            return builder.sign(algorithm);
        } catch (JWTCreationException e) {
            throw new IllegalArgumentException("Unable to create JWT token", e);
        }
    }

    public static Algorithm selectAlgorithm(JwtAlgorithm algorithmName, String secret)
            throws IllegalArgumentException {
        switch (algorithmName) {
            case HMAC256:
                return Algorithm.HMAC256(secret);
            case HMAC384:
                return Algorithm.HMAC384(secret);
            case HMAC512:
                return Algorithm.HMAC512(secret);
            default:
                throw new IllegalArgumentException("JWT algorithm " + algorithmName + " not implemented");
        }
    }
}
