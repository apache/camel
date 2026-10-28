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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that a saga step joins an LRA propagated in the Long-Running-Action header only when the LRA is hosted by the
 * configured coordinator.
 */
class LRAPropagatedSagaTest extends CamelTestSupport {

    private static final String COORDINATOR_URL = "http://lra-coordinator:8080";

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private final LRAClient client = Mockito.mock(LRAClient.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        LRASagaService sagaService = new MockClientLRASagaService(client);
        sagaService.setCoordinatorUrl(COORDINATOR_URL);
        sagaService.setLocalParticipantUrl("http://localhost:" + port.getPort());
        context.addService(sagaService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().port(port.getPort());
            }
        });
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:step")
                        .saga().propagation(SagaPropagation.SUPPORTS)
                        .compensation("mock:compensate")
                        .to("mock:step");
            }
        };
    }

    @Test
    void joinsAnLraOfTheConfiguredCoordinator() throws Exception {
        Mockito.when(client.join(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        MockEndpoint step = getMockEndpoint("mock:step");
        step.expectedMessageCount(1);

        Exchange exchange = send(COORDINATOR_URL + "/lra-coordinator/0_1");

        assertNull(exchange.getException());
        step.assertIsSatisfied();
        ArgumentCaptor<URL> joined = ArgumentCaptor.forClass(URL.class);
        Mockito.verify(client).join(joined.capture(), Mockito.any(), Mockito.any());
        assertEquals(COORDINATOR_URL + "/lra-coordinator/0_1", joined.getValue().toString());
    }

    @Test
    void doesNotJoinAnLraOfAnotherCoordinator() throws Exception {
        MockEndpoint step = getMockEndpoint("mock:step");
        step.expectedMessageCount(0);

        Exchange exchange = send("http://elsewhere:8080/lra-coordinator/0_1");

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        step.assertIsSatisfied();
        Mockito.verifyNoInteractions(client);
    }

    private Exchange send(String lra) {
        return template.send("direct:step", e -> e.getIn().setHeader(Exchange.SAGA_LONG_RUNNING_ACTION, lra));
    }

    // a named class, as the thread pool of the service is named after its class
    private static class MockClientLRASagaService extends LRASagaService {

        private final LRAClient client;

        MockClientLRASagaService(LRAClient client) {
            this.client = client;
        }

        @Override
        protected LRAClient createLRAClient() {
            return client;
        }
    }
}
