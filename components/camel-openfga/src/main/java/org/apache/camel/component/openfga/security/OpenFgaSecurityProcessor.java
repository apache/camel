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
import org.apache.camel.component.openfga.OpenFgaAsyncExecutor;
import org.apache.camel.component.openfga.OpenFgaAuthorizer;
import org.apache.camel.component.openfga.OpenFgaConstants;
import org.apache.camel.component.openfga.OpenFgaEvaluationException;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Enforces the decision of an {@link OpenFgaSecurityPolicy} before the wrapped processor runs.
 * <p/>
 * With asyncProcessing enabled, request submission and SDK future completion release the calling routing thread. The
 * guarded route continues through Camel's asynchronous routing engine in either mode.
 */
public class OpenFgaSecurityProcessor extends DelegateAsyncProcessor {

    private final OpenFgaSecurityPolicy policy;
    private OpenFgaAsyncExecutor asyncExecutor;

    public OpenFgaSecurityProcessor(Processor processor, OpenFgaSecurityPolicy policy) {
        super(processor);
        this.policy = policy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // drives the policy's readiness-check registration; a policy shared by several routes counts them
        if (policy.isAsyncProcessing()) {
            asyncExecutor = new OpenFgaAsyncExecutor(
                    policy.getCamelContext(), this,
                    policy.getAsyncPoolSize(), policy.getAsyncQueueSize(), policy.getAsyncTimeout());
        }
        policy.onProcessorStart();
    }

    @Override
    protected void doStop() throws Exception {
        // remove the readiness check once the last guarded route stops, so a stopped or reloaded route does not leave
        // a check behind reporting on a policy that is no longer enforcing anything (CAMEL-24751)
        if (asyncExecutor != null) {
            asyncExecutor.close();
            asyncExecutor = null;
        }
        policy.onProcessorStop();
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenFgaAsyncExecutor executor = asyncExecutor;
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
        return OpenFgaAsyncExecutor.processFuture(exchange, this, asyncExecutor);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (policy.isAsyncProcessing()) {
            exchange.getMessage().removeHeader(OpenFgaConstants.ALLOWED);
            exchange.getMessage().removeHeader(OpenFgaConstants.FAILED_OPEN);
            exchange.getMessage().removeHeader(OpenFgaConstants.DENY_REASON);
            exchange.getMessage().removeHeader(OpenFgaConstants.USER);
            exchange.getMessage().removeHeader(OpenFgaConstants.OBJECT);
            exchange.getMessage().removeHeader(OpenFgaConstants.RELATION);
            exchange.getMessage().removeHeader(OpenFgaConstants.STORE_ID);
            OpenFgaAsyncExecutor executor = asyncExecutor;
            if (executor == null) {
                exchange.setException(new CamelAuthorizationException("Authorization policy is not started", exchange));
                markFailure(exchange);
                callback.done(true);
                return true;
            }
            OpenFgaAuthorizer authorizer = policy.getAuthorizer();
            String relation = policy.getRelation();
            return executor.process(exchange, doneSync -> {
                if (exchange.getException() != null) {
                    if (!(exchange.getException() instanceof CamelAuthorizationException)) {
                        exchange.setException(new CamelAuthorizationException(
                                "Could not ask OpenFGA whether the exchange may proceed",
                                exchange, exchange.getException()));
                    }
                    markFailure(exchange);
                    callback.done(doneSync);
                } else if (!Boolean.TRUE.equals(exchange.getMessage().getHeader(OpenFgaConstants.ALLOWED))) {
                    markFailure(exchange);
                    exchange.setException(new CamelAuthorizationException(
                            "Denied by OpenFGA relation " + relation + " ("
                                                                          + exchange.getMessage().getHeader(
                                                                                  OpenFgaConstants.DENY_REASON)
                                                                          + ")",
                            exchange));
                    callback.done(doneSync);
                } else {
                    super.process(exchange, downstreamSync -> callback.done(doneSync && downstreamSync));
                }
            }, snapshot -> {
                OpenFgaAsyncExecutor.Action action = authorizer.prepareCheckAsync(snapshot, executor);
                return () -> action.run().handle((allowed, error) -> {
                    if (error != null) {
                        Throwable failure
                                = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                                        ? error.getCause() : error;
                        throw new java.util.concurrent.CompletionException(
                                new CamelAuthorizationException(
                                        "Could not ask OpenFGA whether the exchange may proceed", snapshot, failure));
                    }
                    // A policy denial is a completed decision. Let the winning gate commit its headers before
                    // the routing callback turns it into an authorization exception.
                    return null;
                });
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
