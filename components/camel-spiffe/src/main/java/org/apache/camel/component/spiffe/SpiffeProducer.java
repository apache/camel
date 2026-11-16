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

import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.WorkloadApiClient;
import io.spiffe.workloadapi.X509Context;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class SpiffeProducer extends DefaultProducer {
    private final SpiffeId localSpiffeId;

    public SpiffeProducer(final SpiffeEndpoint endpoint) {
        super(endpoint);
        localSpiffeId = SpiffeX509SvidSelector.parse(endpoint.getConfiguration().getLocalSpiffeId());
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
        Message message = getMessageForResponse(exchange);
        // a fetch that fails, with or without a configured identity, must not leave the identity of an earlier
        // message behind
        message.removeHeader(SpiffeConstants.SPIFFE_ID);
        message.removeHeader(SpiffeConstants.EXPIRY);
        X509Context context = client.fetchX509Context();
        X509Svid svid = localSpiffeId == null
                ? context.getDefaultSvid()
                : SpiffeX509SvidSelector.require(
                        SpiffeX509SvidSelector.select(context.getX509Svids(), localSpiffeId), localSpiffeId);
        // the identity is always available through the headers, so a route that only needs it can avoid the key
        message.setHeader(SpiffeConstants.SPIFFE_ID, svid.getSpiffeId().toString());
        message.setHeader(SpiffeConstants.EXPIRY, svid.getLeaf().getNotAfter());
        switch (getEndpoint().getConfiguration().getX509Response()) {
            // the full SVID carries the private key; the chain does not; id leaves the body untouched
            case svid -> message.setBody(svid);
            case chain -> message.setBody(svid.getChain());
            case id -> {
                // leave the body untouched: the identity is exposed through the headers only
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported x509Response: " + getEndpoint().getConfiguration().getX509Response());
        }
    }

    private void fetchJwtSvid(WorkloadApiClient client, Exchange exchange) throws Exception {
        String[] audiences = resolveAudiences(exchange);
        Message message = getMessageForResponse(exchange);
        message.removeHeader(SpiffeConstants.SPIFFE_ID);
        message.removeHeader(SpiffeConstants.EXPIRY);
        JwtSvid svid;
        if (localSpiffeId == null) {
            svid = audiences.length > 1
                    ? client.fetchJwtSvid(audiences[0], Arrays.copyOfRange(audiences, 1, audiences.length))
                    : client.fetchJwtSvid(audiences[0]);
        } else {
            // the configured identity is the subject of the token; the Workload API refuses a subject the workload
            // is not entitled to, so the option fails closed here as it does for X.509
            svid = audiences.length > 1
                    ? client.fetchJwtSvid(localSpiffeId, audiences[0], Arrays.copyOfRange(audiences, 1, audiences.length))
                    : client.fetchJwtSvid(localSpiffeId, audiences[0]);
        }
        message.setBody(svid.getToken());
        message.setHeader(SpiffeConstants.SPIFFE_ID, svid.getSpiffeId().toString());
        message.setHeader(SpiffeConstants.EXPIRY, svid.getExpiry());
    }

    private void validateJwtSvid(WorkloadApiClient client, Exchange exchange) throws Exception {
        boolean preserveBody = getEndpoint().getConfiguration().isPreserveBody();
        JwtSvid svid = SpiffeJwtValidator.validate(
                client, exchange, getEndpoint().getConfiguration().getAudience(), !preserveBody);
        SpiffeJwtValidator.recordIdentity(exchange, svid, true);
        if (!preserveBody) {
            getMessageForResponse(exchange).setBody(svid);
        }
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

    private static String[] splitAudiences(String audience, String how) {
        return SpiffeJwtValidator.audiences(audience, how);
    }

    private static Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }
}
