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
package org.apache.camel.component.opa;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;

public class OpaProducer extends DefaultAsyncProducer {
    private OpaAsyncExecutor asyncExecutor;

    private OpaProducerHealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public OpaProducer(final OpaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpaEndpoint getEndpoint() {
        return (OpaEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        OpaConfiguration configuration = getEndpoint().getConfiguration();
        if (configuration.isAsyncProcessing()) {
            asyncExecutor = new OpaAsyncExecutor(
                    getEndpoint().getCamelContext(), this,
                    configuration.getAsyncPoolSize(), configuration.getAsyncQueueSize(), configuration.getAsyncTimeout());
        }
        // an injected client can point anywhere, and the endpoint has no way to ask it where; wasm mode evaluates
        // in-process with no server at all - in neither case is there a server we know the address of to probe
        if (configuration.getOpaClient() != null || ObjectHelper.isEmpty(configuration.getServerUrl())
                || OpaEndpoint.WASM_MODE.equalsIgnoreCase(configuration.getEvaluationMode())) {
            return;
        }

        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            producerHealthCheck = new OpaProducerHealthCheck(
                    configuration.getServerUrl(), configuration.getBearerToken(),
                    // the endpoint URI is unique within the context, so two endpoints sharing a policy path but
                    // pointing at different servers get distinct health-check ids instead of colliding
                    getEndpoint().getPolicyPath(), getEndpoint().getEndpointUri(), getEndpoint().getSslContext());
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (asyncExecutor != null) {
            asyncExecutor.close();
            asyncExecutor = null;
        }
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck.close();
            producerHealthCheck = null;
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!getEndpoint().getConfiguration().isAsyncProcessing()) {
            processBlocking(exchange);
            return;
        }
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
        if (exchange.getException() != null) {
            throw exchange.getException();
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Exchange> processAsync(Exchange exchange) {
        return OpaAsyncExecutor.processFuture(exchange, this, asyncExecutor);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!getEndpoint().getConfiguration().isAsyncProcessing()) {
            try {
                processBlocking(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }
        exchange.getMessage().removeHeader(OpaConstants.DECISION_ALLOW);
        exchange.getMessage().removeHeader(OpaConstants.DECISION);
        exchange.getMessage().removeHeader(OpaConstants.POLICY_PATH);
        exchange.getMessage().removeHeader(OpaConstants.DECISION_FAILED_OPEN);
        exchange.getMessage().removeHeader(OpaConstants.BATCH_DECISION);
        OpaAsyncExecutor executor = asyncExecutor;
        if (executor == null) {
            exchange.setException(new java.util.concurrent.CancellationException("Authorization producer is not started"));
            callback.done(true);
            return true;
        }
        return executor.process(exchange, callback, this::prepareAsync);
    }

    private OpaAsyncExecutor.Action prepareAsync(Exchange snapshot) throws Exception {
        OpaPolicyEvaluator evaluator = getEndpoint().getEvaluator();
        boolean batch = getEndpoint().getConfiguration().isBatch();
        Object body = snapshot.getMessage().getBody();
        if (batch && !(body instanceof List<?>)) {
            throw new IllegalArgumentException("batch=true requires a List body");
        }
        return () -> {
            if (batch) {
                evaluator.evaluateBatch(snapshot, (List<?>) body);
            } else {
                evaluator.evaluate(snapshot);
            }
            return CompletableFuture.completedFuture(null);
        };
    }

    private void processBlocking(Exchange exchange) throws Exception {
        // the verdict is reported through the decision headers, so that the route can act on it with a
        // filter or a choice; the body is left untouched
        OpaPolicyEvaluator evaluator = getEndpoint().getEvaluator();
        if (getEndpoint().getConfiguration().isBatch()) {
            Object body = exchange.getMessage().getBody();
            if (!(body instanceof List<?> elements)) {
                throw new IllegalArgumentException(
                        "batch=true requires a List body, but the body was "
                                                   + (body == null ? "null" : body.getClass().getName()));
            }
            evaluator.evaluateBatch(exchange, elements);
        } else {
            evaluator.evaluate(exchange);
        }
    }
}
