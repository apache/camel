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
package org.apache.camel.component.azure.eventhubs;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.models.EventContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventHubsPartitionCheckpointsTest {

    @Test
    void failedCheckpointRunsItsErrorCallbackUnderTheLock() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        EventHubsPartitionCheckpoints checkpoints = new EventHubsPartitionCheckpoints(lock);
        Sinks.Empty<Void> answer = Sinks.empty();
        EventContext event = event(1, answer);
        CompletableFuture<Boolean> lockHeldInOnError = new CompletableFuture<>();

        lock.lock();
        try {
            assertTrue(checkpoints.delivered(event));
            assertTrue(checkpoints.completed(event, true));
            assertTrue(checkpoints.checkpoint(() -> lockHeldInOnError.complete(lock.isHeldByCurrentThread())));
        } finally {
            lock.unlock();
        }

        // the checkpoint store answers on its own thread, as the Event Hubs client does
        Thread answering = new Thread(() -> answer.tryEmitError(new IllegalStateException("store not reachable")));
        answering.start();
        answering.join(TimeUnit.SECONDS.toMillis(5));

        assertTrue(lockHeldInOnError.get(5, TimeUnit.SECONDS),
                "the error callback restores the count of the partition, which the consumer changes under the lock");
        assertFalse(lock.isLocked(), "the lock is released after the answer");
    }

    private static EventContext event(long sequenceNumber, Sinks.Empty<Void> answer) {
        EventData data = mock(EventData.class);
        when(data.getSequenceNumber()).thenReturn(sequenceNumber);
        EventContext context = mock(EventContext.class);
        when(context.getEventData()).thenReturn(data);
        when(context.updateCheckpointAsync()).thenReturn(answer.asMono());
        return context;
    }
}
