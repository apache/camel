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
package org.apache.camel.spring.spi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route with PROPAGATION_REQUIRES_NEW calls a route that uses the same PROPAGATION_REQUIRES_NEW policy: when the
 * inner transaction is done, the rest of the calling route still runs in the calling route's transaction (an
 * asynchronous delay is done on the thread of the transaction only for a transacted exchange).
 */
class TransactionErrorHandlerNestedRequiresNewTest {

    private final TestTransactionManager transactionManager = new TestTransactionManager();
    private volatile Object outerTx;
    private volatile Object innerTx;
    private volatile Object txAfterDelay;
    private volatile boolean transactedAfterInner;
    private volatile boolean transactionActiveAfterDelay;
    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        SpringTransactionPolicy requiresNew = new SpringTransactionPolicy(transactionManager);
        requiresNew.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");

        context = new DefaultCamelContext();
        context.getRegistry().bind("PROPAGATION_REQUIRES_NEW", requiresNew);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:outer").transacted("PROPAGATION_REQUIRES_NEW")
                        .process(e -> outerTx = transactionManager.current.get())
                        .to("direct:inner")
                        .process(e -> transactedAfterInner = e.isTransacted())
                        // asynchronous by default: it stays on the thread of the transaction for a transacted exchange
                        .delay(1)
                        .process(e -> {
                            txAfterDelay = transactionManager.current.get();
                            transactionActiveAfterDelay = TransactionSynchronizationManager.isActualTransactionActive();
                        })
                        .choice().when(body().isEqualTo("fail"))
                        .throwException(new IllegalStateException("Outer failed"))
                        .end();

                from("direct:inner").transacted("PROPAGATION_REQUIRES_NEW")
                        .process(e -> innerTx = transactionManager.current.get());
            }
        });
        context.start();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void outerRouteGoesOnInItsTransactionAfterTheInnerOne() {
        Exchange result = context.createProducerTemplate().send("direct:outer", e -> e.getIn().setBody("ok"));

        assertFalse(result.isFailed());
        assertNotNull(outerTx);
        assertNotNull(innerTx);
        assertNotSame(outerTx, innerTx, "the inner route runs in a new transaction");
        assertTrue(transactedAfterInner, "the exchange is still transacted after the inner transaction");
        assertTrue(transactionActiveAfterDelay, "the step after the delay runs in a transaction");
        assertSame(outerTx, txAfterDelay, "the step after the delay runs in the outer transaction");
        assertTrue(transactionManager.committed.contains(innerTx));
        assertTrue(transactionManager.committed.contains(outerTx));
    }

    @Test
    void outerRouteRollsBackWithTheStepsAfterTheInnerOne() {
        Exchange result = context.createProducerTemplate().send("direct:outer", e -> e.getIn().setBody("fail"));

        assertTrue(result.isFailed());
        assertTrue(transactionManager.committed.contains(innerTx), "the inner transaction commits");
        assertSame(outerTx, txAfterDelay, "the step after the delay runs in the outer transaction");
        assertTrue(transactionManager.rolledBack.contains(outerTx), "the outer transaction rolls back");
    }

    /**
     * A transaction manager without a resource: it records the transactions and supports suspending one for
     * PROPAGATION_REQUIRES_NEW.
     */
    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        private final List<Object> committed = new CopyOnWriteArrayList<>();
        private final List<Object> rolledBack = new CopyOnWriteArrayList<>();
        private final ThreadLocal<Object> current = new ThreadLocal<>();

        @Override
        protected Object doGetTransaction() {
            return new Object[] { current.get() };
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((Object[]) transaction)[0] != null;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            Object tx = new Object();
            ((Object[]) transaction)[0] = tx;
            current.set(tx);
        }

        @Override
        protected Object doSuspend(Object transaction) {
            Object suspended = current.get();
            ((Object[]) transaction)[0] = null;
            current.remove();
            return suspended;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            current.set(suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed.add(((Object[]) status.getTransaction())[0]);
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack.add(((Object[]) status.getTransaction())[0]);
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            current.remove();
        }
    }
}
