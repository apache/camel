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
import org.apache.camel.component.opa.OpaAsyncExecutor;
import org.apache.camel.component.opa.OpaPolicyEvaluationException;
import org.apache.camel.component.opa.OpaPolicyEvaluator;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Enforces the decision of an {@link OpaSecurityPolicy} before the wrapped processor runs.
 * <p/>
 * Evaluating the policy is a blocking call to the OPA server and happens on the calling thread, but the wrapped part of
 * the route is delegated to unchanged, so it keeps using the asynchronous routing engine.
 */
public class OpaSecurityProcessor extends DelegateAsyncProcessor {

    private final OpaSecurityPolicy policy;
    private OpaAsyncExecutor asyncExecutor;

    public OpaSecurityProcessor(Processor processor, OpaSecurityPolicy policy) {
        super(processor);
        this.policy = policy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // drives the policy's readiness-check registration; a policy shared by several routes counts them
        if (policy.isAsyncProcessing()) {
            asyncExecutor = new OpaAsyncExecutor(
                    policy.getCamelContext(), this,
                    policy.getAsyncPoolSize(), policy.getAsyncQueueSize(), policy.getAsyncTimeout());
        }
        policy.onProcessorStart();
    }

    @Override
    protected void doStop() throws Exception {
        // remove the readiness check once the last guarded route stops, so a stopped or reloaded route does not
        // leave a check behind reporting on a policy that is no longer enforcing anything (CAMEL-24751)
        if (asyncExecutor != null) {
            asyncExecutor.close();
            asyncExecutor = null;
        }
        policy.onProcessorStop();
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpaAsyncExecutor executor = asyncExecutor;
        try {
            super.process(exchange);
        } catch (InterruptedException e) {
            if (executor != null) {
                executor.cancel(exchange);
            }
            Thread.currentThread().interrupt();
            throw e;
        }
        if (exchange.getException() instanceof InterruptedException && executor != null) {
            executor.cancel(exchange, exchange.getException());
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Exchange> processAsync(Exchange exchange) {
        return OpaAsyncExecutor.processFuture(exchange, this, asyncExecutor);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (policy.isAsyncProcessing()) {
            exchange.getMessage().removeHeader(org.apache.camel.component.opa.OpaConstants.DECISION_ALLOW);
            exchange.getMessage().removeHeader(org.apache.camel.component.opa.OpaConstants.DECISION_FAILED_OPEN);
            exchange.getMessage().removeHeader(org.apache.camel.component.opa.OpaConstants.DECISION);
            exchange.getMessage().removeHeader(org.apache.camel.component.opa.OpaConstants.POLICY_PATH);
            exchange.getMessage().removeHeader(org.apache.camel.component.opa.OpaConstants.BATCH_DECISION);
            OpaAsyncExecutor executor = asyncExecutor;
            if (executor == null) {
                exchange.setException(new java.util.concurrent.CancellationException("Authorization policy is not started"));
                markFailure(exchange);
                callback.done(true);
                return true;
            }
            OpaPolicyEvaluator evaluator = policy.getEvaluator();
            String path = policy.getPolicyPath();
            return executor.process(exchange, doneSync -> {
                if (exchange.getException() != null) {
                    if (!(exchange.getException() instanceof CamelAuthorizationException)) {
                        exchange.setException(new CamelAuthorizationException(
                                "Policy " + path + " could not be evaluated",
                                exchange, exchange.getException()));
                    }
                    markFailure(exchange);
                    callback.done(doneSync);
                } else if (!Boolean.TRUE.equals(exchange.getMessage().getHeader(
                        org.apache.camel.component.opa.OpaConstants.DECISION_ALLOW))) {
                    markFailure(exchange);
                    exchange.setException(new CamelAuthorizationException("Denied by policy " + path, exchange));
                    callback.done(doneSync);
                } else {
                    super.process(exchange, downstreamSync -> callback.done(doneSync && downstreamSync));
                }
            }, snapshot -> () -> {
                evaluator.evaluate(snapshot);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            });
        }
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
        authorize(exchange, policy.getEvaluator(), policy.getPolicyPath());
    }

    private void authorize(Exchange exchange, OpaPolicyEvaluator evaluator, String path) throws CamelAuthorizationException {
        boolean allowed;
        try {
            allowed = evaluator.evaluate(exchange);
        } catch (OpaPolicyEvaluationException e) {
            // the policy decision point could not be reached, so there is no decision to enforce; deny
            markFailure(exchange);
            throw new CamelAuthorizationException(
                    "Policy " + path + " could not be evaluated", exchange, e);
        }

        if (!allowed) {
            markFailure(exchange);
            throw new CamelAuthorizationException("Denied by policy " + path, exchange);
        }
    }

    private void markFailure(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, policy.getClass().getSimpleName());
    }
}
