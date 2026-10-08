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

import io.spiffe.spiffeid.SpiffeId;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Enforces a {@link SpiffeSecurityPolicy} before the wrapped processor runs.
 * <p>
 * It extends {@link DelegateAsyncProcessor} (as the {@link org.apache.camel.spi.Policy} SPI advises) so the guarded
 * part of the route keeps running on the asynchronous routing engine; only the authorization check itself runs
 * synchronously on the calling thread, which is a cheap in-memory comparison with no I/O.
 */
public class SpiffeSecurityProcessor extends DelegateAsyncProcessor {

    private final SpiffeSecurityPolicy policy;

    public SpiffeSecurityProcessor(Processor processor, SpiffeSecurityPolicy policy) {
        super(processor);
        this.policy = policy;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            authorize(exchange);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return super.process(exchange, callback);
    }

    protected void authorize(Exchange exchange) throws CamelAuthorizationException {
        SpiffeId peer = SpiffePeerIdentity.fromExchange(exchange, policy.getSslSessionHeader());
        if (peer == null) {
            markFailure(exchange);
            throw new CamelAuthorizationException(
                    "No verified SPIFFE peer identity on the exchange (is mutual TLS configured and required?)",
                    exchange);
        }
        if (!policy.isAuthorized(peer)) {
            markFailure(exchange);
            throw new CamelAuthorizationException("Peer SPIFFE ID " + peer + " is not authorized", exchange);
        }
        // hand the verified identity downstream (e.g. to the includeProperties of an OPA policy) as an exchange
        // property, never a header: it must stay a value the route established, not one a sender can put on the message
        exchange.setProperty(SpiffeConstants.PEER_SPIFFE_ID, peer.toString());
    }

    private void markFailure(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, policy.getClass().getSimpleName());
    }
}
