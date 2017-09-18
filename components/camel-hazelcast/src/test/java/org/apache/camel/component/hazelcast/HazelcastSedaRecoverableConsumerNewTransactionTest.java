/**
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

import com.hazelcast.core.HazelcastException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.TransactionContext;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

public class HazelcastSedaRecoverableConsumerNewTransactionTest extends HazelcastSedaRecoverableConsumerTest {

    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        TransactionContext transactionContext = Mockito.mock(TransactionContext.class);
        when(hazelcastInstance.newTransactionContext())
                .thenThrow(new HazelcastException("Could not obtain Connection!!!"))
                .thenReturn(transactionContext);
        when(hazelcastInstance.getQueue("foo")).thenReturn(queue);
        when(transactionContext.getQueue("foo")).thenReturn(tqueue);
    }

    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, times(2)).getQueue("foo");
        verify(hazelcastInstance, atLeastOnce()).newTransactionContext();
    }

}
