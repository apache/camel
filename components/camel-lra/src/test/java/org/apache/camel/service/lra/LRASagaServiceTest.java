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

import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.camel.Exchange;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class LRASagaServiceTest extends CamelTestSupport {

    public LRASagaServiceTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @DisplayName("Tests whether doStart() is creating a LRAClient")
    @Test
    void testCanCreateLRAClient() throws Exception {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        sagaService.setCamelContext(this.context());
        sagaService.doStart();

        LRAClient client = sagaService.getClient();
        Assertions.assertNotNull(client, "lraClient must not be null");
    }

    @DisplayName("Tests whether doStart() is creating an alternative LRAClient")
    @Test
    void testCanCreateAlternativeLRAClient() throws Exception {
        LRASagaService sagaService = new AlternativeLRASagaService();
        applyMockProperties(sagaService);
        sagaService.setCamelContext(this.context());
        sagaService.doStart();

        LRAClient client = sagaService.getClient();
        Assertions.assertNotNull(client, "lraClient must not be null");

        Assertions.assertInstanceOf(AlternativeLRAClient.class, client, "client must be an instance of AlternativeLRAClient");
    }

    @DisplayName("Tests whether newSaga(Exchange) is passing on Exchange to the client")
    @Test
    void testCallsClientWithExchange() throws Exception {
        LRAClient client = Mockito.mock(LRAClient.class);
        LRASagaService sagaService = new ClientMockingLRASagaService(client);
        applyMockProperties(sagaService);
        sagaService.setCamelContext(this.context());
        sagaService.doStart();

        Exchange exchange = Mockito.mock(Exchange.class);

        CompletableFuture<URL> expected = CompletableFuture.failedFuture(new RuntimeException("failed"));
        Mockito.when(client.newLRA(exchange)).thenReturn(expected);
        sagaService.newSaga(exchange);
        Mockito.verify(client).newLRA(exchange);
    }

    @DisplayName("Tests whether newSaga(Exchange) accepts an LRA hosted by the configured coordinator")
    @Test
    void testNewSagaAcceptsAllowedCoordinatorUrl() throws Exception {
        LRAClient client = Mockito.mock(LRAClient.class);
        LRASagaService sagaService = startedService(client, null);

        Exchange exchange = Mockito.mock(Exchange.class);
        URL validUrl = URI.create("http://lra-coordinator:8080/lra-coordinator/0_1").toURL();
        Mockito.when(client.newLRA(exchange)).thenReturn(CompletableFuture.completedFuture(validUrl));

        CamelSagaCoordinator coordinator = sagaService.newSaga(exchange).join();
        Assertions.assertNotNull(coordinator);
        Assertions.assertEquals(validUrl.toString(), coordinator.getId());
    }

    @DisplayName("Tests whether newSaga(Exchange) rejects an LRA hosted by an unapproved host")
    @Test
    void testNewSagaRejectsUnapprovedHost() throws Exception {
        LRAClient client = Mockito.mock(LRAClient.class);
        LRASagaService sagaService = startedService(client, null);

        Exchange exchange = Mockito.mock(Exchange.class);
        URL unapprovedUrl = URI.create("http://elsewhere:8080/lra-coordinator/0_1").toURL();
        Mockito.when(client.newLRA(exchange)).thenReturn(CompletableFuture.completedFuture(unapprovedUrl));

        CompletionException ex = Assertions.assertThrows(CompletionException.class, () -> sagaService.newSaga(exchange).join());
        Assertions.assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        Assertions.assertTrue(ex.getCause().getMessage().contains("is not hosted by the LRA coordinator configured"));
    }

    @DisplayName("Tests whether newSaga(Exchange) rejects an LRA path outside the coordinator context path")
    @Test
    void testNewSagaRejectsPathOutsideCoordinatorContextPath() throws Exception {
        LRAClient client = Mockito.mock(LRAClient.class);
        LRASagaService sagaService = startedService(client, null);

        Exchange exchange = Mockito.mock(Exchange.class);
        URL unapprovedPathUrl = URI.create("http://lra-coordinator:8080/other/0_1").toURL();
        Mockito.when(client.newLRA(exchange)).thenReturn(CompletableFuture.completedFuture(unapprovedPathUrl));

        CompletionException ex = Assertions.assertThrows(CompletionException.class, () -> sagaService.newSaga(exchange).join());
        Assertions.assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @DisplayName("Tests whether newSaga(Exchange) accepts an LRA matching allowedCoordinatorUrls")
    @Test
    void testNewSagaAcceptsAlternativeAllowedCoordinatorUrl() throws Exception {
        LRAClient client = Mockito.mock(LRAClient.class);
        LRASagaService sagaService = startedService(client, "http://proxy-coordinator:8080/lra-coordinator");

        Exchange exchange = Mockito.mock(Exchange.class);
        URL proxyUrl = URI.create("http://proxy-coordinator:8080/lra-coordinator/0_1").toURL();
        Mockito.when(client.newLRA(exchange)).thenReturn(CompletableFuture.completedFuture(proxyUrl));

        CamelSagaCoordinator coordinator = sagaService.newSaga(exchange).join();
        Assertions.assertNotNull(coordinator);
        Assertions.assertEquals(proxyUrl.toString(), coordinator.getId());
    }

    private LRASagaService startedService(LRAClient client, String allowedCoordinatorUrls) throws Exception {
        LRASagaService sagaService = new ClientMockingLRASagaService(client);
        sagaService.setCoordinatorUrl("http://lra-coordinator:8080");
        sagaService.setCoordinatorContextPath("/lra-coordinator");
        sagaService.setLocalParticipantUrl("http://participant:8080");
        if (allowedCoordinatorUrls != null) {
            sagaService.setAllowedCoordinatorUrls(allowedCoordinatorUrls);
        }
        sagaService.setCamelContext(this.context());
        sagaService.doStart();
        return sagaService;
    }

    @DisplayName("Tests whether doStart() fails when the callback secret is set but blank")
    @Test
    void testBlankCallbackSecretFailsToStart() {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        sagaService.setCallbackSecret(" ");

        Assertions.assertThrows(IllegalArgumentException.class, sagaService::doStart);
    }

    private void applyMockProperties(LRASagaService sagaService) {
        sagaService.setCoordinatorUrl("mockCoordinatorUrl");
        sagaService.setLocalParticipantUrl("mockLocalParticipantUrl");
        sagaService.setLocalParticipantContextPath("mockLocalParticipantContextPath");
        sagaService.setCoordinatorContextPath("mockCoordinatorContextPath");
    }

    private class AlternativeLRASagaService extends LRASagaService {
        protected LRAClient createLRAClient() {
            return new AlternativeLRAClient(this);
        }
    }

    private class AlternativeLRAClient extends LRAClient {
        public AlternativeLRAClient(LRASagaService sagaService) {
            super(sagaService);
        }
    }

    private class ClientMockingLRASagaService extends LRASagaService {

        private final LRAClient client;

        public ClientMockingLRASagaService(LRAClient client) {
            this.client = client;
        }

        protected LRAClient createLRAClient() {
            return client;
        }
    }

}
