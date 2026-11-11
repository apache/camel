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
package org.apache.camel.component.openfga;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import dev.openfga.sdk.api.client.model.ClientReadChangesRequest;
import dev.openfga.sdk.api.client.model.ClientReadChangesResponse;
import dev.openfga.sdk.api.configuration.ClientReadChangesOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;

/** One change-log page per poll, acknowledged durably only after the complete page succeeds. */
public class OpenFgaConsumer extends ScheduledPollConsumer {
    private final OpenFgaEndpoint endpoint;
    private final AtomicReference<CompletableFuture<ClientReadChangesResponse>> pending = new AtomicReference<>();

    public OpenFgaConsumer(OpenFgaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {
        if (!isRunAllowed()) {
            return 0;
        }
        OpenFgaConfiguration configuration = endpoint.getConfiguration();
        OpenFgaCheckpointRepository repository = configuration.getCheckpointRepository();
        // Reload on every poll: a failed checkpoint must replay the page, never use an uncommitted in-memory cursor.
        String previous = repository.load(configuration.getCheckpointKey());
        ClientReadChangesRequest request = new ClientReadChangesRequest();
        if (ObjectHelper.isNotEmpty(configuration.getType())) {
            request.type(configuration.getType());
        }
        if (ObjectHelper.isEmpty(previous) && endpoint.getStartTime() != null) {
            request.startTime(endpoint.getStartTime());
        }
        ClientReadChangesOptions options = new ClientReadChangesOptions();
        if (configuration.getPageSize() != null) {
            options.pageSize(configuration.getPageSize());
        }
        if (ObjectHelper.isNotEmpty(previous)) {
            options.continuationToken(previous);
        }
        Exchange exchange = createExchange(false);
        try {
            ClientReadChangesResponse response = endpoint.getAuthorizer().await(exchange, "poll the change log", () -> {
                CompletableFuture<ClientReadChangesResponse> future
                        = endpoint.getAuthorizer().getClient().readChanges(request, options);
                pending.set(future);
                if (!isRunAllowed()) {
                    future.cancel(true);
                }
                return future;
            }, configuration.getChangePollTimeout());
            if (!isRunAllowed()) {
                return 0;
            }
            List<Map<String, Object>> page = OpenFgaProducer.changePage(response);
            String next = response.getContinuationToken();
            if (ObjectHelper.isNotEmpty(next)) {
                exchange.getMessage().setHeader(OpenFgaConstants.CONTINUATION_TOKEN, next);
            }
            exchange.getMessage().setBody(page);
            if (!page.isEmpty()) {
                createUoW(exchange);
                try {
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                    throw e;
                } finally {
                    doneUoW(exchange);
                }
                if (exchange.isFailed() || exchange.isRollbackOnly()) {
                    Exception failure = exchange.getException();
                    throw failure != null ? failure : new IllegalStateException("OpenFGA change page processing rolled back");
                }
            }
            // An empty tail can return the same cursor. Never erase a bookmark when the server omits its token.
            if (isRunAllowed() && ObjectHelper.isNotEmpty(next) && !next.equals(previous)) {
                repository.store(configuration.getCheckpointKey(), next);
            }
            return page.size();
        } finally {
            pending.set(null);
            releaseExchange(exchange, false);
        }
    }

    @Override
    protected void doStop() throws Exception {
        CompletableFuture<ClientReadChangesResponse> future = pending.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
        super.doStop();
    }
}
