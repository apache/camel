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
        String[] audiences = resolveAudiences(exchange);
        JwtSvid svid = client.validateJwtSvid(token, audiences[0]);
        Message message = getMessageForResponse(exchange);
        message.setBody(svid);
        message.setHeader(SpiffeConstants.SPIFFE_ID, svid.getSpiffeId().toString());
    }

    private SpiffeOperation determineOperation(Exchange exchange) {
        SpiffeOperation operation
                = exchange.getIn().getHeader(SpiffeConstants.OPERATION, SpiffeOperation.class);
        return operation != null ? operation : getEndpoint().getConfiguration().getOperation();
    }

    private String[] resolveAudiences(Exchange exchange) {
        String audience = exchange.getIn().getHeader(SpiffeConstants.AUDIENCE, String.class);
        if (ObjectHelper.isEmpty(audience)) {
            audience = getEndpoint().getConfiguration().getAudience();
        }
        if (ObjectHelper.isEmpty(audience)) {
            throw new IllegalArgumentException(
                    "At least one audience is required (set the audience option or the CamelSpiffeAudience header)");
        }
        String[] parts = Arrays.stream(audience.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (parts.length == 0) {
            throw new IllegalArgumentException(
                    "At least one non-blank audience is required (set the audience option or the CamelSpiffeAudience header)");
        }
        return parts;
    }

    private static Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }
}
