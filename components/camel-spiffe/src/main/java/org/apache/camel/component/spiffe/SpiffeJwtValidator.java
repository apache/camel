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
            // a JWT-SVID is presented over HTTP as "Authorization: Bearer <token>", so an HTTP route can validate it
            // without a bean to strip the scheme; it is checked before the body so that a request payload on a POST
            // or PUT is not mistaken for the token
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
        // The audience is the check here, not a parameter: it is what binds the token to THIS workload, so it comes
        // from the configuration only. Honouring a CamelSpiffeAudience header would let a caller validate a token
        // minted for someone else against an audience of their choosing.
        //
        // The Workload API validates against one audience at a time, so a configured list is tried in turn and the
        // token is accepted when any audience matches. Taking only the first would silently enforce a narrower rule
        // than the configuration asks for, which is the wrong failure mode for a check that decides whether a caller
        // is authenticated.
        JwtSvidException failure = null;
        for (String audience : audiences(configuredAudience, "set the audience option")) {
            try {
                return client.validateJwtSvid(token, audience);
            } catch (JwtSvidException e) {
                // could be this audience, or the token itself; only once every audience has failed do we know
                failure = e;
            }
        }
        // audiences never returns an empty array, so the loop ran and failure is set; be explicit rather than leaving
        // a reader (or a static analyser) to prove it
        if (failure == null) {
            throw new IllegalStateException("No configured audience was evaluated");
        }
        throw failure;
    }

    /**
     * Splits a comma-separated audience list, trimming each entry and dropping blank ones.
     *
     * @param  configured the configured list
     * @param  how        how to configure it, for the error when it is empty
     * @return            the audiences, never empty
     */
    static String[] audiences(String configured, String how) {
        if (ObjectHelper.isEmpty(configured)) {
            throw new IllegalArgumentException("At least one audience is required (" + how + ")");
        }
        String[] audiences = Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (audiences.length == 0) {
            throw new IllegalArgumentException("At least one non-blank audience is required (" + how + ")");
        }
        return audiences;
    }

    static void clearIdentity(Exchange exchange) {
        exchange.removeProperty(SpiffeConstants.JWT_IDENTITY);
        exchange.getMessage().removeHeader(SpiffeConstants.SPIFFE_ID);
        exchange.getMessage().removeHeader(SpiffeConstants.EXPIRY);
    }

    /**
     * Publishes the verified identity. The exchange property is the value the route established, which a sender cannot
     * put on the message, and the one to check further down the route. The producer also sets the identity headers, its
     * documented output; the policy does not, so that the identity does not travel in two channels, nor to outbound
     * producers.
     */
    static void recordIdentity(Exchange exchange, JwtSvid svid, boolean headers) {
        SpiffeJwtIdentity identity = new SpiffeJwtIdentity(svid);
        exchange.setProperty(SpiffeConstants.JWT_IDENTITY, identity);
        if (headers) {
            exchange.getMessage().setHeader(SpiffeConstants.SPIFFE_ID, identity.getSpiffeId());
            exchange.getMessage().setHeader(SpiffeConstants.EXPIRY, svid.getExpiry());
        }
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
