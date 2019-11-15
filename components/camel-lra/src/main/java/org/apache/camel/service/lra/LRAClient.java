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
package org.apache.camel.service.lra;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;

import static org.apache.camel.service.lra.LRAConstants.COORDINATOR_PATH_CANCEL;
import static org.apache.camel.service.lra.LRAConstants.COORDINATOR_PATH_CLOSE;
import static org.apache.camel.service.lra.LRAConstants.COORDINATOR_PATH_START;
import static org.apache.camel.service.lra.LRAConstants.HEADER_LINK;
import static org.apache.camel.service.lra.LRAConstants.HEADER_TIME_LIMIT;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPENSATE;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPLETE;

public class LRAClient {


    private final LRASagaService sagaService;

    private final Client client;

    private final WebTarget target;

    public LRAClient(LRASagaService sagaService) {
        this.sagaService = sagaService;

        this.client = ClientBuilder.newBuilder()
                // CAMEL-12204: disabled for compatibility with JAX-RS 2.0
                //.executorService(sagaService.getExecutorService())
                .build();

        this.target = client.target(
                new LRAUrlBuilder()
                        .host(sagaService.getCoordinatorUrl())
                        .path(sagaService.getCoordinatorContextPath())
                        .build()
        );
    }

    public CompletableFuture<URL> newLRA() {
        CompletableFuture<Response> future = new CompletableFuture<>();
        target.path(COORDINATOR_PATH_START)
                .request()
                .async()
                .post(Entity.text(""), callbackToCompletableFuture(future));

        return future.thenApply(res -> {
            URL lraURL = toURL(res.getHeaders().getFirst(Exchange.SAGA_LONG_RUNNING_ACTION));
            if (lraURL == null) {
                throw new IllegalStateException("Cannot obtain LRA id from LRA coordinator");
            }

            return lraURL;
        });
    }

    public CompletableFuture<Void> join(URL lra, LRASagaStep step) {
        return CompletableFuture.supplyAsync(() -> {
            LRAUrlBuilder participantBaseUrl = new LRAUrlBuilder()
                    .host(sagaService.getLocalParticipantUrl())
                    .path(sagaService.getLocalParticipantContextPath())
                    .options(step.getOptions())
                    .compensation(step.getCompensation())
                    .completion(step.getCompletion());


            String compensationURL = participantBaseUrl.path(PARTICIPANT_PATH_COMPENSATE).build();
            String completionURL = participantBaseUrl.path(PARTICIPANT_PATH_COMPLETE).build();

            StringBuilder link = new StringBuilder();
            link.append('<').append(compensationURL).append('>').append("; rel=compensate");
            link.append(',');
            link.append('<').append(completionURL).append('>').append("; rel=complete");


            WebTarget joinTarget = client.target(lra.toString());
            if (step.getTimeoutInMilliseconds().isPresent()) {
                joinTarget = joinTarget.queryParam(HEADER_TIME_LIMIT, step.getTimeoutInMilliseconds().get());
            }

            CompletableFuture<Response> future = new CompletableFuture<>();
            joinTarget.request()
                    .header(HEADER_LINK, link.toString())
                    .header(Exchange.SAGA_LONG_RUNNING_ACTION, lra)
                    .async()
                    .put(Entity.entity(link.toString(), MediaType.TEXT_PLAIN), callbackToCompletableFuture(future));

            return future;
        }, sagaService.getExecutorService())
                .thenCompose(Function.identity())
                .thenApply(response -> {
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        throw new RuntimeCamelException("Cannot join LRA");
                    }

                    return null;
                });
    }

    public CompletableFuture<Void> complete(URL lra) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        client.target(lra.toString())
                .path(COORDINATOR_PATH_CLOSE)
                .request()
                .async()
                .put(Entity.entity("", MediaType.TEXT_PLAIN), callbackToCompletableFuture(future));

        return future.thenApply(response -> {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new RuntimeCamelException("Cannot complete LRA");
            }

            return null;
        });
    }

    public CompletableFuture<Void> compensate(URL lra) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        client.target(lra.toString())
                .path(COORDINATOR_PATH_CANCEL)
                .request()
                .async()
                .put(Entity.entity("", MediaType.TEXT_PLAIN), callbackToCompletableFuture(future));

        return future.thenApply(response -> {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new RuntimeCamelException("Cannot compensate LRA");
            }

            return null;
        });
    }

    private InvocationCallback<Response> callbackToCompletableFuture(CompletableFuture<Response> future) {
        return new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {
                future.complete(response);
            }

            @Override
            public void failed(Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
    }

    private URL toURL(Object url) {
        if (url == null) {
            return null;
        }
        if (url instanceof URL) {
            return URL.class.cast(url);
        }

        try {
            return new URL(url.toString());
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }

}
