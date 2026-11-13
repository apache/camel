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
package org.apache.camel.component.hazelcast;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionalQueue;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A message whose exchange failed rolls back the Hazelcast transaction, so it is still on the queue: the transacted
 * consumer must wait onErrorDelay before it polls again, and not take the same message again at once (CAMEL-25589).
 */
public class HazelcastSedaTransactedRollbackDelayTest extends HazelcastCamelTestSupport {

    private static final int ON_ERROR_DELAY = 3000;

    @Mock
    private IQueue<Object> queue;

    @Mock
    private TransactionalQueue<Object> tqueue;

    private final TransactionContext transaction = Mockito.mock(TransactionContext.class);

    private final AtomicInteger deliveries = new AtomicInteger();

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.newTransactionContext()).thenReturn(transaction);
        when(hazelcastInstance.getQueue("foo")).thenReturn(queue);
        when(transaction.getQueue("foo")).thenReturn(tqueue);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, atLeastOnce()).getQueue("foo");
        verify(hazelcastInstance, atLeastOnce()).newTransactionContext();
    }

    @Test
    public void testRolledBackMessageIsTakenAgainAfterOnErrorDelay() throws Exception {
        // the rolled back message is still on the queue, so every poll takes it again
        when(tqueue.poll(anyLong(), any(TimeUnit.class))).thenReturn("kaboom");

        context.getRouteController().startRoute("foo");

        await().atMost(5, TimeUnit.SECONDS).until(() -> deliveries.get() >= 1);

        // without the delay, the message is taken and rolled back again in a tight loop
        await().during(ON_ERROR_DELAY - 1000, TimeUnit.MILLISECONDS)
                .atMost(ON_ERROR_DELAY + 1000, TimeUnit.MILLISECONDS)
                .until(() -> deliveries.get() == 1);

        // and after the delay it is taken again
        await().atMost(ON_ERROR_DELAY + 2000, TimeUnit.MILLISECONDS).until(() -> deliveries.get() >= 2);

        verify(transaction, atLeastOnce()).rollbackTransaction();
        verify(transaction, never()).commitTransaction();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-seda:foo?transacted=true&onErrorDelay=" + ON_ERROR_DELAY).routeId("foo")
                        .autoStartup(false)
                        .process(e -> deliveries.incrementAndGet())
                        .throwException(new IllegalStateException("Forced"));
            }
        };
    }

    @AfterEach
    public final void stopContext() throws Exception {
        context.stop();
    }
}
