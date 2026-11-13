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

import io.spiffe.exception.JwtSvidException;
import io.spiffe.svid.jwtsvid.JwtSvid;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps JWT validation and its Workload API client within the guarded route's lifecycle. */
final class SpiffeJwtSecurityProcessor extends DelegateAsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(SpiffeJwtSecurityProcessor.class);

    private final SpiffeJwtSecurityPolicy policy;
    private final SpiffeEndpoint endpoint;

    SpiffeJwtSecurityProcessor(Processor processor, SpiffeJwtSecurityPolicy policy, SpiffeEndpoint endpoint) {
        super(processor);
        this.policy = policy;
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        endpoint.start();
        try {
            super.doStart();
        } catch (Exception e) {
            endpoint.stop();
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        try {
            super.doStop();
        } finally {
            endpoint.stop();
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            JwtSvid svid = SpiffeJwtValidator.validate(
                    endpoint.getWorkloadApiClient(), exchange, endpoint.getConfiguration().getAudience(), false);
            if (!policy.isAuthorized(svid.getSpiffeId())) {
                throw new IllegalArgumentException("Validated SPIFFE identity is not authorized by this policy");
            }
            SpiffeJwtValidator.recordIdentity(exchange, svid, false);
        } catch (Exception e) {
            // Only the class of the failure is logged: the message of the SDK exception may contain the credential.
            // A failure that is not a verdict on the token, such as the Workload API being unreachable, is worth a
            // warning, since to the caller every failure is the same 401 and an outage would look like a 401 storm.
            if (e instanceof JwtSvidException || e instanceof IllegalArgumentException) {
                LOG.debug("JWT-SVID authentication or authorization failed: {}", e.getClass().getName());
            } else {
                LOG.warn("JWT-SVID validation could not be performed ({}): is the Workload API reachable?",
                        e.getClass().getName());
            }
            SpiffeJwtValidator.clearIdentity(exchange);
            exchange.getMessage().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, policy.getClass().getSimpleName());
            // Do not include the SDK exception message: it may contain the presented credential.
            exchange.setException(new CamelAuthorizationException("JWT-SVID authentication or authorization failed", exchange));
            callback.done(true);
            return true;
        }
        return super.process(exchange, callback);
    }
}
