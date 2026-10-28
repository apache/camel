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

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionalQueue;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.AdditionalAnswers.answersWithDelay;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A message whose exchange was marked rollback only (without an exception) must roll back the Hazelcast transaction, so
 * the message is not removed from the queue.
 */
public class HazelcastSedaTransactedRollbackOnlyTest extends HazelcastCamelTestSupport {

    @Mock
    private IQueue<Object> queue;

    @Mock
    private TransactionalQueue<Object> tqueue;

    private final TransactionContext okTransaction = Mockito.mock(TransactionContext.class);
    private final TransactionContext kaboomTransaction = Mockito.mock(TransactionContext.class);
    private final TransactionContext idleTransaction = Mockito.mock(TransactionContext.class);

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        // one transaction per polled message, and then one for each (empty) poll after
        when(hazelcastInstance.newTransactionContext())
                .thenReturn(okTransaction, kaboomTransaction, idleTransaction);
        when(hazelcastInstance.getQueue("foo")).thenReturn(queue);
        for (TransactionContext transaction : new TransactionContext[] { okTransaction, kaboomTransaction, idleTransaction }) {
            when(transaction.getQueue("foo")).thenReturn(tqueue);
        }
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, atLeastOnce()).getQueue("foo");
        verify(hazelcastInstance, atLeastOnce()).newTransactionContext();
    }

    @Test
    public void testRollbackOnlyRollsBackTransaction() throws Exception {
        when(tqueue.poll(anyLong(), any(TimeUnit.class)))
                .thenReturn("ok")
                .thenReturn("kaboom")
                .thenAnswer(answersWithDelay(50, invocation -> null));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("ok");

        // start the consumer after the queue is trained
        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);

        verify(kaboomTransaction, timeout(5000)).rollbackTransaction();
        verify(kaboomTransaction, never()).commitTransaction();

        verify(okTransaction).commitTransaction();
        verify(okTransaction, never()).rollbackTransaction();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-seda:foo?transacted=true").routeId("foo").autoStartup(false)
                        .choice().when(body().isEqualTo("kaboom")).markRollbackOnly().end()
                        .to("mock:result");
            }
        };
    }

    @AfterEach
    public final void stopContext() throws Exception {
        context.stop();
    }
}
