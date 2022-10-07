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

import java.util.concurrent.atomic.AtomicInteger;

import com.azure.messaging.eventhubs.models.EventContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventHubsCheckpointUpdaterTimerTaskTest {

    @Test
    void testProcessedEventsResetWhenCheckpointUpdated() {
        var processedEvents = new AtomicInteger(1);
        var eventContext = Mockito.mock(EventContext.class);

        Mockito.when(eventContext.updateCheckpointAsync())
                .thenReturn(Mono.just("").then());

        var timerTask = new EventHubsCheckpointUpdaterTimerTask(eventContext, processedEvents);

        timerTask.run();

        assertEquals(0, processedEvents.get());
    }

    @Test
    void testProcessedEventsNotResetWhenCheckpointUpdateFails() {
        var processedEvents = new AtomicInteger(1);
        var eventContext = Mockito.mock(EventContext.class);

        Mockito.when(eventContext.updateCheckpointAsync())
                .thenReturn(Mono.error(new RuntimeException()));

        var timerTask = new EventHubsCheckpointUpdaterTimerTask(eventContext, processedEvents);

        timerTask.run();

        assertEquals(1, processedEvents.get());
    }

}
