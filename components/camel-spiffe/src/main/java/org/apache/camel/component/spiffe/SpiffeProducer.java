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
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class SpiffeProducer extends DefaultProducer {

    public SpiffeProducer(final SpiffeEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SpiffeEndpoint getEndpoint() {
        return (SpiffeEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final WorkloadApiClient client = getEndpoint().getWorkloadApiClient();
        switch (determineOperation(exchange)) {
            case fetchX509Svid -> fetchX509Svid(client, exchange);
            case fetchJwtSvid -> fetchJwtSvid(client, exchange);
            case validateJwtSvid -> validateJwtSvid(client, exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void fetchX509Svid(WorkloadApiClient client, Exchange exchange) throws Exception {
        X509Svid svid = client.fetchX509Context().getDefaultSvid();
        Message message = getMessageForResponse(exchange);
        message.setBody(svid);
        message.setHeader(SpiffeConstants.SPIFFE_ID, svid.getSpiffeId().toString());
    }

    private void fetchJwtSvid(WorkloadApiClient client, Exchange exchange) throws Exception {
        String[] audiences = resolveAudiences(exchange);
        JwtSvid svid = audiences.length > 1
                ? client.fetchJwtSvid(audiences[0], Arrays.copyOfRange(audiences, 1, audiences.length))
                : client.fetchJwtSvid(audiences[0]);
        Message message = getMessageForResponse(exchange);
        message.setBody(svid.getToken());
        message.setHeader(SpiffeConstants.SPIFFE_ID, svid.getSpiffeId().toString());
        message.setHeader(SpiffeConstants.EXPIRY, svid.getExpiry());
    }

    private void validateJwtSvid(WorkloadApiClient client, Exchange exchange) throws Exception {
        String token = exchange.getIn().getHeader(SpiffeConstants.TOKEN, String.class);
        if (ObjectHelper.isEmpty(token)) {
            token = exchange.getIn().getBody(String.class);
        }
        if (ObjectHelper.isEmpty(token)) {
            throw new IllegalArgumentException(
                    "A JWT-SVID token is required for validateJwtSvid (set the CamelSpiffeToken header or the body)");
        }
        // the audience is the check here, not a parameter: it is what binds the token to THIS workload, so it
        // comes from the configuration only. Honouring CamelSpiffeAudience would let a caller validate a token
        // minted for someone else against an audience of their choosing.
        JwtSvid svid = validateAgainstAnyAudience(client, token, resolveConfiguredAudiences());
        Message message = getMessageForResponse(exchange);
        message.setBody(svid);
        message.setHeader(SpiffeConstants.SPIFFE_ID, svid.getSpiffeId().toString());
    }

    /**
     * Validates the token against the configured audiences, accepting it if <em>any</em> of them matches.
     * <p/>
     * The Workload API validates against one audience at a time, so a configured list has to be tried in turn. Taking
     * only the first would silently enforce a narrower rule than the configuration asks for, which is the wrong failure
     * mode for a check that decides whether a caller is authenticated.
     */
    private JwtSvid validateAgainstAnyAudience(WorkloadApiClient client, String token, String[] audiences)
            throws JwtSvidException {
        JwtSvidException failure = null;
        for (String audience : audiences) {
            try {
                return client.validateJwtSvid(token, audience);
            } catch (JwtSvidException e) {
                // could be this audience, or the token itself; only once every audience has failed do we know
                failure = e;
            }
        }
        // resolveConfiguredAudiences never returns an empty array, so the loop ran and failure is set; be explicit
        // rather than leaving a reader (or a static analyser) to prove it
        if (failure == null) {
            throw new IllegalStateException("No audience was configured to validate against");
        }
        throw failure;
    }

    private SpiffeOperation determineOperation(Exchange exchange) {
        SpiffeOperation configured = getEndpoint().getConfiguration().getOperation();
        if (!getEndpoint().getConfiguration().isAllowOperationHeader()) {
            return configured;
        }
        SpiffeOperation operation
                = exchange.getIn().getHeader(SpiffeConstants.OPERATION, SpiffeOperation.class);
        return operation != null ? operation : configured;
    }

    /**
     * Audience for a fetch: a genuine per-message parameter, so the header may override the configuration.
     */
    private String[] resolveAudiences(Exchange exchange) {
        String audience = exchange.getIn().getHeader(SpiffeConstants.AUDIENCE, String.class);
        if (ObjectHelper.isEmpty(audience)) {
            audience = getEndpoint().getConfiguration().getAudience();
        }
        return splitAudiences(audience, "set the audience option or the CamelSpiffeAudience header");
    }

    /**
     * Audience for a validation: taken from the configuration only, never from the message.
     */
    private String[] resolveConfiguredAudiences() {
        // deliberately does not mention the header: it is ignored for validation
        return splitAudiences(getEndpoint().getConfiguration().getAudience(), "set the audience option");
    }

    private String[] splitAudiences(String audience, String how) {
        if (ObjectHelper.isEmpty(audience)) {
            throw new IllegalArgumentException("At least one audience is required (" + how + ")");
        }
        String[] parts = Arrays.stream(audience.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (parts.length == 0) {
            throw new IllegalArgumentException("At least one non-blank audience is required (" + how + ")");
        }
        return parts;
    }

    private static Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }
}
