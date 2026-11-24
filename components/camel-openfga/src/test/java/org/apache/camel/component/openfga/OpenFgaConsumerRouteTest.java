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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import dev.openfga.sdk.api.client.ApiResponse;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientReadChangesResponse;
import dev.openfga.sdk.api.model.ReadChangesResponse;
import dev.openfga.sdk.api.model.TupleChange;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleOperation;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaConsumerRouteTest extends CamelTestSupport {
    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);
    @BindToRegistry("checkpoints")
    private final MemoryStateRepository checkpoints = new MemoryStateRepository();
    private final AtomicInteger projected = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private boolean rejectSecond;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    void scheduledConsumerUsesTheCoreRepositoryAndAcknowledgesTheWholeSplitPage() throws Exception {
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page()));
        context.start();
        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(checkpoints.getState("projection")).isEqualTo("after"));
        context.getRouteController().stopRoute("changes");
        assertThat(projected).hasValue(2);
        assertThat(errors).hasValue(0);
        OpenFgaEndpoint endpoint = (OpenFgaEndpoint) context.getRoute("changes").getEndpoint();
        assertThat(endpoint.getConfiguration().getChangePollTimeout()).isEqualTo(1000);
        assertThat(endpoint.getConfiguration().getCheckpointRepository()).isSameAs(checkpoints);
    }

    @Test
    void failedSplitDoesNotCheckpointOrSendTheFailureThroughTheBridgedHandlerTwice() throws Exception {
        rejectSecond = true;
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page()));
        context.start();
        await().atMost(5, SECONDS).until(() -> errors.get() == 1);
        context.getRouteController().stopRoute("changes");
        assertThat(checkpoints.getState("projection")).isNull();
        assertThat(projected).hasValue(2);
        assertThat(errors).hasValue(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IOException.class).maximumRedeliveries(0).handled(false)
                        .process(exchange -> errors.incrementAndGet());
                from("openfga:readChanges?openFgaClient=#fgaClient&storeId=store"
                     + "&checkpointRepository=#checkpoints&checkpointKey=projection"
                     + "&bridgeErrorHandler=true&initialDelay=0&delay=60000&changePollTimeout=1s")
                        .routeId("changes")
                        .split(body()).shareUnitOfWork().stopOnException()
                        .process(exchange -> {
                            if (projected.incrementAndGet() == 2 && rejectSecond) {
                                throw new IOException("projection failed");
                            }
                        })
                        .end();
            }
        };
    }

    private static ClientReadChangesResponse page() {
        List<TupleChange> changes = List.of(
                new TupleChange().tupleKey(new TupleKey().user("user:anne").relation("reader")._object("document:a"))
                        .operation(TupleOperation.WRITE),
                new TupleChange().tupleKey(new TupleKey().user("user:anne").relation("reader")._object("document:b"))
                        .operation(TupleOperation.WRITE));
        return new ClientReadChangesResponse(
                new ApiResponse<>(
                        200, Map.of(), "",
                        new ReadChangesResponse().changes(changes).continuationToken("after")));
    }
}
