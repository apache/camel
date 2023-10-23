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
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.Exchange;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class LRASagaCoordinatorTest extends CamelTestSupport {

    private URL url;

    private LRASagaService sagaService;

    private LRAClient client;

    private Exchange exchange;

    private LRASagaCoordinator coordinator;

    @BeforeEach
    public void setup() throws Exception {
        url = new URL("https://localhost/saga");
        sagaService = Mockito.mock(LRASagaService.class);
        client = Mockito.mock(LRAClient.class);
        Mockito.when(sagaService.getClient()).thenReturn(client);
        exchange = Mockito.mock(Exchange.class);

        coordinator = new LRASagaCoordinator(sagaService, url);
    }

    public LRASagaCoordinatorTest() {
        setUseRouteBuilder(false);
    }

    @DisplayName("Tests whether no sagaService is causing exception")
    @Test
    void testSagaServiceIsNotNull() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new LRASagaCoordinator(null, url));
    }

    @DisplayName("Tests whether no sagaService is causing exception")
    @Test
    void testUrlIsNotNull() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new LRASagaCoordinator(sagaService, null));
    }

    @DisplayName("Tests whether join is called on LRAClient")
    @Test
    void testBeginStep() throws Exception {
        CamelSagaStep step = new CamelSagaStep(Optional.empty(), Optional.empty(), Collections.emptyMap(), Optional.empty());

        CompletableFuture<Void> expected = CompletableFuture.completedFuture(null);
        Mockito.when(client.join(Mockito.eq(url), Mockito.any(LRASagaStep.class), Mockito.eq(exchange))).thenReturn(expected);

        CompletableFuture<Void> actual = coordinator.beginStep(exchange, step);
        Assertions.assertSame(expected, actual);

        Mockito.verify(sagaService).getClient();
        Mockito.verify(client).join(Mockito.eq(url), Mockito.any(LRASagaStep.class), Mockito.eq(exchange));
    }

    @DisplayName("Tests whether complete is called on LRAClient")
    @Test
    void testComplete() throws Exception {
        CompletableFuture<Void> expected = CompletableFuture.completedFuture(null);
        Mockito.when(client.complete(url, exchange)).thenReturn(expected);

        CompletableFuture<Void> actual = coordinator.complete(exchange);

        Assertions.assertSame(expected, actual);
        Mockito.verify(sagaService).getClient();
        Mockito.verify(client).complete(url, exchange);
    }

    @DisplayName("Tests whether compensate is called on LRAClient")
    @Test
    void testCompensate() throws Exception {
        CompletableFuture<Void> expected = CompletableFuture.completedFuture(null);
        Mockito.when(client.compensate(url, exchange)).thenReturn(expected);

        CompletableFuture<Void> actual = coordinator.compensate(exchange);

        Assertions.assertSame(expected, actual);
        Mockito.verify(sagaService).getClient();
        Mockito.verify(client).compensate(url, exchange);
    }

}
