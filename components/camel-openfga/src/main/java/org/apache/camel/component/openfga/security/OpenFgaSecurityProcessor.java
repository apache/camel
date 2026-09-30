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
package org.apache.camel.component.openfga.security;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.openfga.OpenFgaConstants;
import org.apache.camel.component.openfga.OpenFgaEvaluationException;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Enforces the decision of an {@link OpenFgaSecurityPolicy} before the wrapped processor runs.
 * <p/>
 * Asking OpenFGA is a blocking call and happens on the calling thread, but the wrapped part of the route is delegated
 * to unchanged, so it keeps using the asynchronous routing engine.
 */
public class OpenFgaSecurityProcessor extends DelegateAsyncProcessor {

    private final OpenFgaSecurityPolicy policy;

    public OpenFgaSecurityProcessor(Processor processor, OpenFgaSecurityPolicy policy) {
        super(processor);
        this.policy = policy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // drives the policy's readiness-check registration; a policy shared by several routes counts them
        policy.onProcessorStart();
    }

    @Override
    protected void doStop() throws Exception {
        // remove the readiness check once the last guarded route stops, so a stopped or reloaded route does not leave
        // a check behind reporting on a policy that is no longer enforcing anything (CAMEL-24751)
        policy.onProcessorStop();
        super.doStop();
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
        boolean allowed;
        try {
            allowed = policy.getAuthorizer().check(exchange);
        } catch (OpenFgaEvaluationException e) {
            // OpenFGA could not be reached, so there is no decision to enforce; deny
            markFailure(exchange);
            throw new CamelAuthorizationException(
                    "Could not ask OpenFGA whether the exchange may proceed", exchange, e);
        }

        if (!allowed) {
            markFailure(exchange);
            // the reason distinguishes a relationship that does not exist from an exchange that never carried a usable
            // subject, which read from the message alone look identical
            throw new CamelAuthorizationException(
                    "Denied by OpenFGA relation " + policy.getRelation() + " ("
                                                  + exchange.getMessage().getHeader(OpenFgaConstants.DENY_REASON) + ")",
                    exchange);
        }
    }

    private void markFailure(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, policy.getClass().getSimpleName());
    }
}
