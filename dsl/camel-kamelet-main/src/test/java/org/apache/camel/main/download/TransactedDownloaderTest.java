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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.spi.TransactedPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-26850: camel run has no transaction manager unless one is declared, so a route with transacted runs its steps
 * without a transaction. The policy bound for it before returned null, and the route dropped every message.
 */
public class TransactedDownloaderTest {

    @Test
    public void aTransactedRouteWithoutATransactionManagerRunsItsSteps() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            assertTrue(TransactedDownloader.bindPassThroughPolicies(context, new TransactedDefinition()));
            context.addRoutes(orders());
            context.start();

            MockEndpoint mock = context.getEndpoint("mock:stored", MockEndpoint.class);
            mock.expectedBodiesReceived("ORD-1001");
            context.createProducerTemplate().sendBody("direct:orders", "ORD-1001");
            mock.assertIsSatisfied();
        }
    }

    @Test
    public void aPolicyOfTheUsersOwnIsKept() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            CountingPolicy own = new CountingPolicy();
            context.getRegistry().bind("PROPAGATION_REQUIRED", own);
            assertFalse(TransactedDownloader.bindPassThroughPolicies(context, new TransactedDefinition()));
            assertSame(own, context.getRegistry().lookupByName("PROPAGATION_REQUIRED"));
            assertNull(context.getRegistry().lookupByName("PROPAGATION_REQUIRES_NEW"));
            context.addRoutes(orders());
            context.start();

            MockEndpoint mock = context.getEndpoint("mock:stored", MockEndpoint.class);
            mock.expectedBodiesReceived("ORD-1001");
            context.createProducerTemplate().sendBody("direct:orders", "ORD-1001");
            mock.assertIsSatisfied();
            assertEquals(1, own.wrapped);
        }
    }

    @Test
    public void aTransactedWithAReferenceIsLeftAsItIs() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            TransactedDefinition transacted = new TransactedDefinition();
            transacted.setRef("myPolicy");
            assertFalse(TransactedDownloader.bindPassThroughPolicies(context, transacted));
            assertNull(context.getRegistry().lookupByName("PROPAGATION_REQUIRED"));
        }
    }

    private static RouteBuilder orders() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .transacted()
                        .to("mock:stored");
            }
        };
    }

    private static final class CountingPolicy implements TransactedPolicy {

        private int wrapped;

        @Override
        public void beforeWrap(Route route, NamedNode definition) {
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            wrapped++;
            return processor;
        }
    }
}
