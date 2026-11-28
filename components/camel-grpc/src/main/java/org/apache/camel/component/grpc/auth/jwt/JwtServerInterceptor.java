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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.apache.camel.component.grpc.GrpcConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON Web Token credentials validator implementation
 */
public class JwtServerInterceptor implements ServerInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(JwtServerInterceptor.class);

    @SuppressWarnings("rawtypes")
    private static final ServerCall.Listener NOOP_LISTENER = new ServerCall.Listener() {
    };

    /**
     * The clock difference between the hosts that is accepted for the exp (expires at) and iat (issued at) claims.
     */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final JWTVerifier verifier;
    private final Duration maxTokenAge;
    private final Clock clock;

    public JwtServerInterceptor(JwtAlgorithm algorithm, String secret, String issuer, String subject) {
        this(algorithm, secret, issuer, subject, Duration.ZERO);
    }

    /**
     * @param maxTokenAge the maximum age of a token by its iat (issued at) claim, 0 or less to not check the age
     */
    public JwtServerInterceptor(JwtAlgorithm algorithm, String secret, String issuer, String subject,
                                Duration maxTokenAge) {
        this(algorithm, secret, issuer, subject, maxTokenAge, Clock.systemUTC());
    }

    JwtServerInterceptor(JwtAlgorithm algorithm, String secret, String issuer, String subject, Duration maxTokenAge,
                         Clock clock) {
        this.verifier = prepareJwtVerifier(algorithm, secret, issuer, subject);
        this.maxTokenAge = maxTokenAge;
        this.clock = clock;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String jwtToken = metadata.get(GrpcConstants.GRPC_JWT_METADATA_KEY);
        if (jwtToken == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("JWT Token is missing from metadata"), metadata);
            return NOOP_LISTENER;
        }

        Context ctx;
        try {
            DecodedJWT verified = verifier.verify(jwtToken);
            checkTokenAge(verified);
            ctx = Context.current()
                    .withValue(GrpcConstants.GRPC_JWT_USER_ID_CTX_KEY,
                            verified.getSubject() == null ? "anonymous" : verified.getSubject())
                    .withValue(GrpcConstants.GRPC_JWT_CTX_KEY, jwtToken);
        } catch (Exception e) {
            LOG.debug("JWT token verification failed - Unauthenticated");
            call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), metadata);
            return NOOP_LISTENER;
        }

        return Contexts.interceptCall(ctx, call, metadata, serverCallHandler);
    }

    private void checkTokenAge(DecodedJWT token) {
        if (maxTokenAge.isNegative() || maxTokenAge.isZero()) {
            return;
        }
        Instant issuedAt = token.getIssuedAtAsInstant();
        if (issuedAt == null) {
            throw new JWTVerificationException("The Token has no iat (issued at) claim, which jwtMaxTokenAge requires.");
        }
        if (issuedAt.plus(maxTokenAge).isBefore(clock.instant())) {
            throw new JWTVerificationException(
                    "The Token was issued at " + issuedAt + ", which is longer ago than jwtMaxTokenAge.");
        }
    }

    /**
     * The verifier checks the signature, the issuer and the subject, and, when the token has them, that the exp
     * (expires at) claim has not passed and that the iat (issued at) claim is not in the future, accepting a clock
     * difference of {@link #CLOCK_SKEW}.
     */
    public static JWTVerifier prepareJwtVerifier(JwtAlgorithm algorithmName, String secret, String issuer, String subject) {
        try {
            Algorithm algorithm = JwtHelper.selectAlgorithm(algorithmName, secret);
            return JWT.require(algorithm).withIssuer(issuer).withSubject(subject)
                    .acceptLeeway(CLOCK_SKEW.toSeconds())
                    .build();
        } catch (JWTCreationException e) {
            throw new IllegalArgumentException("Unable to create JWT verifier", e);
        }
    }
}
