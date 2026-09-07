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
package org.apache.camel.component.opa.security;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.opa.OpaPolicyEvaluationException;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Enforces the decision of an {@link OpaSecurityPolicy} before the wrapped processor runs.
 * <p/>
 * Evaluating the policy is a blocking call to the OPA server and happens on the calling thread, but the wrapped part of
 * the route is delegated to unchanged, so it keeps using the asynchronous routing engine.
 */
public class OpaSecurityProcessor extends DelegateAsyncProcessor {

    private final OpaSecurityPolicy policy;

    public OpaSecurityProcessor(Processor processor, OpaSecurityPolicy policy) {
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
        boolean allowed;
        try {
            allowed = policy.getEvaluator().evaluate(exchange);
        } catch (OpaPolicyEvaluationException e) {
            // the policy decision point could not be reached, so there is no decision to enforce; deny
            markFailure(exchange);
            throw new CamelAuthorizationException(
                    "Policy " + policy.getPolicyPath() + " could not be evaluated", exchange, e);
        }

        if (!allowed) {
            markFailure(exchange);
            throw new CamelAuthorizationException("Denied by policy " + policy.getPolicyPath(), exchange);
        }
    }

    private void markFailure(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, policy.getClass().getSimpleName());
    }
}
