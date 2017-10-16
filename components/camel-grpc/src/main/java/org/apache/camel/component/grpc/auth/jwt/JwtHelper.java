/**
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
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;

/**
 * JSON Web Token credentials generator helper
 */
public final class JwtHelper {
    private JwtHelper() {
    }

    public static String createJwtToken(JwtAlgorithm algorithmName, String secret, String issuer, String subject) {
        try {
            Algorithm algorithm = selectAlgorithm(algorithmName, secret);
            String token = JWT.create().withIssuer(issuer).withSubject(subject).sign(algorithm);
            return token;
        } catch (JWTCreationException e) {
            throw new IllegalArgumentException("Unable to create JWT token", e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("UTF-8 encoding not supported during JWT token creation", e);
        }
    }
    
    public static Algorithm selectAlgorithm(JwtAlgorithm algorithmName, String secret) throws IllegalArgumentException, UnsupportedEncodingException {
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
