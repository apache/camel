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

import java.io.UnsupportedEncodingException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
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

    private final JWTVerifier verifier;

    public JwtServerInterceptor(JwtAlgorithm algorithm, String secret, String issuer, String subject) {
        verifier = prepareJwtVerifier(algorithm, secret, issuer, subject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String jwtToken = metadata.get(GrpcConstants.GRPC_JWT_METADATA_KEY);
        if (jwtToken == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("JWT Token is missing from metadata"), metadata);
            return NOOP_LISTENER;
        }

        Context ctx;
        try {
            DecodedJWT verified = verifier.verify(jwtToken);
            ctx = Context.current()
                         .withValue(GrpcConstants.GRPC_JWT_USER_ID_CTX_KEY, verified.getSubject() == null ? "anonymous" : verified.getSubject())
                         .withValue(GrpcConstants.GRPC_JWT_CTX_KEY, jwtToken);
        } catch (Exception e) {
            LOG.debug("JWT token verification failed - Unauthenticated");
            call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), metadata);
            return NOOP_LISTENER;
        }

        return Contexts.interceptCall(ctx, call, metadata, serverCallHandler);
    }
    
    public static JWTVerifier prepareJwtVerifier(JwtAlgorithm algorithmName, String secret, String issuer, String subject) {
        try {
            Algorithm algorithm = JwtHelper.selectAlgorithm(algorithmName, secret);
            return JWT.require(algorithm).withIssuer(issuer).withSubject(subject).build();
        } catch (JWTCreationException e) {
            throw new IllegalArgumentException("Unable to create JWT verifier", e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("UTF-8 encoding not supported during JWT verifier creation", e);
        }
    }
}
