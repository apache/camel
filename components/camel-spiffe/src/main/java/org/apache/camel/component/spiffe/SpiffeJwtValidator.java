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
package org.apache.camel.component.spiffe;

import java.util.Arrays;

import io.spiffe.exception.JwtSvidException;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;

/** Shared JWT validation for the producer and the route security policy. */
final class SpiffeJwtValidator {
    private SpiffeJwtValidator() {
    }

    static JwtSvid validate(WorkloadApiClient client, Exchange exchange, String configuredAudience, boolean allowBodyToken)
            throws JwtSvidException {
        clearIdentity(exchange);
        Message message = exchange.getMessage();
        String token = message.getHeader(SpiffeConstants.TOKEN, String.class);
        if (ObjectHelper.isEmpty(token)) {
            token = bearerToken(message.getHeader("Authorization", String.class));
        }
        if (ObjectHelper.isEmpty(token) && allowBodyToken) {
            token = message.getBody(String.class);
        }
        if (ObjectHelper.isEmpty(token)) {
            throw new IllegalArgumentException(
                    "A JWT-SVID token is required (set CamelSpiffeToken or an Authorization: Bearer header"
                                               + (allowBodyToken ? ", or the message body)" : ")"));
        }
        JwtSvidException failure = null;
        // Audiences are policy configuration, never a message-selected validation parameter.
        for (String audience : audiences(configuredAudience)) {
            try {
                return client.validateJwtSvid(token, audience);
            } catch (JwtSvidException e) {
                failure = e;
            }
        }
        if (failure == null) {
            throw new IllegalStateException("No configured audience was evaluated");
        }
        throw failure;
    }

    static String[] audiences(String configured) {
        if (ObjectHelper.isEmpty(configured)) {
            throw new IllegalArgumentException("At least one audience is required (set the audience option)");
        }
        String[] audiences = Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (audiences.length == 0) {
            throw new IllegalArgumentException("At least one non-blank audience is required (set the audience option)");
        }
        return audiences;
    }

    static void clearIdentity(Exchange exchange) {
        exchange.removeProperty(SpiffeConstants.JWT_IDENTITY);
        exchange.getMessage().removeHeader(SpiffeConstants.SPIFFE_ID);
        exchange.getMessage().removeHeader(SpiffeConstants.EXPIRY);
    }

    static void recordIdentity(Exchange exchange, JwtSvid svid) {
        SpiffeJwtIdentity identity = new SpiffeJwtIdentity(svid);
        exchange.setProperty(SpiffeConstants.JWT_IDENTITY, identity);
        exchange.getMessage().setHeader(SpiffeConstants.SPIFFE_ID, identity.getSpiffeId());
        exchange.getMessage().setHeader(SpiffeConstants.EXPIRY, svid.getExpiry());
    }

    private static String bearerToken(String authorization) {
        if (ObjectHelper.isEmpty(authorization)) {
            return null;
        }
        String value = authorization.trim();
        String scheme = "Bearer ";
        if (value.length() > scheme.length() && value.regionMatches(true, 0, scheme, 0, scheme.length())) {
            String token = value.substring(scheme.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
