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
package org.apache.camel.jta;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An exchange marked with markRollbackOnlyLast() (no exception) must roll back the transaction of the
 * TransactionErrorHandler, as the camel-spring TransactionErrorHandler does (CAMEL-17105), and the marker must then be
 * cleared so that an outer transaction is not affected.
 */
class TransactionErrorHandlerRollbackOnlyLastTest {

    private CamelContext camelContext;
    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    private JtaTransactionPolicy createPolicy() {
        // a policy that records whether the transaction would be committed or rolled back
        return new JtaTransactionPolicy() {
            @Override
            public void run(Runnable runnable) throws Throwable {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    rollbacks.incrementAndGet();
                    throw t;
                }
                commits.incrementAndGet();
            }
        };
    }

    private TransactionErrorHandler createErrorHandler(Processor processor) {
        TransactionErrorHandler answer
                = new TransactionErrorHandler(camelContext, processor, createPolicy(), LoggingLevel.WARN);
        answer.start();
        return answer;
    }

    private Exchange createExchange() {
        Exchange exchange = camelContext.getEndpoint("direct:test").createExchange();
        exchange.getExchangeExtension().setUnitOfWork(new DefaultUnitOfWork(exchange));
        return exchange;
    }

    @Test
    void markRollbackOnlyLastRollsBackTheTransaction() throws Exception {
        TransactionErrorHandler handler = createErrorHandler(exchange -> exchange.setRollbackOnlyLast(true));
        try {
            Exchange exchange = createExchange();
            handler.process(exchange);

            assertEquals(1, rollbacks.get(), "The transaction should be rolled back");
            assertEquals(0, commits.get(), "The transaction should not be committed");
            // rollback only last affects only this transaction: an outer transaction does not see the marker
            assertFalse(exchange.isRollbackOnlyLast());
            assertNull(exchange.getException());
        } finally {
            handler.stop();
        }
    }

    @Test
    void markRollbackOnlyLastInTransactedRouteRollsBackTheTransaction() throws Exception {
        camelContext.getRegistry().bind("recordingPolicy", createPolicy());
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transacted("recordingPolicy")
                        .setBody(constant("done"))
                        .markRollbackOnlyLast();
            }
        });

        Exchange result = camelContext.createProducerTemplate().send("direct:start", e -> e.getIn().setBody("Hello"));

        assertEquals(1, rollbacks.get(), "The transaction should be rolled back");
        assertEquals(0, commits.get(), "The transaction should not be committed");
        assertNull(result.getException());
    }
}
