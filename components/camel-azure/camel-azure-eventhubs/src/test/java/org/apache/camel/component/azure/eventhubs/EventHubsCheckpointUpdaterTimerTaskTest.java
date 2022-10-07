package org.apache.camel.component.azure.eventhubs;

import com.azure.messaging.eventhubs.models.EventContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

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
